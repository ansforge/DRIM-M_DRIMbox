/*
 *  HL7Receiver.java - DRIMBox
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

package com.bcom.drimbox.dmp.xades.hl7;


import java.util.Base64;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.bcom.drimbox.api.DmpAPI;
import com.bcom.drimbox.dmp.database.DatabaseManager;
import com.bcom.drimbox.dmp.xades.file.CDAFile;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetServer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class HL7Receiver {

	private final Vertx vertx;
	private String message;

	@ConfigProperty(name = "hl7.port")
	private int port;

	@ConfigProperty(name = "hl7.host")
	private String host;

	@Inject
	DmpAPI dmpAPI;

	@Inject
	DatabaseManager db;

	@Inject
	public HL7Receiver(Vertx vertx){
		this.vertx = vertx;
	}

	/**
	 * Start a tcp listener to retrieve hl7 messages
	 */
	public void start() {
		NetServer server = vertx.createNetServer();
		server.connectHandler(socket -> {
			socket.handler(buffer -> {
				message += buffer.toString();
				socket.endHandler(done -> {
					// We need to run this on the main thread since it will register a @Transaction
					Infrastructure.getDefaultExecutor().execute( () -> {
						parseCDA(message);
						message = "";
					});
				});
			});
		});
		server.listen(port, host);
		Log.info("Start TCP Listener on /" + host + ": " + port + " for ORU messages");
	}

	/**
	 * Parse hl7 to retrieve cda and decode the b64 format
	 */
	public void parseCDA(String message) {
		// Check if hl7 messages
		if (message.contains("MSH")) {
			Log.info("HL7 message received");

			// TODO : handle wrong message (e.g. : if OBX doesn't exists)
			String cda = message.substring(message.indexOf("OBX|1|"));
			for (int i = 0; i < 5; i++) {
				cda = cda.substring(cda.indexOf("|") + 1);
			}
			cda = cda.substring(17, cda.indexOf("|"));
			// Decode b64 cda to string
			byte[] rawCDAData = Base64.getDecoder().decode(cda);
			CDAFile cdaString = new CDAFile(new String(rawCDAData));

			dmpAPI.storeCDA(cdaString);
		}
		else {
			Log.info("Message with no MSH");
			Log.info(message);
		}

	}

}
