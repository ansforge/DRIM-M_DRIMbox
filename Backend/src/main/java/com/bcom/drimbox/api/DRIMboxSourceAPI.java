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

import static com.bcom.drimbox.utils.PrefixConstants.METADATA_PREFIX;
import static com.bcom.drimbox.utils.PrefixConstants.SERIES_PREFIX;
import static com.bcom.drimbox.utils.PrefixConstants.STUDIES_PREFIX;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.util.TagUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestMulti;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import com.bcom.drimbox.dmp.database.DatabaseManager;
import com.bcom.drimbox.dmp.database.SourceEntity;
import com.bcom.drimbox.psc.ProSanteConnect;
import com.bcom.drimbox.utils.RequestHelper;
import com.bcom.drimbox.utils.exceptions.RequestErrorException;
import com.bcom.drimbox.utils.exceptions.WadoErrorException;

import io.quarkus.logging.Log;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;


@Path("/api/source")
public class DRIMboxSourceAPI {
	@ConfigProperty(name = "pacs.wado")
	String wadoSuffix;
	@ConfigProperty(name = "pacs.wadoURI")
	String wadoURISuffix;

	@ConfigProperty(name = "pacs.baseUrl")
	String pacsUrl;

	@ConfigProperty(name = "debug.noAuth", defaultValue="false")
	Boolean noAuth;

	@ConfigProperty(name = "source.host")
	String sourceHost;

	@Inject
	RequestHelper requestHelper;


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

	public static final String DICOM_FILE_PREFIX = "dicomfile";

	@GET
	@Produces("multipart/related")
	@Blocking
	@Path("/studies/{studyUID}/series/{seriesUID}")
	public Multi<byte[]> drimboxMultipartWado(String studyUID, String seriesUID, @Context HttpHeaders headers) {

		final String ACCEPTED_FORMAT_SAMPLE = "Accepted format : transfer-syntax=1.2.840.10008.1.2.4.50;q=0.9;boundary=myBoundary, transfer-syntax=1.2.840.10008.1.2.4.50;q=0.5;boundary=myBoundary";

		if (!checkAuthorisation()) {
			return createError("Authentication failure", 401);
		}

		String url = getWadoUrl() + "/" + STUDIES_PREFIX + "/" + studyUID + "/" + SERIES_PREFIX + "/" + seriesUID;

		List<String> acceptHeaderList = headers.getRequestHeaders().get("Accept");
		if(acceptHeaderList == null || acceptHeaderList.isEmpty())  {
			return createError("Missing Accept header. " + ACCEPTED_FORMAT_SAMPLE, 400);
		}


		if(acceptHeaderList.size() == 1)  {
			acceptHeaderList =  Arrays.asList(acceptHeaderList.get(0).split(","));

			Log.info("Found " + acceptHeaderList.size() + " transfer syntax");
		}


		String boundary = "";
		Map<String, String> tsMap = new HashMap<>();
		for (String header : acceptHeaderList) {

			String transfer_syntax = regexExtractor("transfer-syntax=([\\d+\\.?]+)", header);
			if (transfer_syntax == null) {
				return createError("Can't find transfer-syntax field for : " + header, 400);
			}

			String q = regexExtractor("q=(\\d\\.\\d)", header);
			if (q == null) {
				return createError("Can't find q field for : " + header , 400);
			}

			if (boundary.isEmpty()) {
				boundary = regexExtractor("boundary=([^,;.]+)", header);
				if (boundary == null) {
					return createError("Can't find boundary field for : " + header, 400);
				}
			}

			tsMap.put(transfer_syntax, q);
		}

		if (tsMap.isEmpty()) {
			Log.error(acceptHeaderList);
			return createError("Invalid Accept header. Could not find transfer-syntax,q parameter or boundary. " + ACCEPTED_FORMAT_SAMPLE,
					400);
		}

		// Sort by q value
		tsMap = tsMap.entrySet().stream().sorted(Map.Entry.<String, String>comparingByValue().reversed()).collect(Collectors.toMap(
				Map.Entry::getKey,
				Map.Entry::getValue,
				(oldValue, newValue) -> oldValue, LinkedHashMap::new));
		List<String> acceptedTransferSyntax = new ArrayList<>(tsMap.keySet());
		if(acceptedTransferSyntax.isEmpty()) {
			return createError("Transfer syntax list is empty", 500);
		}
		List<String> preferredTransferSyntax = new ArrayList<String>();
		for (var entry : tsMap.entrySet()) {
		    if(Float.parseFloat(entry.getValue()) > 0.7) {
		    	preferredTransferSyntax.add(entry.getKey());
		    }
		    	
		}

		List<String> sopInstanceUIDHeader = headers.getRequestHeader("KOS-SOPInstanceUID");

		if(sopInstanceUIDHeader.isEmpty())  {
			return createError("Missing KOS-SOPInstanceUID", 400);
		}

		String sopInstanceUID = sopInstanceUIDHeader.get(0);
		if(!verifySop(sopInstanceUID, studyUID))  {
			return createError(String.format("Can't find KOS in database. SopInstance : %s / Study : %s ", sopInstanceUID, studyUID), 404);
		}

		String contentType = String.format("multipart/related;start=\"<1@resteasy-multipart>\";type=\"application/dicom\"; boundary=%s", boundary);

		return RestMulti.fromMultiData(requestHelper.fileRequestCMove(url, acceptedTransferSyntax, preferredTransferSyntax, boundary))
				.header("Content-Type", contentType)
				.build();
	}


