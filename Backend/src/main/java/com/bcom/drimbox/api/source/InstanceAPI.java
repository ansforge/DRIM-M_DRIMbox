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

package com.bcom.drimbox.api.source;

import static com.bcom.drimbox.utils.PrefixConstants.SERIES_PREFIX;
import static com.bcom.drimbox.utils.PrefixConstants.STUDIES_PREFIX;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestMulti;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import com.bcom.drimbox.database.DatabaseManager;
import com.bcom.drimbox.database.SourceEntity;
import com.bcom.drimbox.document.KOSFile;
import com.bcom.drimbox.pacs.PacsCacheSource;
import com.bcom.drimbox.psc.ProSanteConnect;
import com.bcom.drimbox.utils.RequestHelper;
import com.bcom.drimbox.utils.exceptions.WadoErrorException;

import io.quarkus.logging.Log;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;

/**
 * API to manage instances related requests
 * 
 */
@Path("/api/source")
public class InstanceAPI {
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

	@Inject
	PacsCacheSource pacsCacheSource;

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
	public Multi<byte[]> wadoRS(String studyUID, String seriesUID, @Context HttpHeaders headers) {

		final String ACCEPTED_FORMAT_SAMPLE = "Accepted format : transfer-syntax=1.2.840.10008.1.2.4.50;q=0.9;boundary=myBoundary, transfer-syntax=1.2.840.10008.1.2.4.50;q=0.5;boundary=myBoundary";

		if (!checkAuthorisation()) {
			return createError("Authentication failure", 401);
		}

		String url = getWadoUrl() + "/" + STUDIES_PREFIX + "/" + studyUID + "/" + SERIES_PREFIX + "/" + seriesUID;
		Log.info("WADO : " + url);
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
		Log.info(contentType);
		return RestMulti.fromMultiData(requestHelper.fileRequestCMove(url, acceptedTransferSyntax, preferredTransferSyntax, boundary, "conso"))
				.header("Content-Type", contentType)
				.build();
	}


	@OPTIONS
	@Produces("application/vnd.sun.wadl+xml")
	@Path("/studies")
	public Response getOptions() {
		InputStream is = getFileFromResourceAsStream("wadl/wadlconfig.wadl"); 
		return Response.ok(is).build();
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


	@Produces(MediaType.APPLICATION_JSON)
	@GET
	@Path("test")
	public Response demoRedirect(@Context UriInfo uriInfo) throws IOException {
		String studyUID = uriInfo.getQueryParameters().getFirst("studyUID");

		return Response //seeOther = 303 redirect
				.seeOther(UriBuilder.fromUri("https://vi1.test1.mesimagesmedicales.fr/viewer/dicomjson?url=https://vi1.test1.mesimagesmedicales.fr/api-vi1-source/metadata/" + studyUID)
						.build())//build the URL where you want to redirect
				.build();
	}

	/**
	 * API to handle call to open DB source viewer. 
	 * It generates json after a wado-rs metadata request to the pacs
	 * @param studyUID
	 * @param uriInfo
	 * @param idCDA
	 * @return
	 * @throws IOException 
	 */
	@Produces(MediaType.APPLICATION_JSON)
	@GET
	@Path("metadata/{studyUID}")
	public Response getSourceMetadata(String studyUID, @Context UriInfo uriInfo) throws IOException {

		SourceEntity entity = this.databaseManager.getEntity(studyUID);
		if(entity == null) {
			Log.info("No data found in database");
		}

		KOSFile kos = new KOSFile(entity.rawKOS);
		Log.info("\n" + kos.getSeriesInfo().size() + "\n");

		Log.info("Available series : ");
		Log.info(kos.getSeriesInfo().keySet());

		DicomInputStream dis = new DicomInputStream(new ByteArrayInputStream(kos.getRawData()));
		Attributes attributes = dis.readDataset();

		JsonObjectBuilder root = Json.createObjectBuilder();

		String patientINS = kos.getPatientINS();


		JsonArrayBuilder studiesArray = Json.createArrayBuilder();
		JsonArrayBuilder seriesArray = Json.createArrayBuilder();
		JsonArrayBuilder instancesArray = Json.createArrayBuilder();



		String textValue = "";
		for (Attributes sequence : attributes.getSequence(Tag.ContentSequence)) {
			if(sequence.getString(Tag.ValueType).equals("TEXT")) {
				textValue = sequence.getString(Tag.TextValue);
			}
		}
		String modal = textValue.split("Série-")[1].split(" : ")[1].split(" ")[0];



		for(String seriesUID : kos.getSeriesInfo().keySet()) {

			pacsCacheSource.addNewEntry(studyUID, seriesUID);
			String url = getWadoUrl() + "/" + STUDIES_PREFIX + "/" + studyUID + "/" + SERIES_PREFIX + "/" + seriesUID;
			requestHelper.fileRequestCMove(url, Arrays.asList(UID.JPEGBaseline8Bit, "0.9",
					UID.JPEGExtended12Bit, "0.8",
					UID.JPEG2000, "0.8",
					UID.JPEGLosslessSV1, "0.7",
					UID.JPEGLSLossless, "0.6",
					UID.ExplicitVRLittleEndian, "0.5",
					UID.MPEG2MPML, "0.4",
					UID.MPEG2MPHL, "0.3",
					UID.MPEG4HP41, "0.3",
					UID.MPEG4HP41BD, "0.3"),  
					Arrays.asList(UID.JPEGBaseline8Bit, "0.9",
							UID.JPEGExtended12Bit, "0.8",
							UID.JPEG2000, "0.8",
							UID.JPEGLosslessSV1, "0.7",
							UID.JPEGLSLossless, "0.6",
							UID.ExplicitVRLittleEndian, "0.5",
							UID.MPEG2MPML, "0.4",
							UID.MPEG2MPHL, "0.3",
							UID.MPEG4HP41, "0.3",
							UID.MPEG4HP41BD, "0.3"), "myBoundary", "source");

			for(Attributes currentSequence : attributes.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence).get(0).getSequence(Tag.ReferencedSeriesSequence)
					.get(0).getSequence(Tag.ReferencedSOPSequence)) {

				JsonObjectBuilder metadata = Json.createObjectBuilder();
				metadata.add("SOPInstanceUID", currentSequence.getString(Tag.ReferencedSOPInstanceUID));

				metadata.add("SeriesInstanceUID", seriesUID);
				metadata.add("StudyInstanceUID", attributes.getString(Tag.StudyInstanceUID));

				metadata.add("SOPClassUID", currentSequence.getString(Tag.ReferencedSOPClassUID));
				metadata.add("Modality", modal);
				metadata.add("INS", patientINS);

				metadata.add("InstanceNumber", currentSequence.getString(Tag.InstanceNumber));

				// Add to the instance list
				instancesArray.add(Json.createObjectBuilder()
						.add("metadata", metadata)
						.add("url", "dicomweb:" + this.sourceHost  +"/api-vi1-source/" + DICOM_FILE_PREFIX + "/" + studyUID + "/" + seriesUID + "/" + currentSequence.getString(Tag.ReferencedSOPInstanceUID) )
						);
			}

			JsonObjectBuilder seriesObject = Json.createObjectBuilder();
			seriesObject.add("SeriesInstanceUID", seriesUID);
			seriesObject.add("instances", instancesArray);
			seriesObject.add("Modality", "CT");
			seriesArray.add(seriesObject);
		}
		JsonObjectBuilder study = Json.createObjectBuilder();
		study.add("StudyInstanceUID", studyUID);
		study.add("series", seriesArray);
		study.add("NumInstances", "5");

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
		dis.close();

		return Response.ok(root.build().toString()).build();
	}


