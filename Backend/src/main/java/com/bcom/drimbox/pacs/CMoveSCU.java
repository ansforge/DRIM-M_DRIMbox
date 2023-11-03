/*
 *  CMoveSCU.java - DRIMBox
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

import static com.bcom.drimbox.pacs.CStoreSCP.EB_DONE_ADDRESS;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.DimseRSPHandler;
import org.dcm4che3.net.IncompatibleConnectionException;
import org.dcm4che3.net.Priority;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.bcom.drimbox.dmp.database.DatabaseManager;
import com.bcom.drimbox.dmp.database.SourceEntity;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.vertx.core.Vertx;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class CMoveSCU {
	private Device device;
	private ApplicationEntity ae;

	@Inject
	EventBus eventBus;

	@ConfigProperty(name="dcm.cmove.callingAET")
	String callingAET;
	@ConfigProperty(name="dcm.cmove.calledAET")
	String calledAET;
	@ConfigProperty(name="dcm.cmove.port")
	int port;

	@ConfigProperty(name="pacs.baseUrl")
	String pacsUrl;


	private Attributes request;

	// Cache of instance data
	@Inject
	CStoreSCP cStoreSCP;
	
	@Inject
	DatabaseManager databaseManager;

	private final Vertx vertx;

	@Inject
	public CMoveSCU(Vertx vertx) {
		this.vertx = vertx;
	}


	public Multi<byte[]> cMove(String studyUID, String serieUID, List<String> supportedTransferSyntax, List<String> preferredTransferSyntax, String boundary)  {
		//Instant startTime = Instant.now();
		// We start the cmove in another thread so we can return the Multi as soon as possible
		vertx.executeBlocking(promise -> {
					request = new Attributes(2);
					this.request.setString(Tag.QueryRetrieveLevel, VR.CS, "SERIES");
					this.request.setString(Tag.StudyInstanceUID, VR.UI, studyUID);
					this.request.setString(Tag.SeriesInstanceUID, VR.UI, serieUID);
					
					String ins = retrieveINS(studyUID);
					Log.info("ins : " + ins);


					cStoreSCP.resetMultipart();
					cStoreSCP.setPreferredTransferSyntax(preferredTransferSyntax);
					cStoreSCP.setSupportedTransferSyntax(supportedTransferSyntax);
					cStoreSCP.setBoundary(boundary);
					cStoreSCP.setIns(ins);

					ExecutorService executor = Executors.newFixedThreadPool(4);
                    ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(4);
					try {
						device = new Device("c-move-scu");
						ae = new ApplicationEntity(this.callingAET);
						setScheduledExecutor(scheduledExecutor);
						Connection conn = new Connection();
						device.addApplicationEntity(ae);
						device.addConnection(conn);
						ae.addConnection(conn);

						setExecutor(executor);
						doCMove();
						cStoreSCP.done();
					} catch (Exception e) {
						Log.error("Error while doing cmove : " + e.getMessage());
					} finally {
						executor.shutdown();
						scheduledExecutor.shutdown();
					}
					promise.complete();
				}, res -> {
					if (res.failed()) {
						// TODO : handle this better
						Log.error("Unexpected error : " + res);
						eventBus.publish(EB_DONE_ADDRESS, true);
					} else {
						//Log.info("Pacs cmove TimeTT : " + Duration.between(startTime, Instant.now()).toString());
					}
				}
		);
		return cStoreSCP.getResponseStream();
	}

	public void setExecutor(Executor executor) {
		device.setExecutor(executor);
	}

	public void setScheduledExecutor(ScheduledExecutorService executor) {
		device.setScheduledExecutor(executor);
	}

	private void doCMove()
			throws IOException, InterruptedException, GeneralSecurityException, IncompatibleConnectionException {

		String pacsBaseUrl = new URL(pacsUrl).getHost();

		Association as = ae.connect(getConnection(pacsBaseUrl, this.port), makeAAssociateRQ(this.calledAET));
		try {
			as.cmove(UID.StudyRootQueryRetrieveInformationModelMove,
					Priority.NORMAL,
					this.request,
					UID.ImplicitVRLittleEndian,
					this.cStoreSCP.getAET(),
					new DimseRSPHandler(as.nextMessageID()));
		} catch (IOException e) {
			System.err.printf("Failed to invoke C-MOVE-RQ to %s - %s%n", this.calledAET, e);
		}
		as.waitForOutstandingRSP();
		as.release();
	}

	private AAssociateRQ makeAAssociateRQ(String calledAET) {
		AAssociateRQ aarq = new AAssociateRQ();
		aarq.setCallingAET(ae.getAETitle()); // optional: will be set in ae.connect() if not explicitly set.
		aarq.setCalledAET(calledAET);
		aarq.addPresentationContext(
				new PresentationContext(1,
						UID.StudyRootQueryRetrieveInformationModelMove,
						UID.ImplicitVRLittleEndian));
		return aarq;
	}

	private Connection getConnection(String hostName, int port) {
		return new Connection(null, hostName, port);
	}


	public void setAttributeRequests(String[] args) {
		this.request = new Attributes(args.length);
		String type;
		if (args.length == 1) {
			type = "STUDY";
		} else if (args.length == 2) {
			type = "SERIES";
		} else {
			type = "IMAGE";
		}

		this.request.setString(Tag.QueryRetrieveLevel, VR.CS, type);
		this.request.setString(Tag.StudyInstanceUID, VR.UI, args[0]);
		if (args.length > 1) {
			this.request.setString(Tag.SeriesInstanceUID, VR.UI, args[1]);
			if (args.length > 2) {
				String[] iuids = new String[args.length - 2];
				System.arraycopy(args, 2, iuids, 0, iuids.length);
				this.request.setString(Tag.SOPInstanceUID, VR.UI, iuids);
			}
		}
	}
	
	public String retrieveINS(String studyInstanceUID) {
		SourceEntity entity = this.databaseManager.getEntity(studyInstanceUID);
		if(entity == null) {
			Log.info("No data found in database");
			return "error";
		}
		InputStream targetStream = new ByteArrayInputStream(entity.rawKOS);
		try (DicomInputStream dis = new DicomInputStream(targetStream)) {
			dis.setIncludeBulkData(IncludeBulkData.URI);
			Attributes dataset = dis.readDataset();
			String ins = dataset.getString(Tag.PatientID);
			Log.info(ins);
			return ins;

		} catch (Exception e) {
			Log.error("Error in verifySop");
			Log.error(e.getMessage());
		}
		return "error";
		
	}
}
