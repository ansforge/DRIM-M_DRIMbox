/*
 *  DocumentEntry.java - DRIMBox
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

package com.bcom.drimbox.dmp.xades;


import com.bcom.drimbox.dmp.xades.file.CDAFile;
import com.bcom.drimbox.dmp.xades.file.KOSFile;
import com.bcom.drimbox.dmp.xades.utils.XadesType;
import com.bcom.drimbox.dmp.xades.utils.XadesType.ClassificationCode;

import io.quarkus.logging.Log;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomEncodingOptions;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.util.SafeClose;

import static com.bcom.drimbox.dmp.xades.utils.XadesUUID.*;

public class DocumentEntry extends BaseElement {

	public enum FileType {
		KOS,
		CDA,
		SubmissionType
	}

	XadesType.SourcePatientInfo sourcePatientInfo;
	String submissionTime;
	String creationTime;
	String languageCode;
	String legalAuthenticator;
	String serviceStartTime;
	String serviceStopTime;
	String sourcePatientID;

	XadesType.ClassificationCode classCode;
	XadesType.ClassificationCode contentType;
	XadesType.ClassificationCode format;
	XadesType.ClassificationCode practiceSetting;
	XadesType.ClassificationCode healthcareFacilityType;

	List<XadesType.ClassificationCode> eventCodes = new ArrayList<>();
	List<XadesType.ClassificationCode> confidentialities = new ArrayList<>();
	List<XadesType.Author> authors = new ArrayList<>();
	String mimeType;

	String studyID;
	String accessionNumberRoot;
	String accessionNumberExtension;
	String orderRoot;
	String orderExtension;
	String size;
	String hash;

	public FileType getFileType() {
		return fileType;
	}

	FileType fileType;

	public DocumentEntry(SubmissionSet submissionSet, CDAFile cdaFile, String uniqueID, String hash, Integer size) {
		super();

		parseCDA(cdaFile);

		fileType = FileType.SubmissionType;

		sourcePatientID = patientID;
		sourcePatientInfo = new XadesType.SourcePatientInfo("", "","" );
		entryID = "Signature01";
		this.uniqueID = uniqueID;
		mimeType = "text/xml";
		this.hash =  hash;
		this.size = String.valueOf(size + 1);
		legalAuthenticator = submissionSet.authors.get(0).authorPerson;
		String currentTime = getCurrentTime();
		submissionTime = currentTime;
		serviceStartTime = currentTime;
		serviceStopTime = currentTime;
		languageCode = "art";
		classCode = new XadesType.ClassificationCode( "urn:oid:1.3.6.1.4.1.19376.1.2.1.1.1", "Digital Signature", "URN"  );
		contentType = new XadesType.ClassificationCode("E1762", "Full Document", "ASTM");
		format = new XadesType.ClassificationCode( "http://www.w3.org/2000/09/xmldsig#", "Default Signature Style", "URN" );

		final String eventCodeDisplayName = "Source";
		eventCodes.clear();
		eventCodes.add( new XadesType.ClassificationCode("1.2.840.10065.1.12.1.14", eventCodeDisplayName, "1.2.840.10065.1.12"));
		title = eventCodeDisplayName;

		// Seems that it is not needed (we already get it from the CDA)
		confidentialities.clear();
		confidentialities.add(new XadesType.ClassificationCode("N", "Normal", "2.16.840.1.113883.5.25" ));
		confidentialities.add(new XadesType.ClassificationCode("MASQUE_PS", "Masqué aux professionnels de santé", "1.2.250.1.213.1.1.4.13" ));
		confidentialities.add(new XadesType.ClassificationCode("INVISIBLE_PATIENT", "Non visible par le patient", "1.2.250.1.213.1.1.4.13" ));

		createDocumentEntry();
	}

	private void parseCDA(CDAFile cdaFile) {
		confidentialities.add(cdaFile.getConfidentiality());
		Log.info(cdaFile.getConfidentiality().code);
		if(cdaFile.getConfidentiality().code.equals("INVISIBLE_PATIENT")) {
			confidentialities.add(new XadesType.ClassificationCode("N", "Normal", "2.16.840.1.113883.5.25" ));
		}
		patientID = cdaFile.getPatientID();
		accessionNumberRoot = cdaFile.getAccessionNumberRoot();
		accessionNumberExtension = cdaFile.getAccessionNumberExtension();
		authors.add(new XadesType.Author(cdaFile.getAuthorInstitution(), cdaFile.getAuthorPerson(), "" ,"" ));
		legalAuthenticator =  cdaFile.getLegalAuthenticator();
		orderRoot = cdaFile.getOrderRoot();
		orderExtension = cdaFile.getOrderExtension();
		serviceStartTime = cdaFile.getServiceStartTime();
		serviceStopTime = cdaFile.getServiceStopTime();
		sourcePatientID = cdaFile.getSourcePatientID();
		eventCodes.add(cdaFile.getEventCode());
		healthcareFacilityType = cdaFile.getHealthcareFacilityType();
		practiceSetting = cdaFile.getPracticeSetting();
		studyID = cdaFile.getStudyID();
		sourcePatientInfo = cdaFile.getSourcePatientInfo();
	}

	public DocumentEntry(CDAFile cdaFile, FileType fileType, KOSFile kos) throws IOException {
		super();

		parseCDA(cdaFile);

		languageCode = "fr-FR";
		submissionTime = getCurrentTime();

		this.fileType = fileType;
		String sopInstanceUID = "";
		String textValue = "";
		Attributes dataset;
		DicomOutputStream dos = null;
		InputStream targetStream = new ByteArrayInputStream(kos.getRawData());
		try (DicomInputStream dis = new DicomInputStream(targetStream)) {

			dis.setIncludeBulkData(IncludeBulkData.URI);
			dataset = dis.readDataset();
			for (Attributes sequence : dataset.getSequence(Tag.ContentSequence)) {
				if(sequence.getString(Tag.ValueType).equals("TEXT")) {
					textValue = sequence.getString(Tag.TextValue);
				}
			}

			sopInstanceUID = dataset.getString(Tag.SOPInstanceUID);
			creationTime = dataset.getString(Tag.InstanceCreationDate) + dataset.getString(Tag.InstanceCreationTime);
		} finally {
			SafeClose.close(dos);
		}

		for (int i=0; i < StringUtils.countMatches(textValue, "Série-"); i++) {
			String modal = textValue.split("Série-")[i+1].split(" : ")[1].split(" ")[0];
			Boolean present = false;
			for (ClassificationCode eventCode : eventCodes) {
				if(eventCode.code.equals(modal)) {
					present = true;
				}
			}
			if(!present) {
				String description = getModaliteValue(modal);
				eventCodes.add( new XadesType.ClassificationCode(modal, description, "1.2.250.1.213.1.1.5.618") );
			}
		}     

		switch(fileType) {
		case KOS:
			hash =  DigestUtils.sha1Hex(kos.getRawData());
			size = String.valueOf(kos.getRawData().length);
			mimeType = "application/dicom";
			entryID = "DocumentKOS";
			uniqueID =  sopInstanceUID;
			comments = "KOS";
			title = "Reference d'Objets d'un Examen d'Imagerie";
			contentType = new XadesType.ClassificationCode("IMG-KOS", "Reference d'objets d'un examen d'imagerie", "1.2.250.1.213.1.1.4.12");
			classCode = new XadesType.ClassificationCode("31", "Imagerie médicale", "1.2.250.1.213.1.1.4.1");
			format = new XadesType.ClassificationCode("1.2.840.10008.5.1.4.1.1.88.59", "Document de Références d'objets d'imagerie selon profil IHE RAD XDS-I", "1.2.840.10008.2.6.1");

			break;
		case CDA:
			mimeType = "text/xml";
			entryID = "DocumentCDA";
			uniqueID =  "1.2.250.1.999.1.1.8121." + getRandomInt(20,29) +  "." + getRandomInt(0, 10000);

			comments = "pdf";
			title = "CDA";

			contentType = new XadesType.ClassificationCode("18748-4", "CR d'imagerie médicale", "2.16.840.1.113883.6.1");
			classCode = new XadesType.ClassificationCode("10", "Compte rendu", "1.2.250.1.213.1.1.4.1");
			format = new XadesType.ClassificationCode("urn:ihe:iti:xds-sd:pdf:2008", "Document à corps non structuré en Pdf/A-1", "1.3.6.1.4.1.19376.1.2.3");

			break;
		default:
			throw new IllegalStateException("Unexpected value: " + fileType);
		}



		createDocumentEntry();
	}


	private void createDocumentEntry() {
		var extrinsicObject = xmlDocument.createElement("ExtrinsicObject");
		extrinsicObject.setAttribute("id", entryID);
		extrinsicObject.setAttribute("mimeType", mimeType);
		extrinsicObject.setAttribute("objectType", XDSDocumentEntry);

		xmlDocument.appendChild(extrinsicObject);

		// creationTime
		// TODO : check value. Works with submissionTime for now
		if(creationTime == null) {
			creationTime = getCurrentTime();
		}
		extrinsicObject.appendChild(createSlotField("creationTime", creationTime) );

		// Current time
		extrinsicObject.appendChild(createSlotField("submissionTime", submissionTime) );

		// Language code
		extrinsicObject.appendChild(createSlotField("languageCode", languageCode) );
		// legalAuthenticator
		extrinsicObject.appendChild(createSlotField("legalAuthenticator", legalAuthenticator) );
		// serviceStartTime
		extrinsicObject.appendChild(createSlotField("serviceStartTime", serviceStartTime) );
		// serviceStopTime
		extrinsicObject.appendChild(createSlotField("serviceStopTime", serviceStopTime) );
		// sourcePatientId
		extrinsicObject.appendChild(createSlotField("sourcePatientId", sourcePatientID) );

		extrinsicObject.appendChild(createSlotField("hash", hash));
		extrinsicObject.appendChild(createSlotField("size", size));


		// Patient info
		if (!sourcePatientInfo.isEmpty()) {
			List<String> patientInfo = new ArrayList<>();

			patientInfo.add("PID-5|" + sourcePatientInfo.PID5);
			patientInfo.add("PID-7|" + sourcePatientInfo.PID7);
			patientInfo.add("PID-8|" + sourcePatientInfo.PID8);

			extrinsicObject.appendChild(createSlotField("sourcePatientInfo", patientInfo));
		}

		// Title
		extrinsicObject.appendChild(createTitle());
		// Comments
		extrinsicObject.appendChild(createComments());

		// Author
		for(XadesType.Author author : authors) {
			extrinsicObject.appendChild(createAuthorField(XDSDocumentEntry_author, author));
		}


		// Class Code
		extrinsicObject.appendChild(createSchemeClassificationField(XDSDocumentEntry_classCode, classCode));

		// Content type
		extrinsicObject.appendChild(createSchemeClassificationField(XDSDocumentEntry_typeCode, contentType));

		// Confidentiality
		for (XadesType.ClassificationCode confidentiality : confidentialities) {
			extrinsicObject.appendChild(createSchemeClassificationField(XDSDocumentEntry_confidentialityCode, confidentiality));
		}


		// format
		extrinsicObject.appendChild(createSchemeClassificationField(XDSDocumentEntry_formatCode, format));
		// healthcareFacilityType
		extrinsicObject.appendChild(createSchemeClassificationField(XDSDocumentEntry_healthCareFacilityTypeCode, healthcareFacilityType));
		// practiceSetting
		extrinsicObject.appendChild(createSchemeClassificationField(XDSDocumentEntry_practiceSettingCode, practiceSetting));

		// Code list
		for (XadesType.ClassificationCode eventCode : eventCodes) {
			extrinsicObject.appendChild(createSchemeClassificationField(XDSDocumentEntry_eventCodeList, eventCode));
		}

		// Reference ID
		List<String> referenceID = new ArrayList<>();
		referenceID.add(accessionNumberExtension + "^^^&" + accessionNumberRoot + "&ISO^urn:ihe:iti:xds:2013:accession");
		referenceID.add(studyID + "^^^&1.2.3.4.5.6&ISO^urn:ihe:iti:xds:2016:studyInstanceUID");
		referenceID.add(orderExtension + "^^^&" + orderRoot + "&ISO^urn:ihe:iti:xds:2013:order");
		extrinsicObject.appendChild(createSlotField("urn:ihe:iti:xds:2013:referenceIdList", referenceID));

		// External identifier
		extrinsicObject.appendChild(createExternalIdentifierField(XDSDocumentEntry_patientId, patientID, "XDSDocumentEntry.patientId"));
		extrinsicObject.appendChild(createExternalIdentifierField(XDSDocumentEntry_uniqueId, uniqueID, "XDSDocumentEntry.uniqueId"));
	}



	private String getModaliteValue(String type) {
		InputStream is = getFileFromResourceAsStream("CISIS/ValueSet_AcquisitionModality.json");
		JsonReader rdr = Json.createReader(is);
		JsonObject json = rdr.readObject();
		JsonArray jArr = json.getJsonObject("compose").getJsonArray("include").getJsonObject(0).getJsonArray("concept");
		//JsonArray jArr = json.getJsonArray("compose").getJsonObject(0).getJsonArray("include").getJsonObject(0).getJsonArray("concept");
		for (int i=0; i < jArr.size(); i++) {
			if(jArr.getJsonObject(i).getString("code").equals(type))
				return jArr.getJsonObject(i).getString("display");
		}

		return "error";
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