	@GET
	@Path("check")
	public Response checkParameters(@QueryParam("idCDA") String idCDA, @QueryParam("accessionNumber") String accessionNumber, @QueryParam("requestType") String requestType
			, @QueryParam("studyInstanceUID") String studyInstanceUID) {

		SourceEntity entity = this.databaseManager.getEntity(studyInstanceUID);
		if(entity == null) {
			Log.info("No data found in database");
			return Response.status(406, "no study found in bdd").build();
		}

		if(!entity.cdaID.split("/")[0].equals(idCDA.split("_")[0])) {
			Log.info("incorrect value between idcda in request : " + idCDA.split("_")[0] + " and idcda in bdd : " + entity.cdaID.split("/")[0]);
			return Response.status(406, String.format("incorrect idcda : %s and %s", idCDA.split("_")[0], entity.cdaID)).build();
		}

		return Response.ok().build();
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
	public Uni<RestResponse<byte[]>> getDicomFile(String studyUID, String seriesUID, String instanceUID) {

		try {
			Future<byte[]> future = pacsCacheSource.getDicomFile(studyUID, seriesUID, instanceUID);
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


	private boolean checkAuthorisation()  {
		return noAuth || (!bearerToken.isEmpty() && proSanteConnect.introspectToken(bearerToken));
	}


	// Get wado url (e.g. http://localhost:8080/dcm4chee-arc/aets/AS_RECEIVED/rs)
	private String getWadoUrl() {
		return pacsUrl + "/" + wadoSuffix;
	}



	/**
	 * Get file from the resource folder
	 * @param fileName File to load
	 * @return Opened file
	 */
	private InputStream getFileFromResourceAsStream(String fileName) {

		// The class loader that loaded the class
		ClassLoader classLoader = getClass().getClassLoader();
		InputStream inputStream = classLoader.getResourceAsStream(fileName);

		// the stream holding the file content
		if (inputStream == null) {
			throw new IllegalArgumentException("file not found! " + fileName);
		} else {
			return inputStream;
		}
	}
}
