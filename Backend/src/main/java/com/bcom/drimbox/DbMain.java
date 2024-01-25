/*
 *  DbMain.java - DRIMBox
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

package com.bcom.drimbox;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import com.bcom.drimbox.dmp.xades.hl7.HL7Receiver;
import com.bcom.drimbox.pacs.CStoreSCP;
import com.bcom.drimbox.pacs.IocmCStoreSCP;

import jakarta.annotation.PostConstruct;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import io.quarkus.runtime.annotations.CommandLineArguments;

import java.util.Optional;

@Startup
@Singleton
public class DbMain {

	@ConfigProperty(name="dcm.cstore.AET")
	String calledAET;
	@ConfigProperty(name="dcm.cstore.host")
	String host;
	@ConfigProperty(name="dcm.cstore.port")
	int port;
	
	@ConfigProperty(name="dcm.cstoreIOCM.AET")
	String calledAETIOCM;
	@ConfigProperty(name="dcm.cstoreIOCM.host")
	String hostIOCM;
	@ConfigProperty(name="dcm.cstoreIOCM.port")
	int portIOCM;

	@Inject
	@CommandLineArguments
	String[] args;

	// Cache of instance datas
	@Inject
	CStoreSCP cStoreSCP;
	
	// Cache of instance datas
	@Inject
	IocmCStoreSCP iocmcStoreSCP;

	// Cache of instance datas
	@Inject
	HL7Receiver tcpListener;
	
	public enum DrimBOXMode {
		SOURCE,
		CONSO,
		RIS
	}
	
	DrimBOXMode type;

	static final String SOURCE_ARG = "source";
	static final String CONSO_ARG = "conso";
	static final String RIS_ARG = "ris";
	@PostConstruct
	public void checkParams() throws Exception {

		DrimBOXMode mode = DrimBOXMode.CONSO;
		// Also check environment (mainly for docker)
		String envDbMode = Optional.ofNullable(System.getenv("DRIMBOX_MODE")).orElse("");
		
		if (args.length == 1 && args[0].equals(SOURCE_ARG)
			|| envDbMode.equals(SOURCE_ARG)) {
			mode = DrimBOXMode.SOURCE;
		}
		else if (args.length == 1 && args[0].equals(RIS_ARG)
			|| envDbMode.equals(RIS_ARG)) {
			mode = DrimBOXMode.RIS;
		}

		tcpListener.start();

		switch (mode) {
			case SOURCE:
				Log.info("Starting DrimBOX Source");
				type = DrimBOXMode.SOURCE;
				cStoreSCP.startCStore(calledAET, host, port);
				iocmcStoreSCP.startCStore(calledAETIOCM, hostIOCM, portIOCM);
				break;
			case CONSO:
				Log.info("Starting DrimBOX Conso");
				type = DrimBOXMode.CONSO;
				break;
			case RIS:
				Log.info("Starting DrimBOX Ris");
				type = DrimBOXMode.RIS;
				break;
		}

		Log.info("java.library.path=" + System.getProperty("java.library.path"));
	}
	
	public DrimBOXMode getTypeDrimbBOX() {
		return this.type;
	}
}