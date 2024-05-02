/*
 *  IocmCStoreSCP.java - DRIMBox
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.DimseRQHandler;
import org.dcm4che3.net.PDVInputStream;
import org.dcm4che3.net.TransferCapability;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicCEchoSCP;
import org.dcm4che3.net.service.BasicCStoreSCP;
import org.dcm4che3.net.service.DicomServiceRegistry;
import org.dcm4che3.util.StreamUtils;

import com.bcom.drimbox.api.DmpAPI;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class IocmCStoreSCP {

	private ApplicationEntity ae;

	private Device device;

	private String studyInstanceUID = "";

	@Inject
	DmpAPI dmpAPI;

	public void startCStore(String calledAET, String bindAddress, int port) throws Exception {

		ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
		ExecutorService executor = Executors.newFixedThreadPool(2);

		device = new Device("c-echo-scp");
		this.ae = new ApplicationEntity(calledAET);
		Connection conn = new Connection(null, "127.0.0.1", port);
		conn.setBindAddress(bindAddress);
		// enable Asynchronous Operations
		conn.setMaxOpsInvoked(0);
		conn.setMaxOpsPerformed(0);
		device.addApplicationEntity(this.ae);
		device.addConnection(conn);
		this.ae.addConnection(conn);
		// Retrieve only kos from pacs
		this.ae.addTransferCapability(new TransferCapability(null, "1.2.840.10008.5.1.4.1.1.88.59", TransferCapability.Role.SCP, "*"));

		device.setDimseRQHandler(createServiceRegistry());


		setExecutor(executor);
		setScheduledExecutor(scheduledExecutor);
		start();
	}

	public void setExecutor(Executor executor) {
		device.setExecutor(executor);
	}

	public void setScheduledExecutor(ScheduledExecutorService executor) {
		device.setScheduledExecutor(executor);
	}

	public void start() throws IOException, GeneralSecurityException {
		device.bindConnections();
	}


	private DimseRQHandler createServiceRegistry() {
		DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();
		serviceRegistry.addDicomService(new BasicCEchoSCP());
		serviceRegistry.addDicomService(new BasicCStoreSCP("*") {
			@Override
			protected void store(Association as, PresentationContext pc, Attributes rq,
					PDVInputStream data, Attributes rsp) throws IOException {
				IocmCStoreSCP.this.store(as, pc, rq, data);
			}
		});
		return serviceRegistry;
	}


	private void store(Association as, PresentationContext pc, Attributes rq, PDVInputStream data)
			throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		String cuid = rq.getString(Tag.AffectedSOPClassUID);
		String iuid = rq.getString(Tag.AffectedSOPInstanceUID);
		String tsuid = pc.getTransferSyntax();
		Attributes fmi = as.createFileMetaInformation(iuid, cuid, tsuid);

		try (DicomOutputStream dos = new DicomOutputStream(output, tsuid)) {
			dos.writeFileMetaInformation(fmi);
			StreamUtils.copy(data, dos);
		}

		InputStream input = new ByteArrayInputStream(output.toByteArray()); 
		Attributes dataset;
		// Retrieve and parse kos from IOCM message
		try (DicomInputStream dis = new DicomInputStream(input)) {
			dis.setIncludeBulkData(IncludeBulkData.URI);
			dataset = dis.readDataset();
			studyInstanceUID = dataset.getString(Tag.StudyInstanceUID);
		}
		// Get the studyInstanceUID
		Log.info("before kos generate : " + studyInstanceUID);
		Infrastructure.getDefaultExecutor().execute( () -> {
			try {
				dmpAPI.updateKOS(studyInstanceUID);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});

	}

}
































