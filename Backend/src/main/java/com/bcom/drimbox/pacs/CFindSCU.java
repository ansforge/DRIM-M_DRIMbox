/*
 *  CFindSCU.java - DRIMBox
 *
 * N°IDDN : IDDN.FR.001.020012.000.S.C.2023.000.30000
 *
 * MIT License
 *
 * Copyright (c) 2022 b<>com
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.bcom.drimbox.pacs;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.DimseRSPHandler;
import org.dcm4che3.net.Priority;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.logging.Log;
import jakarta.inject.Singleton;


@Singleton
public class CFindSCU {
	
	@ConfigProperty(name = "dmp.retrieveURL")
	String retrieveURL;

	//private String retrieveUrl = "http://demodrim.labs.b-com.com/drimbox/studies/";
	private String retrieveUrl = "http://localhost:4200/api/source/studies/";

	// Parameters to access pacs from application.properties
	String callingAET = ConfigProvider.getConfig().getValue("dcm.cmove.callingAET", String.class);
	String calledAET = ConfigProvider.getConfig().getValue("dcm.cmove.calledAET", String.class);
	int port = ConfigProvider.getConfig().getValue("dcm.cmove.port", int.class);
	String pacsUrl = ConfigProvider.getConfig().getValue("pacs.baseUrl", String.class);;

	private ApplicationEntity ae;
	private final Device device = new Device("findscu");
	private Association as;
	private Attributes keys = new Attributes();
	private int cancelAfter;
	private Attributes results = new Attributes();
	private String queryLevel;

	/**
	 * CFind to pacs to retrieve informations for the kos
	 * @param studyInstanceUID value from cda we want in pacs
	 * @param queryRetrieveLevel IMAGE level
	 * @return
	 */
	@SuppressWarnings("finally")
	public Attributes CFind(String studyInstanceUID, String queryRetrieveLevel) {
		try {
			Conf();
			ExecutorService executorService =
					Executors.newSingleThreadExecutor();
			ScheduledExecutorService scheduledExecutorService =
					Executors.newSingleThreadScheduledExecutor();
			this.device.setExecutor(executorService);
			this.device.setScheduledExecutor(scheduledExecutorService);
			this.keys.clear();

			this.queryLevel = queryRetrieveLevel;
			this.keys.setString(Tag.StudyInstanceUID, VR.UI, studyInstanceUID);
			this.keys.setString(Tag.QueryRetrieveLevel, VR.UI, queryRetrieveLevel);

			if(this.queryLevel.equals("IMAGE")) {
				// Values we want to get
				this.keys.setString(Tag.StudyDate, VR.DA, "");
				this.keys.setString(Tag.StudyTime, VR.DA, "");
				this.keys.setString(Tag.ReferringPhysicianName, VR.PN, "");
				this.keys.setString(Tag.StudyDescription, VR.LO, "");
				this.keys.setString(Tag.SeriesDescription, VR.LO, "");
				this.keys.setString(Tag.StudyID, VR.SH, "");
				this.keys.setString(Tag.SeriesInstanceUID, VR.SH, "*");
				this.keys.setString(Tag.SOPInstanceUID, VR.SH, "*");
				this.keys.setString(Tag.SOPClassUID, VR.SH, "");
				this.keys.setString(Tag.Modality, VR.SH, "*");
				this.keys.setString(Tag.AccessionNumber, VR.SH, "");
				this.keys.setString(Tag.PatientID, VR.LO, "");
			}
			
			try {
				String pacsBaseUrl = new URL(this.pacsUrl).getHost();
				as = ae.connect(getConnection(pacsBaseUrl, this.port), makeAAssociateRQ(this.calledAET));
				this.query();
			} finally {
				this.close();
				executorService.shutdown();
				scheduledExecutorService.shutdown();
				return results;
			}
		} catch (Exception e) {
			System.err.println("findscu: " + e.getMessage());
			e.printStackTrace();
			System.exit(2);
		}
		return null;
	}

	private void Conf() {
		Connection conn = new Connection();
		ae = new ApplicationEntity(this.callingAET);
		device.addConnection(conn);
		device.addApplicationEntity(ae);
		ae.addConnection(conn);
	}

	private Connection getConnection(String hostName, int port) {
		return new Connection(null, hostName, port);
	}

	private AAssociateRQ makeAAssociateRQ(String calledAET) {
		AAssociateRQ aarq = new AAssociateRQ();
		aarq.setCallingAET(ae.getAETitle()); // optional: will be set in ae.connect() if not explicitly set.
		aarq.setCalledAET(calledAET);
		aarq.addPresentationContext(
				new PresentationContext(1,
						UID.StudyRootQueryRetrieveInformationModelFind,
						UID.ImplicitVRLittleEndian));
		return aarq;
	}

	public void query() throws IOException, InterruptedException {
		query(keys);
	}

	private void query(Attributes keys) throws IOException, InterruptedException {
		DimseRSPHandler rspHandler = new DimseRSPHandler(as.nextMessageID()) {

			int cancelAfter = CFindSCU.this.cancelAfter;
			int numMatches;

			@Override
			public void onDimseRSP(Association as, Attributes cmd,
					Attributes data) {
				super.onDimseRSP(as, cmd, data);
				int status = cmd.getInt(Tag.Status, -1);
				if (Status.isPending(status)) {
					CFindSCU.this.onResult(data);
					++numMatches;
					if (cancelAfter != 0 && numMatches >= cancelAfter)
						try {
							cancel(as);
							cancelAfter = 0;
						} catch (IOException e) {
							e.printStackTrace();
						}
				}
			}
		};

		query(keys, rspHandler);
	}

	private void query(Attributes keys, DimseRSPHandler rspHandler) throws IOException, InterruptedException {
		as.cfind(UID.StudyRootQueryRetrieveInformationModelFind, Priority.NORMAL, keys, null, rspHandler);
	}

	private void onResult(Attributes data) {
		if(this.queryLevel.equals("IMAGE")) {
			// We get the infos from each series and each images
			this.results.addAll(data);
			boolean alreadyExist = false;
			Log.info("Accession Number : " + data.getString(Tag.AccessionNumber));
			Log.info("PatientID : " + data.getString(Tag.PatientID));
			if(this.results.getSequence(Tag.ReferencedSeriesSequence) != null) {
				for (Attributes attr : this.results.getSequence(Tag.ReferencedSeriesSequence)) {

					if(attr.getString(Tag.SeriesInstanceUID).equals(data.getString(Tag.SeriesInstanceUID))) {
						alreadyExist = true;
						//attr.setString(Tag.ReferencedSOPInstanceUID, VR.UI, data.getString(Tag.SOPInstanceUID));
						Sequence ReferencedSOPSequence = attr.getSequence(Tag.ReferencedSOPSequence);
						Attributes attrsReferencedSop = new Attributes();
						attrsReferencedSop.setString(Tag.ReferencedSOPClassUID, VR.UI, data.getString(Tag.SOPClassUID));
						attrsReferencedSop.setString(Tag.ReferencedSOPInstanceUID, VR.UI, data.getString(Tag.SOPInstanceUID));
						ReferencedSOPSequence.add(attrsReferencedSop);
					}
				}

				if (!alreadyExist) {
					Sequence referencedSeriesSequence  = this.results.getSequence(Tag.ReferencedSeriesSequence);
					Attributes attrsReferenced = new Attributes();
					attrsReferenced.setString(Tag.RetrieveURL, VR.UR, this.retrieveUrl + data.getString(Tag.StudyInstanceUID) + "/series/" + data.getString(Tag.SeriesInstanceUID));
					attrsReferenced.setString(Tag.Modality, VR.UR, data.getString(Tag.Modality));
					attrsReferenced.setString(Tag.SeriesDescription, VR.UR, data.getString(Tag.SeriesDescription));
					attrsReferenced.setString(Tag.AccessionNumber, VR.SH, data.getString(Tag.AccessionNumber));
					attrsReferenced.setString(Tag.PatientID, VR.LO, data.getString(Tag.PatientID));
					attrsReferenced.setString(Tag.SeriesInstanceUID, VR.UI, data.getString(Tag.SeriesInstanceUID));
					referencedSeriesSequence.add(attrsReferenced);

					Sequence ReferencedSOPSequence = attrsReferenced.newSequence(Tag.ReferencedSOPSequence, 1);
					Attributes attrsReferencedSop = new Attributes();
					attrsReferencedSop.setString(Tag.ReferencedSOPClassUID, VR.UI, data.getString(Tag.SOPClassUID));
					attrsReferencedSop.setString(Tag.ReferencedSOPInstanceUID, VR.UI, data.getString(Tag.SOPInstanceUID));
					ReferencedSOPSequence.add(attrsReferencedSop);
				}

			}
			else  {
				Sequence referencedSeriesSequence  = this.results.newSequence(Tag.ReferencedSeriesSequence , 1);
				Attributes attrsReferenced = new Attributes();
				attrsReferenced.setString(Tag.RetrieveURL, VR.UR, this.retrieveUrl + data.getString(Tag.StudyInstanceUID) + "/series/" + data.getString(Tag.SeriesInstanceUID));
				attrsReferenced.setString(Tag.Modality, VR.UR, data.getString(Tag.Modality));
				attrsReferenced.setString(Tag.SeriesDescription, VR.UR, data.getString(Tag.SeriesDescription));
				attrsReferenced.setString(Tag.AccessionNumber, VR.SH, data.getString(Tag.AccessionNumber));
				attrsReferenced.setString(Tag.PatientID, VR.LO, data.getString(Tag.PatientID));
				attrsReferenced.setString(Tag.SeriesInstanceUID, VR.UI, data.getString(Tag.SeriesInstanceUID));
				referencedSeriesSequence.add(attrsReferenced);

				Sequence ReferencedSOPSequence = attrsReferenced.newSequence(Tag.ReferencedSOPSequence, 1);
				Attributes attrsReferencedSop = new Attributes();
				attrsReferencedSop.setString(Tag.ReferencedSOPClassUID, VR.UI, data.getString(Tag.SOPClassUID));
				attrsReferencedSop.setString(Tag.ReferencedSOPInstanceUID, VR.UI, data.getString(Tag.SOPInstanceUID));
				ReferencedSOPSequence.add(attrsReferencedSop);
			}
		}
	}

	public void close() throws IOException, InterruptedException {
		if (as != null && as.isReadyForDataTransfer()) {
			as.waitForOutstandingRSP();
			as.release();
		}
	}
}
