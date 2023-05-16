/*
 *  DRIMboxConsoAPI.java - DRIMBox
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

import com.bcom.drimbox.dmp.xades.file.KOSFile;
import com.bcom.drimbox.utils.exceptions.RequestErrorException;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import com.bcom.drimbox.dmp.auth.WebTokenAuth;
import com.bcom.drimbox.pacs.PacsCache;
import com.bcom.drimbox.utils.RequestHelper;
import io.vertx.core.Vertx;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.util.TagUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestResponse;

import javax.inject.Inject;
import javax.json.*;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Function;

import static com.bcom.drimbox.utils.PrefixConstants.*;


@Path("/")
public class DRIMboxConsoAPI {

	@Inject
	WebTokenAuth webTokenAuth;

	@Inject
	RequestHelper requestHelper;


	/**
	 * Http protocol (may be changed later to https://)
	 */
	public static final String HTTP_PROTOCOL = "http://";
	public static final String DICOM_FILE_PREFIX = "dicomfile";
	@Inject
	PacsCache pacsCache;

	/**
	 * This will contain the cookieID in the form of "Bearer cookieID"
	 */
	@HeaderParam("Authorization")
	@DefaultValue("")
	String authHeader;

	@ConfigProperty(name = "debug.noAuth", defaultValue="false")
	Boolean noAuth;

	private final Vertx vertx;

	@Inject
	public DRIMboxConsoAPI(Vertx vertx) {
		this.vertx = vertx;
	}


	@GET
	@Path("wado/{drimboxSourceURL}")
	@Produces("application/dicom")
	public Uni<RestResponse<byte[]>> wadoRequest(String drimboxSourceURL, @Context UriInfo uriInfo) {

		if (!checkAuthorization())
			return Uni.createFrom().item(requestHelper.getDeniedFileResponse(401));

		MultivaluedMap<String, String> params = uriInfo.getQueryParameters();
		String studyUID = params.get("studyUID").get(0);
		String seriesUID = params.get("seriesUID").get(0);
		String instanceUID = params.get("objectUID").get(0);

		Log.info("[WADO] Request : " + instanceUID);

		try {
			Future<byte[]> future = pacsCache.getDicomFile(studyUID, seriesUID, instanceUID);
			return Uni.createFrom().future(future).onItem().transform(
					item -> {
						Log.info("[WADO] Response : " + instanceUID);
						return RestResponse.ResponseBuilder.ok(item).build();
					}
					);
		} catch (Exception e) {
			// TODO : Is this really fatal ?
			Log.fatal("Can't get file from cache");
			return Uni.createFrom().item(requestHelper.getDeniedFileResponse());
		}
	}

	@GET
	@Path("rs/{drimboxSourceURL}/studies/{studyUID}")
	public RestResponse<byte[]> studyRequest(String drimboxSourceURL, String studyUID) {
		return seriesRequest(drimboxSourceURL, studyUID, "");
	}


	@GET
	@Path("rs/{drimboxSourceURL}/studies/{studyUID}/series/{seriesUID}")
	//@Produces(MediaType.MULTIPART_FORM_DATA)
	public RestResponse<byte[]> seriesRequest(String drimboxSourceURL, String studyUID, String seriesUID) {
		String url = HTTP_PROTOCOL + drimboxSourceURL + "/" + DRIMBOX_PREFIX + "/" + STUDIES_PREFIX + "/" + studyUID;
		if (!seriesUID.isEmpty()) {
			url += "/series/" + seriesUID;
		}

		return requestHelper.fileRequest(url, this::getDrimboxConnection);
	}

	@GET
	@Path("rs/{drimboxSourceURL}/studies/{studyUID}/series")
	@Produces("application/dicom+json")
	public RestResponse<String> seriesListRequest(String drimboxSourceURL, String studyUID) {
		String url = HTTP_PROTOCOL + drimboxSourceURL + "/" + DRIMBOX_PREFIX + "/" + STUDIES_PREFIX + "/" + studyUID + "/" + SERIES_PREFIX;
		return requestHelper.stringRequest(url, this::getDrimboxConnection);
	}

	@GET
	@Path("rs/{drimboxSourceURL}/studies/{studyUID}/series/{seriesUID}/metadata")
	@Produces("application/dicom+json")
	public RestResponse<String> metadataRequest(String drimboxSourceURL, String studyUID, String seriesUID) {
		String url = HTTP_PROTOCOL + drimboxSourceURL + "/" + DRIMBOX_PREFIX + "/" + STUDIES_PREFIX + "/" + studyUID;
		if (!seriesUID.isEmpty()) {
			url += "/series/" + seriesUID;
		}
		String sopInstanceUID = "";
		// This is non-blocking operation
		pacsCache.addNewEntry(drimboxSourceURL, getAccessToken(), studyUID, seriesUID, sopInstanceUID);

		return requestHelper.stringRequest(url + "/" + METADATA_PREFIX, this::getDrimboxConnection);
	}

	private String getAccessToken() {
		String accessToken = "noAuthDebugOnly";
		if (!noAuth) {
			accessToken = webTokenAuth.getAccessToken(getCookieID()).getRawAccessToken();
		}
		return accessToken;
	}

	@Produces(MediaType.APPLICATION_JSON)
	@GET
	@Path("ohifmetadata/{drimboxSourceUrl}")
	public String getOHIFMetadata(@Context UriInfo uriInfo, String drimboxSourceUrl) {

		JsonObjectBuilder root = Json.createObjectBuilder();
		JsonObjectBuilder servers = Json.createObjectBuilder();
		JsonArrayBuilder dicomWebArray = Json.createArrayBuilder();
		JsonObjectBuilder dicomWebObject =  Json.createObjectBuilder();

		dicomWebObject.add("name", "DCM4CHEE");

		dicomWebObject.add("wadoUriRoot", uriInfo.getBaseUri() + "wado/" + drimboxSourceUrl);
		dicomWebObject.add("wadoRoot", uriInfo.getBaseUri() + "rs/" + drimboxSourceUrl);
		dicomWebObject.add("qidoRoot", uriInfo.getBaseUri() + "rs/" + drimboxSourceUrl);

		// Those are here for debug purpose in case something is wrong with the forwarding
		//        dicomWebObject.add("wadoUriRoot", "http://localhost:8012/dcm4chee-arc/aets/AS_RECEIVED/wado");
		//        dicomWebObject.add("qidoRoot", "http://localhost:8012/dcm4chee-arc/aets/AS_RECEIVED/rs");
		//        dicomWebObject.add("wadoRoot", "http://localhost:8012/dcm4chee-arc/aets/AS_RECEIVED/rs");

		// Todo : Test wadoRS for imageRendering
		dicomWebObject.add("imageRendering", "wadouri");
		dicomWebObject.add("thumbnailRendering", "wadouri");
		dicomWebObject.add("enableStudyLazyLoad", true);
		dicomWebObject.add("qidoSupportsIncludeField", true);

		dicomWebArray.add(dicomWebObject);
		servers.add("dicomWeb", dicomWebArray);
		root.add("servers", servers);

		return root.build().toString();
	}



	@Produces(MediaType.APPLICATION_JSON)
	@GET
	@Path("ohifv3metadata/{studyUID}/{seriesUID}/{sopInstanceUID}")
	public Uni<Response> getOHIFv3Metadata(@Context UriInfo uriInfo, String studyUID, String seriesUID, String sopInstanceUID) {
		String drimboxSourceURL;
		// Get drimbox source url from KOS
		KOSFile kos = DmpAPI.getKOS(studyUID);

//		// TODO : this is for testing purpose only
//		ClassLoader classLoader = getClass().getClassLoader();
//		InputStream inputStream = classLoader.getResourceAsStream("testKos.dcm");
//		KOSFile kos = null;
//		try {
//			kos = new KOSFile(inputStream.readAllBytes());
//		} catch (IOException e) {
//			throw new RuntimeException(e);
//		}

		if (kos == null) {
			final String errorMessage = "Can't find KOS associated with study UID " + studyUID;
			Log.error(errorMessage);
			return Uni.createFrom().item(Response.ok(errorMessage).status(404).build());
		}

		KOSFile.SeriesInfo seriesInfo = kos.getSeriesInfo().get(seriesUID);
		if (seriesInfo == null) {
			final String errorMessage = "Can't find series " + seriesUID + " in KOS " + studyUID;
			Log.error(errorMessage);
			Log.info("Available series : ");
			Log.info(kos.getSeriesInfo().keySet());
			return Uni.createFrom().item(Response.ok(errorMessage).status(404).build());
		}

		if (seriesInfo.instancesUID.isEmpty()) {
			final String errorMessage = "Series info doesn't have any instance ID";
			Log.error(errorMessage);
			return Uni.createFrom().item(Response.ok(errorMessage).status(404).build());
		}

		// Get drimboxSource url
		try {
			URL u = new URL(seriesInfo.retrieveURL);
			String protocol = u.getProtocol();
			String authority = u.getAuthority();
			drimboxSourceURL = String.format("%s://%s", protocol, authority);

		} catch (MalformedURLException e) {
			e.printStackTrace();
			return Uni.createFrom().item(Response.ok(e.getMessage()).status(500).build());
		}


		// Add series to the cache
		// This is non-blocking operation
		var cacheFuture = pacsCache.addNewEntry(drimboxSourceURL, getAccessToken(), studyUID, seriesUID, sopInstanceUID);

		CompletableFuture<Response> completableFuture = new CompletableFuture<>();

		String patientINS = kos.getPatientINS();
		vertx.eventBus().consumer( PacsCache.getEventBusID(studyUID, seriesUID), message ->  {
			// OHIF needs metadata in advance for images. We work around that by taking one image in the series
			// and we extract their metadata.
			String referenceInstanceUID = message.body().toString();

			Attributes attributes;
			try {
				byte[] dicomFile = pacsCache.getDicomFile(studyUID, seriesUID, referenceInstanceUID).get();
				DicomInputStream dis = new DicomInputStream(new ByteArrayInputStream(dicomFile));
				attributes = dis.readDataset();

			} catch (Exception e) {
				Log.error("Can't get dicom file from cache.");
				e.printStackTrace();
				completableFuture.complete(Response.noContent().status(500).build());
				return;
			}

			JsonObjectBuilder root = Json.createObjectBuilder();
			JsonArrayBuilder studiesArray = Json.createArrayBuilder();
			JsonArrayBuilder seriesArray = Json.createArrayBuilder();
			JsonArrayBuilder instancesArray = Json.createArrayBuilder();

			JsonObjectBuilder seriesObject = Json.createObjectBuilder();
			seriesObject.add("SeriesInstanceUID", seriesUID);


			// TODO : temporary until we can get this from the KOS
			int instanceNumber = 0;


			for(String currentInstanceUID : seriesInfo.instancesUID) {
				Function<Integer, String> getStringField = (var tag) ->  attributes.getString(tag, "");
				Function<Integer, Integer> getIntField = (var tag) -> attributes.getInt(tag, 0);

				JsonObjectBuilder metadata = Json.createObjectBuilder();
				metadata.add("SOPInstanceUID", currentInstanceUID);

				metadata.add("SeriesInstanceUID", getStringField.apply(Tag.SeriesInstanceUID));
				metadata.add("StudyInstanceUID", getStringField.apply(Tag.StudyInstanceUID));
				metadata.add("SOPClassUID", getStringField.apply(Tag.SOPClassUID));
				metadata.add("Modality", getStringField.apply(Tag.Modality));
				metadata.add("Columns", getIntField.apply(Tag.Columns));
				metadata.add("Rows", getIntField.apply(Tag.Rows));
				metadata.add("PixelRepresentation", getIntField.apply(Tag.PixelRepresentation));
				metadata.add("BitsAllocated", getIntField.apply(Tag.BitsAllocated));
				metadata.add("BitsStored", getIntField.apply(Tag.BitsStored));
				metadata.add("SamplesPerPixel", getIntField.apply(Tag.SamplesPerPixel));
				metadata.add("HighBit", getIntField.apply(Tag.HighBit));
				metadata.add("PhotometricInterpretation", getStringField.apply(Tag.PhotometricInterpretation));
				metadata.add("INS", patientINS);


				// TODO : handle instance number from KOS
				metadata.add("InstanceNumber", instanceNumber++);


				// Add to the instance list
				instancesArray.add(Json.createObjectBuilder()
						.add("metadata", metadata)
						.add("url", "dicomweb:" + uriInfo.getBaseUri() + DICOM_FILE_PREFIX + "/" + studyUID + "/" + seriesUID + "/" + currentInstanceUID )
				);
			}

			seriesObject.add("instances", instancesArray);
			seriesObject.add("Modality", "CT");
			seriesArray.add(seriesObject);

			JsonObjectBuilder study = Json.createObjectBuilder();
			study.add("StudyInstanceUID", studyUID);
			study.add("series", seriesArray);
			study.add("NumInstances", seriesInfo.instancesUID.size());

			study.add("StudyDate",  attributes.getString(Tag.StudyDate, "20000101"));
			study.add("StudyTime", attributes.getString(Tag.StudyTime, ""));
			study.add("PatientName", attributes.getString(Tag.PatientName, "Anonymous"));
			study.add("PatientID", attributes.getString(Tag.PatientID, ""));
			study.add("AccessionNumber", "");
			study.add("PatientAge", attributes.getString(Tag.PatientAge, ""));
			study.add("PatientSex", attributes.getString(Tag.PatientSex, ""));
			study.add("StudyDescription", "");
			// TODO handle modalities (maybe not necessary ?)
			study.add("Modalities", attributes.getString(Tag.Modality, ""));

			studiesArray.add(study);

			root.add("studies", studiesArray);

			String ohifMetadata = root.build().toString();
			completableFuture.complete(Response.ok(ohifMetadata).build());

		});


		cacheFuture.onComplete(
				entryAdded -> {
			// We need to fire the event on the event bus ourselves since the data is already in the cache
			if (entryAdded.succeeded() && entryAdded.result() == 0) {
				vertx.eventBus().publish(PacsCache.getEventBusID(studyUID, seriesUID),
						// We take the first instance ID of the series
						pacsCache.getFirstInstanceNumber(studyUID, seriesUID)
						// NOTE : we do not do something like this : seriesInfo.instancesUID.get(0) in case of missing images
				);
			// This should not happen, but it is here in a fail-case scenario
			} else if (entryAdded.succeeded() && !completableFuture.isDone()) {
				completableFuture.complete(Response.ok("Cache was created but JSON data is empty").status(500).build());
			}
		});

		cacheFuture.onFailure(
				e -> {
					RequestErrorException exception = (RequestErrorException) e;
					switch (exception.getErrorCode()) {
						case 1404:
							completableFuture.complete(Response.ok(e.getMessage()).status(404).build());
							break;
						case 404:
							completableFuture.complete(Response.ok("Can't find image(s) on PACS. Maybe it was deleted ?").status(410).build());
							break;
						default:
							completableFuture.complete(Response.ok(exception.getMessage()).status(exception.getErrorCode()).build());
					}
				});

		return Uni.createFrom().future(completableFuture);
	}

	@Produces(MediaType.APPLICATION_JSON)
	@GET
	@Path("ohifv3metadata/{drimboxSourceURL}/{studyUID}/{seriesUID}/{sopInstanceUID}")
	public Response getOHIFv3Metadata(@Context UriInfo uriInfo, String drimboxSourceURL, String studyUID, String seriesUID, String sopInstanceUID) {
		// TODO : this is direct access to the contents and will be removed in the future

		// Note : This will also populate the cache
		// First see if we can get the metadata from the source
		// TODO : check auth
		String url = HTTP_PROTOCOL + drimboxSourceURL + "/" + DRIMBOX_PREFIX + "/" + STUDIES_PREFIX + "/" + studyUID;
		if (!seriesUID.isEmpty()) {
			url += "/series/" + seriesUID;
		}
		var response = requestHelper.stringRequest(url + "/" + METADATA_PREFIX, this::getDrimboxConnection);

		int responseCode = response.getStatus();
		String responseMessage = response.getEntity();
		switch(responseCode) {
			case 502:
			case 504:
				return Response.ok(responseMessage).status(responseCode).build();
			case 404:
				return Response.ok(String.format("Series %s (study : %s) not found at %s", seriesUID, studyUID, drimboxSourceURL)).status(responseCode).build();
			case 200:
				break;
			default:
				return Response.ok(String.format("Error when retrieving metadata at %s for %s / %s / %s. Reason : %s", drimboxSourceURL, studyUID, seriesUID, sopInstanceUID, responseMessage)).status(responseCode).build();
		}


		// Add series to the cache
		// This is non-blocking operation
		pacsCache.addNewEntry(drimboxSourceURL, getAccessToken(), studyUID, seriesUID, sopInstanceUID);


		// Read metadata from server
		var jsonReader = Json.createReader(new StringReader(responseMessage));
		JsonArray dicomMetadata = jsonReader.readArray();


		JsonObjectBuilder root = Json.createObjectBuilder();
		JsonArrayBuilder studiesArray = Json.createArrayBuilder();
		JsonArrayBuilder seriesArray = Json.createArrayBuilder();
		JsonArrayBuilder instancesArray = Json.createArrayBuilder();

		JsonObjectBuilder seriesObject = Json.createObjectBuilder();
		seriesObject.add("SeriesInstanceUID", seriesUID);

		for(Object dicomInstanceMetadata : dicomMetadata) {
			JsonObject currentInstanceJson = (JsonObject) dicomInstanceMetadata;
			Function<Integer, String> getStringField = (var tag) ->  currentInstanceJson.getJsonObject(TagUtils.toHexString(tag)).getJsonArray("Value").getString(0);
			Function<Integer, Integer> getIntField = (var tag) ->  currentInstanceJson.getJsonObject(TagUtils.toHexString(tag)).getJsonArray("Value").getInt(0);

			JsonObjectBuilder metadata = Json.createObjectBuilder();
			String instanceUID = getStringField.apply(Tag.SOPInstanceUID);
			metadata.add("SOPInstanceUID", instanceUID);
			metadata.add("SeriesInstanceUID", getStringField.apply(Tag.SeriesInstanceUID));
			metadata.add("StudyInstanceUID", getStringField.apply(Tag.StudyInstanceUID));
			metadata.add("SOPClassUID", getStringField.apply(Tag.SOPClassUID));
			metadata.add("Modality", getStringField.apply(Tag.Modality));
			metadata.add("Columns", getIntField.apply(Tag.Columns));
			metadata.add("Rows", getIntField.apply(Tag.Rows));
			metadata.add("PixelRepresentation", getIntField.apply(Tag.PixelRepresentation));
			metadata.add("BitsAllocated", getIntField.apply(Tag.BitsAllocated));
			metadata.add("BitsStored", getIntField.apply(Tag.BitsStored));
			metadata.add("SamplesPerPixel", getIntField.apply(Tag.SamplesPerPixel));
			metadata.add("HighBit", getIntField.apply(Tag.HighBit));
			metadata.add("PhotometricInterpretation", getStringField.apply(Tag.PhotometricInterpretation));
			metadata.add("InstanceNumber", getStringField.apply(Tag.InstanceNumber));



			// Add to the instance list
			instancesArray.add(Json.createObjectBuilder()
					.add("metadata", metadata)
					.add("url", "dicomweb:" + uriInfo.getBaseUri() + DICOM_FILE_PREFIX + "/" + studyUID + "/" + seriesUID + "/" + instanceUID )
			);
		}
		seriesObject.add("instances", instancesArray);
		seriesObject.add("Modality", "CT");
		seriesArray.add(seriesObject);

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
		// TODO handle modalities (maybe not necessary ?)
		study.add("Modalities", "CT");

		studiesArray.add(study);

		root.add("studies", studiesArray);

		return Response.ok(root.build().toString()).build();
	}


	// Note : this method create a JSON for OHIF AND populate the cache

	@GET
	@Path(DICOM_FILE_PREFIX + "/{studyUID}/{seriesUID}/{instanceUID}")
	//@Produces("application/dicom")
	public Uni<RestResponse<byte[]>> getDicomFile(String studyUID, String seriesUID, String instanceUID) {
		if (!checkAuthorization())
			return Uni.createFrom().item(requestHelper.getDeniedFileResponse(401));

		try {
			Future<byte[]> future = pacsCache.getDicomFile(studyUID, seriesUID, instanceUID);
			return Uni.createFrom().future(future).onItem().transform(
					item -> {
						Log.info("[dicomfile] Response : " + instanceUID);
						return RestResponse.ResponseBuilder.ok(item).header("Accept-Ranges", "bytes").build();
					}
					)
					.onFailure().recoverWithItem(requestHelper.getDeniedFileResponse(404));
		} catch (Exception e) {
			Log.error("Can't get file from cache");
			e.printStackTrace();
			return Uni.createFrom().item(requestHelper.getDeniedFileResponse());
		}
	}

	private Boolean checkAuthorization() {
		if(noAuth)
			return true;

		// Check auth header
		// We expect something like "Bearer cookieID" from OHIF
		if (authHeader == null || authHeader.isEmpty())
			return false;

		return webTokenAuth.clientRegistered(getCookieID());
	}

	private String getCookieID() {
		// Remove Bearer prefix (the space after is important)
		return authHeader.replace("Bearer ", "");
	}

	private HttpURLConnection getDrimboxConnection(String drimboxUrl) throws RequestErrorException {

		try {
			Log.info("Check auth with cookie ID...");
			if (!checkAuthorization()) {
				throw new RequestErrorException("Cookie ID is not valid", 401);
			}
			Log.info("Auth is ok.");

			final URL url = new URL(drimboxUrl);

			final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");

			if (!noAuth)
				connection.setRequestProperty("Authorization", webTokenAuth.getAccessToken(getCookieID()).getRawAccessToken());

			int responseCode = connection.getResponseCode();

			switch (responseCode) {
				case 502:
					throw new RequestErrorException("Pacs failed to respond", responseCode);
				case 504:
					throw new RequestErrorException("Pacs timeout", responseCode);
				case 200:
				case 206:
					break;
				default:
					throw new RequestErrorException("DRIMbox request failed with code " + responseCode, responseCode);
			}

			return connection;
		} catch (ProtocolException e) {
			throw new RequestErrorException("ProtocolException : " + e.getMessage(), 500);
		} catch (MalformedURLException e) {
			throw new RequestErrorException("MalformedURLException : " + e.getMessage(), 500);
		} catch (ConnectException e) {
			Log.error(String.format("DRIMbox at %s is not responding.", drimboxUrl));
			Log.error(String.format("Error : %s", e.getMessage()));

			throw new RequestErrorException("DRIMbox source is not responding.", 502);
		} catch (IOException e) {
			throw new RequestErrorException("IOException : " + e.getMessage(), 500);
		}


	}
}

