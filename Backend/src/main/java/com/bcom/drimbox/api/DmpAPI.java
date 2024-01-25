
/*
 *  DmpAPI.java - DRIMBox
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.dcm4che3.mime.MultipartParser;

import com.bcom.drimbox.dmp.DMPConnect;
import com.bcom.drimbox.dmp.DMPConnect.DMPResponseBytes;
import com.bcom.drimbox.dmp.auth.WebTokenAuth;
import com.bcom.drimbox.dmp.database.DatabaseManager;
import com.bcom.drimbox.dmp.database.SourceEntity;
import com.bcom.drimbox.dmp.request.BaseRequest;
import com.bcom.drimbox.dmp.request.FindAllDocumentRequest;
import com.bcom.drimbox.dmp.request.FindEntryUUIDDocumentRequest;
import com.bcom.drimbox.dmp.request.FindFilterDocumentRequest;
import com.bcom.drimbox.dmp.request.GiveAuthorizationRequest;
import com.bcom.drimbox.dmp.request.ParameterList;
import com.bcom.drimbox.dmp.request.RetrieveDocumentRequest;
import com.bcom.drimbox.dmp.request.VerifyAuthorizationRequest;
import com.bcom.drimbox.dmp.xades.file.CDAFile;
import com.bcom.drimbox.dmp.xades.file.KOSFile;
import com.bcom.drimbox.dmp.xades.request.BaseXadesRequest;
import com.bcom.drimbox.dmp.xades.request.DeleteRequest;
import com.bcom.drimbox.dmp.xades.request.ProvideAndRegisterRequest;
import com.bcom.drimbox.dmp.xades.request.UpdateRequest;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

// Todo : find a prefix for those request, /dmp maybe ?
@Path("/api/conso/dmp")
public class DmpAPI {

	@Inject
	DMPConnect dmpConnect;

	@Inject
	WebTokenAuth webTokenAuth;

	@Inject
	ParameterList parameterList;

	@Inject
	DatabaseManager databaseManager;

	protected static final String FIELD_ACTIVITIES = "activities";

	private enum returnType {
		STRING, RAWBYTES
	}

	@GET
	@Path("/grant/{ins}")
	@Produces(MediaType.TEXT_XML)
	public Response auth(String ins, @CookieParam("SessionToken") Cookie cookieSession)  {
		GiveAuthorizationRequest request = new GiveAuthorizationRequest();
		return dmpRequest(request, ins, cookieSession, returnType.STRING);
	}


	@GET
	@Path("/query/{ins}")
	@Produces(MediaType.TEXT_XML)
	public Response query(String ins, @CookieParam("SessionToken") Cookie cookieSession, @QueryParam("modality") List<String> modalities,
			@QueryParam("region") List<String> regions, @QueryParam("start") String start, @QueryParam("stop") String stop, @QueryParam("accessionNumber") String accessionNumber)  {

		if(!modalities.isEmpty() || !regions.isEmpty() || start != null || stop != null || accessionNumber != null) {
			FindFilterDocumentRequest request = new FindFilterDocumentRequest(ins, modalities, regions, start, stop, accessionNumber);
			return dmpRequest(request, ins, cookieSession, returnType.STRING);

		}
		else {
			FindAllDocumentRequest request = new FindAllDocumentRequest(ins);
			return dmpRequest(request, ins, cookieSession, returnType.STRING);
		}
	}

	@Produces(MediaType.TEXT_XML)
	public Response queryUUID(String ins, String uniqueID, byte[] rawMetadata)  {
		FindEntryUUIDDocumentRequest request = new FindEntryUUIDDocumentRequest(uniqueID);
		return dmpRequest(request, ins, rawMetadata, returnType.STRING);
	}

	private static final Map<String, KOSFile> kosReceived = new HashMap<>();

	public static KOSFile getKOS(String studyUID) {
		return kosReceived.get(studyUID);
	}

	public static void setKOS(KOSFile kos) {
		kosReceived.put(kos.getStudyUID(), kos);
	}

	private String getBoundary(String contentType) {
		String[] respContentTypeParams = contentType.split(";");
		for (String respContentTypeParam : respContentTypeParams)
			if (respContentTypeParam.replace(" ", "").startsWith("boundary="))
				return respContentTypeParam
						.substring(respContentTypeParam.indexOf("=") + 1)
						.replaceAll("\"", "");

		return null;
	}

	@GET
	@Path("/retrieve/{ins}")
	@Produces(MediaType.TEXT_XML)
	public Response retrieve(String ins, @QueryParam("repositoryId") String repositoryId, @QueryParam("uniqueId") String uniqueId, @CookieParam("SessionToken") Cookie cookieSession)  {
		RetrieveDocumentRequest request = new RetrieveDocumentRequest(repositoryId, uniqueId);

		Response r = dmpRequest(request, ins, cookieSession, returnType.RAWBYTES);
		var contentTypeObject = r.getHeaders().get("Content-Type");
		if (contentTypeObject == null) {
			Log.error("Request don't have a content type can't build retrieve KOS");
		} else {
			String contentType = (String)contentTypeObject.get(0);
			String boundary = getBoundary(contentType);
			if (boundary == null) {
				Log.error("Invalid boundary in contentType. Can't unpack KOS.");
			}
			try {
				new MultipartParser(boundary).parse(new ByteArrayInputStream((byte[]) r.getEntity()), (partNumber, multipartInputStream) -> {
					// We need to call this to get read of the header params to ensure we only have the KOS left in multipartInputStream
					Map<String, List<String>> headerParams = multipartInputStream.readHeaderParams();
					// We look for the KOS
					if (headerParams.get("content-type").get(0).equals("application/octet-stream")) {
						KOSFile kos = new KOSFile(multipartInputStream.readAllBytes());
						kosReceived.put(kos.getStudyUID(), kos);
					}
				});

			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		return r;
	}

	@GET
	@Path("/verify/{ins}")
	@Produces(MediaType.TEXT_XML)
	public Response verify(String ins, @CookieParam("SessionToken") Cookie cookieSession, @QueryParam("uuid") String uuid)  {
		VerifyAuthorizationRequest request = new VerifyAuthorizationRequest(ins);
		if(Objects.equals(webTokenAuth.getSecteurActivite(cookieSession.getValue()), "empty") && !Objects.equals(this.parameterList.getSituation(uuid), "empty")) {
			JsonObject exercices = webTokenAuth.getUserInfo(cookieSession.getValue()).getJsonObject("SubjectRefPro").getJsonArray("exercices").getJsonObject(0);
			JsonObject activites = null;
			for (int i=0; i < exercices.getJsonArray(FIELD_ACTIVITIES).size(); i++) {
				if(exercices.getJsonArray(FIELD_ACTIVITIES).getJsonObject(i).getString("ancienIdentifiantDeLaStructure").equals(this.parameterList.getSituation(uuid)))
					activites = exercices.getJsonArray(FIELD_ACTIVITIES).getJsonObject(i);
			}
			if (activites != null) {
				webTokenAuth.setSecteurActivite(cookieSession.getValue(), activites.getString("raisonSocialeSite"));
			}
		}
		return dmpRequest(request, ins, cookieSession, returnType.STRING);
	}


	public boolean storeCDA(CDAFile c) {
		//KOSFile k = new KOSFile(new File(getClass().getClassLoader().getResource("kos.dcm").getPath()));
		try {
			KOSFile k = new KOSFile(c);
			ProvideAndRegisterRequest request = new ProvideAndRegisterRequest(c, k);
			String requestValid = request.getRequest().split("<0.1>\n")[1].split("--")[0];
			if ( ! databaseManager.addEntity(c, k, requestValid.getBytes(), request.getSignDoc().getBytes())) {
				Log.error("Can't add KOS to BDD. Study UID : " + c.getStudyID());
			}

			Response response = dmpRequest(request);
			if(response.getStatus() == 200) {
				return true;
			}
			else return false;

		} catch (Exception e) {
			Log.error("Error while storing CDA : " + e);
			e.printStackTrace();
		}
		return false;
	}

	@Transactional
	public void updateKOS(String studyInstanceUID) throws IOException {
		// Check if studyUID exists in bdd
		SourceEntity entity = this.databaseManager.getEntity(studyInstanceUID);
		if(entity == null) {
			Log.info("No data found in database");
		}
		else {
			Log.info("data found in database");
		}
		KOSFile kos = new KOSFile(entity.rawKOS);
		KOSFile newKos = new KOSFile(kos);
		// Retrieve EntryUID with UniqueID from kos
		Response uuidResponse = queryUUID(kos.getPatientINS(), kos.getSopInstanceUID(), entity.rawMetadata);
		// Check if kos with this uniqueID exists in the dmp
		if(!uuidResponse.getEntity().toString().split("totalResultCount=\"")[1].split("\"")[0].equals("0")) {
			String entryUUID = uuidResponse.getEntity().toString().split("ObjectRef id=\"")[1].split("\"")[0];
			Log.info(entryUUID);
			String cdaString = new String(entity.rawCDA, StandardCharsets.UTF_8);
			CDAFile cda = new CDAFile(cdaString);
			try {
				// If serie is updated
				if(newKos.getExist()) {

					UpdateRequest updateRequest = new UpdateRequest(cda, newKos, entryUUID);

					String requestValid = updateRequest.getRequest().split("<0.1>\n")[1].split("--")[0];
					SourceEntity oldEntity = databaseManager.getEntity(studyInstanceUID);
					cda.setOruIpp(oldEntity.ipp);
					// Update the database
					databaseManager.deleteEntity(oldEntity);
					if ( ! databaseManager.addEntity(cda, newKos, requestValid.getBytes(), updateRequest.getSignDoc().getBytes())) {
						Log.error("Can't add KOS to BDD. Study UID : " + cda.getStudyID());
					}

					Response response = dmpRequest(updateRequest);
					Log.info(response.getStatus());
					Log.info(response.getEntity().toString());
				}
				// If serie is deleted
				else {
					DeleteRequest deleteRequest = new DeleteRequest(cda, newKos, entryUUID);
					Response response = dmpRequest(deleteRequest, kos.getPatientINS(), entity.rawMetadata, returnType.STRING);
					Log.info(response.getStatus());
					Log.info(response.getEntity().toString());
					// Delete the study in the database
					SourceEntity oldEntity = databaseManager.getEntity(studyInstanceUID);
					databaseManager.deleteEntity(oldEntity);
				}

			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			Log.info("after kos generate");
		}
		else {
			Log.info("No entryUUID found in the DMP");
		}
	}

	/**
	 * Make a request to the DMP
	 *
	 * @param request Request to make
	 * @param ins Patient INS
	 * @param cookieSession Cookie session gathered from the backend request. If null it will return a 401 error code.
	 * @return DMP response with code 200 if all is going well. 401 if there is a failure in auth, 500 if VIHF could not be created.
	 */
	private Response dmpRequest(BaseRequest request, String ins, Cookie cookieSession, returnType returnObject) {
		if(cookieSession != null) {
			String cookieID = cookieSession.getValue();

			if ( webTokenAuth.clientRegistered(cookieID)) {

				Boolean result = request.createVIHF(
						webTokenAuth.getUserInfo(cookieID),
						ins,
						webTokenAuth.getSecteurActivite(cookieID));

				if (!result)
					return Response.status(500).build();

				if (returnObject == returnType.STRING) {
					DMPConnect.DMPResponse response = dmpConnect.sendRequest(request);
					return Response.ok(response.message).build();
				}
				// To retrieve a file (cda or kos), we need to have it in byteArray format to not lose information
				if (returnObject == returnType.RAWBYTES) {
					DMPResponseBytes response = dmpConnect.sendKOSRequest(request);
					return Response.ok(response.rawMessage).header("Content-Type", response.contentType).build();
				}
			}
		}

		return Response.status(401).build();
	}

	/**
	 * Make a request to the DMP
	 *
	 * @param request Request to make
	 * @param ins Patient INS
	 * @param cookieSession Cookie session gathered from the backend request. If null it will return a 401 error code.
	 * @return DMP response with code 200 if all is going well. 401 if there is a failure in auth, 500 if VIHF could not be created.
	 */
	private Response dmpRequest(BaseRequest request, String ins, byte[] rawMetadata, returnType returnObject) {
		Boolean result = request.createVIHF(rawMetadata, ins);

		if (!result)
			return Response.status(500).build();
		//Log.info("\n\n" + request.getRequest() + "\n\n");
		if (returnObject == returnType.STRING) {
			DMPConnect.DMPResponse response = dmpConnect.sendRequest(request);
			return Response.ok(response.message).build();
		}
		// To retrieve a file (cda or kos), we need to have it in byteArray format to not lose information
		if (returnObject == returnType.RAWBYTES) {
			DMPResponseBytes response = dmpConnect.sendKOSRequest(request);
			return Response.ok(response.rawMessage).header("Content-Type", response.contentType).build();
		}

		return Response.status(401).build();
	}

	private Response dmpRequest(BaseXadesRequest request) {
		DMPConnect.DMPResponse response = dmpConnect.sendPostRequest(request);
		Log.info(response.message);
		Log.info(response.statusCode);
		return Response.ok(response.message).build();
	}
}
