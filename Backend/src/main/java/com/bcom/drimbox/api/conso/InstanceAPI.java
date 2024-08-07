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

package com.bcom.drimbox.api.conso;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestResponse;

import com.bcom.drimbox.auth.WebTokenAuth;
import com.bcom.drimbox.document.KOSFile;
import com.bcom.drimbox.pacs.PacsCacheConso;
import com.bcom.drimbox.utils.RequestHelper;
import com.bcom.drimbox.utils.exceptions.RequestErrorException;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;


/**
 * API to manage images requests coming from OHIF
 * 
 */
@Path("/api/conso")
public class InstanceAPI {

	@Inject
	WebTokenAuth webTokenAuth;

	@Inject
	RequestHelper requestHelper;

	@ConfigProperty(name = "conso.host")
	String consoHost;

	/**
	 * Http protocol (may be changed later to https://)
	 */
	public static final String HTTP_PROTOCOL = "http://";
	public static final String DICOM_FILE_PREFIX = "dicomfile";
	@Inject
	PacsCacheConso pacsCache;

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
	public InstanceAPI(Vertx vertx) {
		this.vertx = vertx;
	}

	private String getAccessToken() {
		String accessToken = "noAuthDebugOnly";
		if (!noAuth) {
			accessToken = webTokenAuth.getAccessToken(getCookieID()).getRawAccessToken();
		}
		return accessToken;
	}

	/**
	 *  Generates a reduced JSON datasource from KOS related to Study/Series/Instances UID given 
	 */
	@Produces(MediaType.APPLICATION_JSON)
	@GET
	@Path("ohifv3metadata/{studyUID}/{seriesUID}/{sopInstanceUID}")
	public Uni<Response> getOHIFv3Metadata(@Context UriInfo uriInfo, String studyUID, String seriesUID, String sopInstanceUID) throws IOException {
		String drimboxSourceURL;
		// Get drimbox source url from KOS
		KOSFile kos = DmpAPI.getKOS(studyUID);

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
			String path = u.getPath().split("/studies")[0].substring(1);
			//drimboxSourceURL = String.format("%s://%s", protocol, authority);
			drimboxSourceURL = String.format("%s://%s/%s", protocol, authority, path);
			Log.info(drimboxSourceURL);
		} catch (MalformedURLException e) {
			e.printStackTrace();
			return Uni.createFrom().item(Response.ok(e.getMessage()).status(500).build());
		}


		// Add series to the cache
		// This is non-blocking operation
		var cacheFuture = pacsCache.addNewEntry(drimboxSourceURL, getAccessToken(), studyUID, seriesUID, sopInstanceUID);

		CompletableFuture<Response> completableFuture = new CompletableFuture<>();
		String patientINS = kos.getPatientINS();
		DicomInputStream dis = new DicomInputStream(new ByteArrayInputStream(kos.getRawData()));
		Attributes attributes = dis.readDataset();

		JsonObjectBuilder root = Json.createObjectBuilder();
		JsonArrayBuilder studiesArray = Json.createArrayBuilder();
		JsonArrayBuilder seriesArray = Json.createArrayBuilder();
		JsonArrayBuilder instancesArray = Json.createArrayBuilder();

		JsonObjectBuilder seriesObject = Json.createObjectBuilder();
		seriesObject.add("SeriesInstanceUID", seriesUID);

		String textValue = "";
		for (Attributes sequence : attributes.getSequence(Tag.ContentSequence)) {
			if(sequence.getString(Tag.ValueType).equals("TEXT")) {
				textValue = sequence.getString(Tag.TextValue);
			}
		}
		String modal = textValue.split("Série-")[1].split(" : ")[1].split(" ")[0];

