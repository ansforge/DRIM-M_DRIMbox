/*
 *  DRIMboxSourceAPI.java - DRIMBox
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

package com.bcom.drimbox.api;

import static com.bcom.drimbox.utils.PrefixConstants.DRIMBOX_PREFIX;
import static com.bcom.drimbox.utils.PrefixConstants.METADATA_PREFIX;
import static com.bcom.drimbox.utils.PrefixConstants.SERIES_PREFIX;
import static com.bcom.drimbox.utils.PrefixConstants.STUDIES_PREFIX;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.UriInfo;

import com.bcom.drimbox.utils.exceptions.RequestErrorException;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestResponse;

import com.bcom.drimbox.dmp.database.DatabaseManager;
import com.bcom.drimbox.dmp.database.SourceEntity;
import com.bcom.drimbox.pacs.CStoreSCP;
import com.bcom.drimbox.psc.ProSanteConnect;
import com.bcom.drimbox.utils.RequestHelper;

import io.quarkus.logging.Log;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;


@Path("/" + DRIMBOX_PREFIX)
public class DRIMboxSourceAPI {
	@ConfigProperty(name = "pacs.wado")
	String wadoSuffix;
	@ConfigProperty(name = "pacs.wadoURI")
	String wadoURISuffix;

	@ConfigProperty(name = "pacs.baseUrl")
	String pacsUrl;

	@ConfigProperty(name = "debug.noAuth", defaultValue="false")
	Boolean noAuth;


	@Inject
	RequestHelper requestHelper;

	@Inject
	CStoreSCP cstoreSCP;

	@Inject
	DatabaseManager databaseManager;

	/**
	 * Bearer token that is in the request. It will be verified with the introspection mechanism of prosanteconnect
	 */
	@HeaderParam("Authorization")
	String bearerToken;
	/**
	 * Pro sante connect service to verify the token
	 */
	@Inject
	ProSanteConnect proSanteConnect;


	@GET
	@Blocking
	@Path("/studies/{studyUID}/series/{seriesUID}")
	@Produces("multipart/related;start=\\\"<1@resteasy-multipart>\\\";transfer-syntax=1.2.840.10008.1.2.1;type=\\\"application/dicom\\\"; boundary=myBoundary")
	public Multi<byte[]> drimboxMultipartWado(String studyUID, String seriesUID, @Context HttpHeaders headers) {
		if (!checkAuthorisation()) {
			Log.fatal("Authentication failure");
			return Multi.createFrom().empty();
		}

		String url = getWadoUrl() + "/" + STUDIES_PREFIX + "/" + studyUID + "/" + SERIES_PREFIX + "/" + seriesUID;
		Map<String, String> tsMap = new HashMap<>();
		for (String header : headers.getRequestHeaders().get("Accept")) {

			tsMap.put(header.split("transfer-syntax=")[1].split(";")[0], header.split("q=")[1].split(";")[0]);
		}
		tsMap = tsMap.entrySet().stream().sorted(Map.Entry.<String, String>comparingByValue().reversed()).collect(Collectors.toMap(
				Map.Entry::getKey, 
				Map.Entry::getValue, 
				(oldValue, newValue) -> oldValue, LinkedHashMap::new));
		List<String> transferSyntaxes = new ArrayList<>(tsMap.keySet());
		String boundary = headers.getRequestHeaders().get("Accept").get(0).split("boundary=")[1];
		cstoreSCP.setBoundary(boundary);

		String sopInstanceUID = headers.getRequestHeader("KOS-SOPInstanceUID").get(0);

		if(!verifySop(sopInstanceUID, studyUID))  {
			Log.error("Verify SOP returned false");
			return Multi.createFrom().empty();
		}

		return requestHelper.fileRequestCMove(url, transferSyntaxes);
	}

	private boolean verifySop(String sopInstanceUID, String studyUID) {
		SourceEntity entity = this.databaseManager.getEntity(studyUID);
		if(entity == null) {
			Log.info("No data found in database");
			return false;
		}

		InputStream targetStream = new ByteArrayInputStream(entity.rawKOS);
		try (DicomInputStream dis = new DicomInputStream(targetStream)) {
			dis.setIncludeBulkData(IncludeBulkData.URI);
			Attributes dataset = dis.readDataset();
			String sopInstanceUIDLocal = dataset.getString(Tag.SOPInstanceUID);

			return sopInstanceUIDLocal.equals(sopInstanceUID);
		} catch (Exception e) {
			Log.error("Error in verifySop");
			Log.error(e.getMessage());
		}

		return false;
	}

	@GET
	@Path("/studies/{studyUID}/series/{seriesUID}/metadata")
	@Produces("application/dicom+json")
	public RestResponse<String> drimboxMetadataRequest(String studyUID, String seriesUID) {
		String url = getWadoUrl() + "/" + STUDIES_PREFIX +"/" + studyUID + "/" + SERIES_PREFIX + "/" + seriesUID + "/" + METADATA_PREFIX;

		return requestHelper.stringRequest(url, this::getPacsConnection);
	}

	@GET
	@Path("/studies/{studyUID}/series")
	@Produces("application/dicom+json")
	public RestResponse<String> drimboxSeriesRequest(String studyUID) {
		String url = getWadoUrl() + "/" + STUDIES_PREFIX + "/" + studyUID + "/" + SERIES_PREFIX;

		return requestHelper.stringRequest(url, this::getPacsConnection);
	}

	@GET
	@Path("/wado")
	@Produces("application/dicom")
	public RestResponse<byte[]> drimboxWadoURI(@Context UriInfo uriInfo) {
		String url = requestHelper.constructUrlWithParam(getWadoURIUrl(), uriInfo);

		return requestHelper.fileRequest(url, this::getPacsConnection);
	}


	private HttpURLConnection getPacsConnection(String pacsUrl) throws RequestErrorException  {
		try {
			final URL url = new URL(pacsUrl);

			if (!checkAuthorisation()) {
				throw new RequestErrorException("Authentication failure", 401);
			}

			final int timeoutValueMS = 60000;
			final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setConnectTimeout(timeoutValueMS);
			connection.setReadTimeout(timeoutValueMS);
			connection.setRequestMethod("GET");
			int responseCode = connection.getResponseCode();

			switch(responseCode) {
				case 404:
					throw new RequestErrorException("Cannot find resource", responseCode);
				case 504:
					throw new RequestErrorException("Pacs didn't respond in time.", responseCode);
				case 200:
				case 206:
					break;
				default:
					throw new RequestErrorException("Pacs request failed.", responseCode);
			}

			return connection;
		} catch (ProtocolException e) {
			throw new RequestErrorException("ProtocolException : " + e.getMessage(), 500);
		} catch (MalformedURLException e) {
			throw new RequestErrorException("MalformedURLException : " + e.getMessage(), 500);
		} catch (SocketTimeoutException e) {
			throw new RequestErrorException("Pacs didn't respond in time. " + e.getMessage(), 504);
		} catch (ConnectException e) {
			Log.error(String.format("Pacs at %s is not responding.", pacsUrl));
			throw new RequestErrorException("Pacs is not responding. " + e.getMessage(), 502);
		} catch (IOException e) {
			throw new RequestErrorException("IOException : " + e.getMessage(), 500);
		}
	}

	private boolean checkAuthorisation()  {
		return noAuth || (!bearerToken.isEmpty() && proSanteConnect.introspectToken(bearerToken));
	}


	// Get wado url (e.g. http://localhost:8080/dcm4chee-arc/aets/AS_RECEIVED/rs)
	private String getWadoUrl() {
		return pacsUrl + "/" + wadoSuffix;
	}
	// Get wado URI url (e.g. http://localhost:8080/dcm4chee-arc/aets/AS_RECEIVED/wado)
	private String getWadoURIUrl() {
		return pacsUrl + "/" + wadoURISuffix;
	}



}