	private String regexExtractor(String regex, String baseString) {
		final Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
		final Matcher matcher = pattern.matcher(baseString);
		if (!matcher.find())
			return null;

		return matcher.group(1);
	}


	@ServerExceptionMapper
	public RestResponse<String> mapException(WadoErrorException x) {
		return RestResponse.ResponseBuilder.ok(x.getMessage()).status(x.getErrorCode()).build();
	}


	private Multi<byte[]> createError(String error, int code) {
		Log.error(error);
		return Multi.createFrom().failure(new WadoErrorException(error, code));
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

	
	/**
	 * API to handle call to open DB source viewer. 
	 * It generates json after a wado-rs metadata request to the pacs
	 * @param studyUID
	 * @param uriInfo
	 * @param idCDA
	 * @return
	 */
	@Produces(MediaType.APPLICATION_JSON)
	@GET
	@Path("metadata/{studyUID}")
	public Response getSourceMetadata(String studyUID, @Context UriInfo uriInfo, @QueryParam("idCDA") String idCDA, 
			@QueryParam("accessionNumber") String accessionNumber, @QueryParam("requestType") String requestType) {
		final String pacsUrl = getWadoUrl();

		String url = pacsUrl + "/" + STUDIES_PREFIX +"/" + studyUID + "/" + METADATA_PREFIX;
		var response = requestHelper.stringRequest(url, this::getPacsConnection);

		int responseCode = response.getStatus();
		String responseMessage = response.getEntity();
		switch(responseCode) {
			case 502:
			case 504:
				return Response.ok(responseMessage).status(responseCode).build();
			case 404:
				Log.info(studyUID + " not correct : " + studyUID);
				return Response.ok(String.format("Study : %s not found at %s", studyUID, pacsUrl)).status(responseCode).build();
			case 200:
				break;
			default:
				return Response.ok(String.format("Error when retrieving metadata at %s for study %s. Reason : %s", pacsUrl, studyUID, responseMessage)).status(responseCode).build();
		}
		
		SourceEntity entity = this.databaseManager.getEntity(studyUID);
		if(entity == null) {
			Log.info("No data found in database");
		}

		if(!entity.cdaID.equals(idCDA.split("_")[0])) {
			Log.info("incorrect value between idcda in request : " + idCDA.split("_")[0] + " and idcda in bdd : " + entity.cdaID);
			return Response.ok(String.format("incorrect idcda : %s and %s", idCDA.split("_")[0], entity.cdaID)).build();
		}
		
		// Read metadata from server
		var jsonReader = Json.createReader(new StringReader(responseMessage));
		JsonArray dicomMetadata = jsonReader.readArray();


		JsonObjectBuilder root = Json.createObjectBuilder();
		JsonArrayBuilder studiesArray = Json.createArrayBuilder();
		JsonArrayBuilder seriesArray = Json.createArrayBuilder();



		Map<String, JsonObjectBuilder> seriesMap = new HashMap<>();
		Map<String, JsonArrayBuilder> instanceMap = new HashMap<>();

		for(Object dicomInstanceMetadata : dicomMetadata) {

			JsonObject currentInstanceJson = (JsonObject) dicomInstanceMetadata;
			Function<Integer, String> getStringField = (var tag) ->  currentInstanceJson.getJsonObject(TagUtils.toHexString(tag)).getJsonArray("Value").getString(0);

			final String currentSeriesUID = getStringField.apply(Tag.SeriesInstanceUID);

			// If our series is not already in the map we add it
			if (!seriesMap.containsKey(currentSeriesUID)) {
				JsonObjectBuilder seriesObject = Json.createObjectBuilder();
				seriesObject.add("SeriesInstanceUID", currentSeriesUID);

				seriesMap.put(currentSeriesUID, seriesObject);
			}

			JsonObjectBuilder metadata = Json.createObjectBuilder();
			String instanceUID = getStringField.apply(Tag.SOPInstanceUID);
			metadata.add("SOPInstanceUID", instanceUID);
			metadata.add("SeriesInstanceUID", currentSeriesUID);
			metadata.add("StudyInstanceUID", getStringField.apply(Tag.StudyInstanceUID));
			metadata.add("SOPClassUID", getStringField.apply(Tag.SOPClassUID));
			metadata.add("InstanceNumber", getStringField.apply(Tag.InstanceNumber));


			if (!instanceMap.containsKey(currentSeriesUID)) {
				JsonArrayBuilder instancesArray = Json.createArrayBuilder();
				instanceMap.put(currentSeriesUID, instancesArray);
			}

			JsonArrayBuilder currentInstancesArray = instanceMap.get(currentSeriesUID);

			// Add to the instance list
			currentInstancesArray.add(Json.createObjectBuilder()
					.add("metadata", metadata)
					.add("url", "wadouri:" + this.sourceHost + "/" + "api/source" + "/" + DICOM_FILE_PREFIX + "/" + studyUID + "/" + currentSeriesUID + "/" + instanceUID )
			);

		}

		for (Map.Entry<String, JsonObjectBuilder> entry : seriesMap.entrySet()) {
			String seriesUID = entry.getKey();
			JsonObjectBuilder currentSeriesJsonObject = entry.getValue();

			currentSeriesJsonObject.add("instances", instanceMap.get(seriesUID));
			seriesArray.add(currentSeriesJsonObject);
		}


		JsonObjectBuilder study = Json.createObjectBuilder();
		study.add("StudyInstanceUID", studyUID);
		study.add("series", seriesArray);
		study.add("NumInstances", dicomMetadata.size());

		study.add("StudyDate", "20000101");
		study.add("StudyTime", "");
		study.add("PatientName", "");
		study.add("PatientID", "LOL");
		study.add("AccessionNumber", "");
		study.add("PatientAge", "");
		study.add("PatientSex", "");
		study.add("StudyDescription", "");

		studiesArray.add(study);

		root.add("studies", studiesArray);

		return Response.ok(root.build().toString()).build();
	}

	/**
	 * Retrieve each images from the pacs during a call to DB source viewer
	 * @param studyUID
	 * @param seriesUID
	 * @param instanceUID
	 * @return
	 */
	// TODO : add authentification
	@GET
	@Path(DICOM_FILE_PREFIX + "/{studyUID}/{seriesUID}/{instanceUID}")
	public RestResponse<byte[]> getDicomFile(String studyUID, String seriesUID, String instanceUID) {
		String url = getWadoUrl() + "/" + STUDIES_PREFIX +"/" + studyUID + "/" + SERIES_PREFIX + "/" + seriesUID + "/instances/" +  instanceUID;

		var dicomFiles = requestHelper.multipartFileRequest(url, this::getPacsConnection);

		if (dicomFiles.size() != 1) {
			Log.error("Should have retrieve only one dicom file");
			return RestResponse.noContent();
		}

		return RestResponse.ok(dicomFiles.get(0));
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




}