		for(Attributes currentSequence : attributes.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence).get(0).getSequence(Tag.ReferencedSeriesSequence)
				.get(0).getSequence(Tag.ReferencedSOPSequence)) {

			JsonObjectBuilder metadata = Json.createObjectBuilder();
			metadata.add("SOPInstanceUID", currentSequence.getString(Tag.ReferencedSOPInstanceUID));

			metadata.add("SeriesInstanceUID", seriesUID);
			metadata.add("StudyInstanceUID", attributes.getString(Tag.StudyInstanceUID));

			metadata.add("SOPClassUID", currentSequence.getString(Tag.ReferencedSOPClassUID));
			metadata.add("Modality", modal);
			metadata.add("INS", patientINS);

			metadata.add("InstanceNumber", Integer.valueOf(currentSequence.getString(Tag.InstanceNumber)));

			// Add to the instance list
			instancesArray.add(Json.createObjectBuilder()
					.add("metadata", metadata)
					.add("url", "dicomweb:" + this.consoHost  +"/api/conso/" + DICOM_FILE_PREFIX + "/" + studyUID + "/" + seriesUID + "/" + currentSequence.getString(Tag.ReferencedSOPInstanceUID) )
					);
		}

		seriesObject.add("instances", instancesArray);
		seriesObject.add("Modality", "CT");
		seriesArray.add(seriesObject);

		JsonObjectBuilder study = Json.createObjectBuilder();
		study.add("StudyInstanceUID", studyUID);
		study.add("series", seriesArray);
		study.add("NumInstances", seriesInfo.instancesUID.size());

		study.add("StudyDate",  attributes.getString(Tag.StudyDate));
		study.add("StudyTime", attributes.getString(Tag.StudyTime));
		study.add("PatientName", attributes.getString(Tag.PatientName));
		study.add("PatientID", attributes.getString(Tag.PatientID));
		study.add("AccessionNumber", attributes.getSequence(Tag.ReferencedRequestSequence).get(0).getString(Tag.AccessionNumber));
		study.add("PatientAge", attributes.getString(Tag.PatientAge, ""));
		study.add("PatientSex", attributes.getString(Tag.PatientSex));
		study.add("StudyDescription", attributes.getString(Tag.StudyDescription));
		study.add("Modalities", modal);
		study.add("fullmetadataset", "no");

		studiesArray.add(study);

		root.add("studies", studiesArray);
		String ohifMetadata = root.build().toString();
		completableFuture.complete(Response.ok(ohifMetadata).build());

		dis.close();

		cacheFuture.onComplete(
				entryAdded -> {
					// We need to fire the event on the event bus ourselves since the data is already in the cache
					if (entryAdded.succeeded() && entryAdded.result() == 0) {
						vertx.eventBus().publish(PacsCacheConso.getEventBusID(studyUID, seriesUID),
								// We take the first instance ID of the series
								pacsCache.getFirstInstanceNumber(studyUID, seriesUID)
								// NOTE : we do not do something like this : seriesInfo.instancesUID.get(0) in case of missing images
								);
						// This should not happen, but it is here in a fail-case scenario
					} else if (entryAdded.succeeded() && !completableFuture.isDone()) {
						completableFuture.complete(Response.ok("Cache was created but JSON data is empty").status(500).build());
						// If some images were not found on the pacs we mark them as not existing
					} else if (entryAdded.succeeded() && entryAdded.result() != seriesInfo.instancesUID.size()) {
						Log.warn("Some images seems to be missing in the pacs");
						pacsCache.markInstanceAsNotFound(studyUID, seriesUID, seriesInfo.instancesUID);
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

	/**
	 *  Return image with the UIDs specified 
	 */
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
						if (item.length == 0) {
							Log.info("[dicomfile] Not found : " + instanceUID);
							return RestResponse.ResponseBuilder.ok(item).header("Accept-Ranges", "bytes").status(410).build();
						}
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

	/**
	 *  Post a DICOM KOS (in case of a manual import, when DMP is offline for instance) 
	 */
	@POST
	@Path("/importKOS")
	@Produces(MediaType.TEXT_XML)
	public void addKos(byte[] requestBody)  {
		Log.info(requestBody);
		KOSFile kos = new KOSFile(requestBody);
		Log.info("Import kos");
		Log.info("kos.studyInstanceUID : " + kos.getStudyUID());
		DmpAPI.setKOS(kos);
	}
}

