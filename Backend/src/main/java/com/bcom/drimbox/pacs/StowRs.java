/*
 *  StowRs.java - DRIMBox
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

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

import org.dcm4che3.data.UID;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.bcom.drimbox.api.DRIMboxConsoAPI;
import com.bcom.drimbox.utils.PrefixConstants;

import io.quarkus.logging.Log;

@Path("/api/conso")
public class StowRs {

	// Stow url (e.g. http://localhost:8080/dcm4chee-arc/aets/DCM4CHEE/rs/studies)
	@ConfigProperty(name = "pacs.stow")
	String stowSuffix;

	@ConfigProperty(name = "pacs.baseUrl")
	String baseUrl;

	// Boundary for multipart request
	private static final String BOUNDARY = "myBoundary";

	public byte[] rawMessage;

	private String getStowUrl() {
		return baseUrl + "/" + stowSuffix;
	}
	/**
	 * 
	 * @param studyUID for the study to stow
	 * @param serieUID if present to only stow this serie
	 * @throws Exception
	 */
	@Path("/export")
	public void stow(@QueryParam("drimboxSourceURL") String drimboxSourceURL, @QueryParam("studyUID") String studyUID, @QueryParam("serieUID") String serieUID) throws Exception {

		try {
			// Map of transfersStyntaxes and q paramaters associated
			Map<String, String> transferSyntaxes = Map.of(
					UID.JPEGLSLossless, "0.9",
					UID.JPEGLosslessSV1, "0.8",
					UID.JPEGBaseline8Bit, "0.7",
					UID.JPEGExtended12Bit, "0.6",
					UID.JPEG2000, "0.6",
					UID.ExplicitVRLittleEndian, "0.5",
					UID.MPEG4HP41, "0.4",
					UID.MPEG2MPML, "0.3",
					UID.MPEG2MPHL, "0.3",
					UID.MPEG4HP41BD, "0.3");
			// Db source URL to ask study 
			Log.info("dbSource : " + drimboxSourceURL);
			String serviceURL = drimboxSourceURL + PrefixConstants.STUDIES_PREFIX + "/" + studyUID + "/series/" + serieUID;
			final URL url = new URL(serviceURL);
			Log.info(url);
			final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		    for (Map.Entry<String, String> entry : transferSyntaxes.entrySet()) {
				connection.addRequestProperty("Accept", "multipart/related; type=\"application/dicom\";transfer-syntax="+entry.getKey()+";q="+entry.getValue()+";boundary="+ BOUNDARY);
		    }
		    Log.info(connection.getContentType());
		    Log.info(connection.getResponseMessage());
		    Log.info(connection.getResponseCode());
			if (connection.getResponseCode() == 200) {
				// Retrieve multipart of serie asked to Db source
				rawMessage = connection.getInputStream().readAllBytes();
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		//		}	


		// Open connection with Pacs
		URLConnection urlConnection = new URL(getStowUrl()).openConnection();
		final HttpURLConnection connection = (HttpURLConnection) urlConnection;

		// Initialize header parameters
		connection.setDoOutput(true);
		connection.setDoInput(true);
		connection.setRequestMethod("POST");
		Map<String, String> requestProperties = new HashMap<>();
		requestProperties.put("Content-Type",
				"multipart/related;type=\"application/dicom\";boundary=" + BOUNDARY);
		requestProperties.put("Accept", "application/dicom+json");
		requestProperties.put("Connection", "keep-alive");
		requestProperties.forEach(connection::setRequestProperty);
		try (OutputStream out = connection.getOutputStream()) {
			// Write series retrieved from Db source
			out.write(rawMessage);

			out.flush();
			Log.info(connection.getResponseMessage());
			Log.info(connection.getErrorStream());
			Log.info(connection.getResponseCode());
			connection.connect();

			connection.disconnect();

		}
	}

}
