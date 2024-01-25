/*
 *  KOSFile.java - DRIMBox
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

package com.bcom.drimbox.dmp.xades.file;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;

import com.bcom.drimbox.dmp.database.DatabaseManager;
import com.bcom.drimbox.dmp.database.SourceEntity;
import com.bcom.drimbox.pacs.CFindSCU;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;

public class KOSFile {
	byte[] fileContent;

	@Inject 
	CFindSCU cFindSCU;

	String studyUID;

	String patientINS;

	String sopInstanceUID;
	
	Boolean exist = false;

	public static class SeriesInfo {
		public String retrieveURL;
		public List<String> instancesUID = new ArrayList<>();
	}
	// StudyID => { SeriesID => RetrieveURL, SeriesID => RetrieveURL,... }
	Map<String, SeriesInfo> seriesURL = new HashMap<>();

	private static final String BASE_OID = "1.3.2.751468943.3";


	// This is used for testing purpose only for now.
	@Deprecated
	public KOSFile(File file) {
		try {
			fileContent = Files.readAllBytes(file.toPath());
			parseKOS(new DicomInputStream(file));
		} catch (IOException e) {
			throw new IllegalStateException("could not read file " + file, e);
		}
	}


	/**
	 * Creating kos from cda and c-find values
	 * @param c CDAFile from hl7 message
	 */
	public KOSFile(CDAFile c) throws IOException {
		String newPatientName = "Patient^Name";
		File file = new File("ko.dcm");
		file.createNewFile();
		Attributes fmi = new Attributes();
		Attributes attrs = new Attributes();
		//Let's modify the patient name tag
		attrs.setString(Tag.PatientName, VR.PN, newPatientName);
		attrs.setString(Tag.AccessionNumber, VR.SH, "");
		attrs.setString(Tag.Modality, VR.CS, "KO");
		attrs.setString(Tag.Manufacturer, VR.LO, "BCOM");
		attrs.setString(Tag.InstitutionName, VR.CS, "BCOM");
		attrs.setString(Tag.SeriesNumber, VR.IS, "59");
		attrs.setString(Tag.InstanceNumber, VR.IS, "1");
		attrs.setString(Tag.ValueType, VR.CS, "CONTAINER");
		attrs.setString(Tag.ContinuityOfContent, VR.CS, "SEPARATE");
		attrs.setString(Tag.TimezoneOffsetFromUTC, VR.SH, "+0200");
		attrs.setString(Tag.IssuerOfPatientID, VR.LO, "ASIP-SANTE-INS-NIR");
		attrs.setString(Tag.SpecificCharacterSet, VR.CS, "ISO_IR 100");
		attrs.setString(Tag.SOPClassUID, VR.UI, UID.KeyObjectSelectionDocumentStorage);

		LocalDate localDate = LocalDate.now();
		DateTimeFormatter fmt1 = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd  

		attrs.setString(Tag.InstanceCreationDate, VR.DA, localDate.format(fmt1));
		attrs.setString(Tag.SeriesDate, VR.DA, localDate.format(fmt1));
		attrs.setString(Tag.ContentDate, VR.DA, localDate.format(fmt1));

		LocalTime localTime = LocalTime.now();
		DateTimeFormatter fmt2 = DateTimeFormatter.ofPattern("HHmmss");  

		attrs.setString(Tag.InstanceCreationTime, VR.DA, localTime.format(fmt2));
		attrs.setString(Tag.SeriesTime, VR.DA, localTime.format(fmt2));
		attrs.setString(Tag.ContentTime, VR.DA, localTime.format(fmt2));

		attrs.setString(Tag.PatientName, VR.PN, c.getPatientName());
		attrs.setString(Tag.OtherPatientNames, VR.PN, c.getPatientName());
		attrs.setString(Tag.PatientID, VR.LO, c.getPatientID().concat("^").split("\\^")[0]);
		attrs.setString(Tag.PatientBirthDate, VR.DA, c.getPatientBirthDate());
		attrs.setString(Tag.PatientSex, VR.CS, c.getPatientSex());

		attrs.setString(Tag.StudyInstanceUID, VR.UI, c.getStudyID());
		String sopInstanceUID = this.generateUUID("sopInstance");
		String seriesInstanceUID = this.generateUUID("seriesInstance");
		Log.info("SopInstanceUID : " + sopInstanceUID);
		attrs.setString(Tag.SOPInstanceUID, VR.UI, sopInstanceUID);
		attrs.setString(Tag.SeriesInstanceUID, VR.UI, seriesInstanceUID);

		attrs.setString(Tag.PatientComments, VR.PN, "29758");

		Sequence issuerOfPatientIDQualifiersSequence = attrs.newSequence(Tag.IssuerOfPatientIDQualifiersSequence, 1);
		Attributes seqIssuer = new Attributes();
		seqIssuer.setString(Tag.UniversalEntityID, VR.UT, "1.2.250.1.213.1.4.10");
		seqIssuer.setString(Tag.UniversalEntityIDType, VR.CS, "ISO");
		issuerOfPatientIDQualifiersSequence.add(seqIssuer);

		Sequence otherPatientIDsSequence = attrs.newSequence(Tag.OtherPatientIDsSequence, 1);
		Attributes seqOthers = new Attributes();
		seqOthers.setString(Tag.PatientID, VR.LO, c.getPatientID().split("\\^")[0]);
		seqOthers.setString(Tag.TypeOfPatientID, VR.CS, "TEXT");

		Sequence issuerOfPatientIDQualifiersSequences = seqOthers.newSequence(Tag.IssuerOfPatientIDQualifiersSequence, 1);
		Attributes seqIssuers = new Attributes();
		seqIssuers.setString(Tag.UniversalEntityID, VR.UT, "1.2.250.1.213.1.4.10");
		seqIssuers.setString(Tag.UniversalEntityIDType, VR.CS, "ISO");
		issuerOfPatientIDQualifiersSequences.add(seqIssuers);
		otherPatientIDsSequence.add(seqOthers);

		Sequence conceptNameCodeSequence = attrs.newSequence(Tag.ConceptNameCodeSequence, 1);
		Attributes seqConcept = new Attributes();
		seqConcept.setString(Tag.CodeValue, VR.SH, "113030");
		seqConcept.setString(Tag.CodingSchemeDesignator, VR.SH, "DCM");
		seqConcept.setString(Tag.CodeMeaning, VR.LO, "MANIFEST");
		conceptNameCodeSequence.add(seqConcept);

		Sequence referencedRequestSequence = attrs.newSequence(Tag.ReferencedRequestSequence, 1);
		Attributes seqReferenced = new Attributes();
		seqReferenced.setString(Tag.StudyInstanceUID, VR.UI, c.getStudyID());
		seqReferenced.setString(Tag.AccessionNumber, VR.SH, c.getAccessionNumberExtension());
		seqReferenced.setString(Tag.FillerOrderNumberImagingServiceRequest, VR.SH, "");
		seqReferenced.setString(Tag.RequestedProcedureDescription, VR.LO, "");
		seqReferenced.setString(Tag.RequestedProcedureID, VR.LO, "");
		seqReferenced.setString(Tag.PlacerOrderNumberImagingServiceRequest, VR.LO, c.getOrderExtension());

		Sequence referencedStudySequence = seqReferenced.newSequence(Tag.ReferencedStudySequence, 1);

		Sequence requestedProcedureCodeSequence = seqReferenced.newSequence(Tag.RequestedProcedureCodeSequence, 1);

		Sequence issuerOfAccessionNumberSequence = seqReferenced.newSequence(Tag.IssuerOfAccessionNumberSequence, 1);
		Attributes issuerAttrs = new Attributes();
		issuerAttrs.setString(Tag.UniversalEntityID, VR.UT, c.getAccessionNumberRoot());
		issuerAttrs.setString(Tag.UniversalEntityIDType, VR.CS, "ISO");
		issuerOfAccessionNumberSequence.add(issuerAttrs);

		Sequence orderPlacerIdentifierSequence = seqReferenced.newSequence(Tag.OrderPlacerIdentifierSequence, 1);
		Attributes issuerOrderAttrs = new Attributes();
		issuerOrderAttrs.setString(Tag.UniversalEntityID, VR.UT, c.getOrderRoot());
		issuerOrderAttrs.setString(Tag.UniversalEntityIDType, VR.CS, "ISO");
		orderPlacerIdentifierSequence.add(issuerOrderAttrs);

		referencedRequestSequence.add(seqReferenced);

		Sequence referencedPerformedProcedureStepSequence = attrs.newSequence(Tag.ReferencedPerformedProcedureStepSequence, 1);

		// C-Find on level IMAGE to retrieve missing informations
		this.cFindSCU = new CFindSCU();
		Attributes studyAttrs = this.cFindSCU.CFind(c.getStudyID(), "IMAGE");
		attrs.setString(Tag.StudyDate, VR.DA, studyAttrs.getString(Tag.StudyDate));
		attrs.setString(Tag.StudyTime, VR.DA, studyAttrs.getString(Tag.StudyTime));
		attrs.setString(Tag.ReferringPhysicianName, VR.PN, studyAttrs.getString(Tag.ReferringPhysicianName));
		attrs.setString(Tag.StudyDescription, VR.LO, studyAttrs.getString(Tag.StudyDescription));
		attrs.setString(Tag.StudyID, VR.SH, studyAttrs.getString(Tag.StudyID));
		Sequence currentRequestedProcedureEvidenceSequence  = attrs.newSequence(Tag.CurrentRequestedProcedureEvidenceSequence , 1);
		Attributes AtCurrentRequestedProcedureEvidenceSequence = new Attributes();
		AtCurrentRequestedProcedureEvidenceSequence.setString(Tag.StudyInstanceUID, VR.UI, c.getStudyID());
		String accessionNumberImage = studyAttrs.getSequence(Tag.ReferencedSeriesSequence).get(0).getString(Tag.AccessionNumber);
		String patientIDImage = studyAttrs.getSequence(Tag.ReferencedSeriesSequence).get(0).getString(Tag.PatientID);

		Sequence referencedSeriesSequence  = AtCurrentRequestedProcedureEvidenceSequence.newSequence(Tag.ReferencedSeriesSequence , 1);
		String textValue = "Examen : " + studyAttrs.getString(Tag.StudyDescription) + "\n";
		textValue += "Acte =  : " + c.getEventCode().displayName + "\n";
		for (int i = 0; i < studyAttrs.getSequence(Tag.ReferencedSeriesSequence).size(); i++) {
			textValue += "Série-" + studyAttrs.getSequence(Tag.ReferencedSeriesSequence).get(i).getString(Tag.SeriesInstanceUID) +
					" : " +studyAttrs.getSequence(Tag.ReferencedSeriesSequence).get(i).getString(Tag.Modality) + " @  : " +studyAttrs.getSequence(Tag.ReferencedSeriesSequence).get(i).getString(Tag.SeriesDescription) + "\n";

			studyAttrs.getSequence(Tag.ReferencedSeriesSequence).get(i).remove(Tag.Modality);
			studyAttrs.getSequence(Tag.ReferencedSeriesSequence).get(i).remove(Tag.SeriesDescription);
			studyAttrs.getSequence(Tag.ReferencedSeriesSequence).get(i).remove(Tag.AccessionNumber);
			studyAttrs.getSequence(Tag.ReferencedSeriesSequence).get(i).remove(Tag.PatientID);


			Attributes aReferencedSeriesSequence = new Attributes();
			aReferencedSeriesSequence.addAll(studyAttrs.getNestedDataset(Tag.ReferencedSeriesSequence, i));	
			referencedSeriesSequence.add(aReferencedSeriesSequence);
		}
		currentRequestedProcedureEvidenceSequence.add(AtCurrentRequestedProcedureEvidenceSequence);

		Sequence contentSequence = attrs.newSequence(Tag.ContentSequence, 1);

		for (Attributes attrSeq : attrs.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence).get(0).getSequence(Tag.ReferencedSeriesSequence)) {
			for (Attributes attrSOP : attrSeq.getSequence(Tag.ReferencedSOPSequence)) {

				Attributes contentAttr = new Attributes();
				contentAttr.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
				contentAttr.setString(Tag.ValueType, VR.CS, "IMAGE");
				Sequence referencedSOPSequence = contentAttr.newSequence(Tag.ReferencedSOPSequence, 1);
				Attributes attrCopy = new Attributes();
				attrCopy.addAll(attrSOP);
				referencedSOPSequence.add(attrCopy);
				contentSequence.add(contentAttr);
			}
		}

		Attributes contentAttr = new Attributes();
		contentAttr.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
		contentAttr.setString(Tag.ValueType, VR.CS, "TEXT");
		Sequence conceptNameCodeSequenceTextValue = contentAttr.newSequence(Tag.ConceptNameCodeSequence, 1);
		Attributes attrConceptNameCode = new Attributes();
		attrConceptNameCode.setString(Tag.CodeValue, VR.SH, "113012");
		attrConceptNameCode.setString(Tag.CodingSchemeDesignator, VR.SH, "DCM");
		attrConceptNameCode.setString(Tag.CodeMeaning, VR.LO, "Key Object Description");
		conceptNameCodeSequenceTextValue.add(attrConceptNameCode);
		contentAttr.setString(Tag.TextValue, VR.UT, textValue);

		contentSequence.add(contentAttr);

		fmi.setString(Tag.TransferSyntaxUID, VR.UI, UID.ImplicitVRLittleEndian);
		fmi.setString(Tag.MediaStorageSOPClassUID, VR.UI, UID.KeyObjectSelectionDocumentStorage);
		fmi.setBytes(Tag.FileMetaInformationVersion, VR.OB, new byte[] {(byte)0x00, (byte)0x01});
		fmi.setString(Tag.MediaStorageSOPInstanceUID, VR.UI, sopInstanceUID);
		fmi.setString(Tag.ImplementationClassUID, VR.UI, "1.2.276.0.7230010.3.0.3.6.7");

		if(!accessionNumberImage.equals(c.getAccessionNumberExtension())) {
			Log.info("Wrong accession number between cda and image");
			Log.info("image : " + accessionNumberImage);
			Log.info("cda : " + c.getAccessionNumberExtension());
		}
		else if(!patientIDImage.equals(c.getIpp())) {
			Log.info("Wrong ipp between cda and image");
			Log.info("image : " + patientIDImage);
			Log.info("cda : " + c.getIpp());
		}
		else {
			// Not sure about the tsuid
			//DicomOutputStream dos = new DicomOutputStream(bos, "1.2.840.10008.1.2.1");
			DicomOutputStream dos = new DicomOutputStream(file);
			dos.writeDataset(fmi, attrs);
			dos.flush();
			dos.close();

			//fileContent = bos.toByteArray();
			//parseKOS(new DicomInputStream(new ByteArrayInputStream(fileContent)));
			fileContent = Files.readAllBytes(file.toPath());
			parseKOS(new DicomInputStream(file));
		}
	}

	public KOSFile(KOSFile kos) throws IOException {
		InputStream input = new ByteArrayInputStream(kos.getRawData()); 
		Attributes dataset;
		Attributes fmi = new Attributes();
		try (DicomInputStream dis = new DicomInputStream(input)) {
			dis.setIncludeBulkData(IncludeBulkData.URI);
			dataset = dis.readDataset();
			String textValueOld = dataset.getSequence(Tag.ContentSequence).get(dataset.getSequence(Tag.ContentSequence).size() -1).getString(Tag.TextValue);
			String region = textValueOld.split("Acte =  : ")[1].split("\n")[0];
			dataset.remove(Tag.CurrentRequestedProcedureEvidenceSequence);
			dataset.remove(Tag.ContentSequence);

			// C-Find on level IMAGE to retrieve missing informations
			this.cFindSCU = new CFindSCU();
			Attributes studyAttrs = this.cFindSCU.CFind(dataset.getString(Tag.StudyInstanceUID), "IMAGE");
			if(studyAttrs.size() != 0)
			{
				this.exist = true;
				File file = new File("koNEW.dcm");
				file.createNewFile();
				dataset.setString(Tag.StudyDate, VR.DA, studyAttrs.getString(Tag.StudyDate));
				dataset.setString(Tag.StudyTime, VR.DA, studyAttrs.getString(Tag.StudyTime));
				dataset.setString(Tag.ReferringPhysicianName, VR.PN, studyAttrs.getString(Tag.ReferringPhysicianName));
				dataset.setString(Tag.StudyDescription, VR.LO, studyAttrs.getString(Tag.StudyDescription));
				dataset.setString(Tag.StudyID, VR.SH, studyAttrs.getString(Tag.StudyID));

				String uniqueID = dataset.getString(Tag.SOPInstanceUID);
				Integer incr = Integer.parseInt(uniqueID.substring(uniqueID.length() -1, uniqueID.length())) + 1;
				uniqueID = uniqueID.substring(0, uniqueID.length() - 1 ) + incr;

				dataset.remove(Tag.SOPInstanceUID);
				dataset.setString(Tag.SOPInstanceUID, VR.UI, uniqueID);

				Sequence currentRequestedProcedureEvidenceSequence  = dataset.newSequence(Tag.CurrentRequestedProcedureEvidenceSequence , 1);
				Attributes AtCurrentRequestedProcedureEvidenceSequence = new Attributes();
				AtCurrentRequestedProcedureEvidenceSequence.setString(Tag.StudyInstanceUID, VR.UI, dataset.getString(Tag.StudyInstanceUID));


				Sequence referencedSeriesSequence  = AtCurrentRequestedProcedureEvidenceSequence.newSequence(Tag.ReferencedSeriesSequence , 1);
				String textValue = "Examen : " + studyAttrs.getString(Tag.StudyDescription) + "\n";
				textValue += "Acte =  : " + region + "\n";
				for (int i = 0; i < studyAttrs.getSequence(Tag.ReferencedSeriesSequence).size(); i++) {
					textValue += "Série-" + studyAttrs.getSequence(Tag.ReferencedSeriesSequence).get(i).getString(Tag.SeriesInstanceUID) +
							" : " +studyAttrs.getSequence(Tag.ReferencedSeriesSequence).get(i).getString(Tag.Modality) + " @  : " +studyAttrs.getSequence(Tag.ReferencedSeriesSequence).get(i).getString(Tag.SeriesDescription) + "\n";

					studyAttrs.getSequence(Tag.ReferencedSeriesSequence).get(i).remove(Tag.Modality);
					studyAttrs.getSequence(Tag.ReferencedSeriesSequence).get(i).remove(Tag.SeriesDescription);
					studyAttrs.getSequence(Tag.ReferencedSeriesSequence).get(i).remove(Tag.AccessionNumber);
					studyAttrs.getSequence(Tag.ReferencedSeriesSequence).get(i).remove(Tag.PatientID);


					Attributes aReferencedSeriesSequence = new Attributes();
					aReferencedSeriesSequence.addAll(studyAttrs.getNestedDataset(Tag.ReferencedSeriesSequence, i));	
					referencedSeriesSequence.add(aReferencedSeriesSequence);
				}
				currentRequestedProcedureEvidenceSequence.add(AtCurrentRequestedProcedureEvidenceSequence);

				Sequence contentSequence = dataset.newSequence(Tag.ContentSequence, 1);

				for (Attributes attrSeq : dataset.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence).get(0).getSequence(Tag.ReferencedSeriesSequence)) {
					for (Attributes attrSOP : attrSeq.getSequence(Tag.ReferencedSOPSequence)) {

						Attributes contentAttr = new Attributes();
						contentAttr.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
						contentAttr.setString(Tag.ValueType, VR.CS, "IMAGE");
						Sequence referencedSOPSequence = contentAttr.newSequence(Tag.ReferencedSOPSequence, 1);
						Attributes attrCopy = new Attributes();
						attrCopy.addAll(attrSOP);
						referencedSOPSequence.add(attrCopy);
						contentSequence.add(contentAttr);
					}
				}

				Attributes contentAttr = new Attributes();
				contentAttr.setString(Tag.RelationshipType, VR.CS, "CONTAINS");
				contentAttr.setString(Tag.ValueType, VR.CS, "TEXT");
				Sequence conceptNameCodeSequenceTextValue = contentAttr.newSequence(Tag.ConceptNameCodeSequence, 1);
				Attributes attrConceptNameCode = new Attributes();
				attrConceptNameCode.setString(Tag.CodeValue, VR.SH, "113012");
				attrConceptNameCode.setString(Tag.CodingSchemeDesignator, VR.SH, "DCM");
				attrConceptNameCode.setString(Tag.CodeMeaning, VR.LO, "Key Object Description");
				conceptNameCodeSequenceTextValue.add(attrConceptNameCode);
				contentAttr.setString(Tag.TextValue, VR.UT, textValue);

				contentSequence.add(contentAttr);			

				dataset.setInt(Tag.InstanceNumber, VR.IS, Integer.valueOf(dataset.getString(Tag.InstanceNumber)) + 1);

				fmi = dis.readFileMetaInformation();
				DicomOutputStream dos = new DicomOutputStream(file);
				dos.writeDataset(fmi, dataset);
				dos.flush();
				dos.close();

				fileContent = Files.readAllBytes(file.toPath());
			}
		}
	}

	private String generateUUID(String type) throws UnknownHostException, SocketException {
		InetAddress ip;
		ip = InetAddress.getLocalHost();
		NetworkInterface network = NetworkInterface.getByInetAddress(ip);
		/*byte[] mac = network.getHardwareAddress();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < mac.length; i++) {
			sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? "" : ""));
		}

		String s = sb.toString();
		BigInteger bi = new BigInteger(s, 16);
		long macA = bi.longValue() % 100000000;
		 */
		DateTimeFormatter fmt3 = DateTimeFormatter.ofPattern("YYMMddHHmmss");  
		String oid = BASE_OID + "." + "1234" + "." + new Random().nextInt(99999) + "." +  LocalDateTime.now().format(fmt3) + ".1.1";
		switch(type) {
		case "sopInstance" :
			oid += ".1";
			break;
		case "seriesInstance" : 
			oid += ".2";
			break;
		}

		return oid;
	}

	public KOSFile(byte[] rawData) {
		fileContent = rawData;
		try {
			parseKOS(new DicomInputStream(new ByteArrayInputStream(fileContent)));
		} catch (IOException e) {
			throw new RuntimeException("cannot create input stream : " + e.getMessage());
		}

	}

	private void parseKOS(DicomInputStream dis) {
		try {
			Attributes attributes = dis.readDataset();

			sopInstanceUID = attributes.getString(Tag.SOPInstanceUID);

			patientINS = attributes.getString(Tag.PatientID);

			studyUID = attributes.getString(Tag.StudyInstanceUID);
			//            CurrentRequestedProcedureEvidenceSequence :                               // allStudies
			//              - Item #0                                                               // currentItem
			//                  - ReferencedSeriesSequence :                                        // currentSerieSeq
			//                      - Item #0                                                       // currentSerieInfo
			//                          - RetrieveURL
			//                          - ReferencedSOPSequence
			//								- ReferencedSOPClassUID : "1.2.840.10008.5.1.4.1.1.2" [ CT Image Storage ]
			//								- ReferencedSOPInstanceUID : "1.2.276.0.7230010.3.1.4.8323329.4426.1666943459.930423"
			//                          - SeriesInstanceUID
			Sequence allStudies = attributes.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);
			if (allStudies == null) {
				Log.error("Can't get CurrentRequestedProcedureEvidenceSequence from KOS");
				return;
			}

			if (allStudies.size() !=1) {
				Log.error("There should be only 1 study in the sequence");
				return;
			}
			// We make sure before that we only get one study
			Attributes currentItem = allStudies.get(0);

			Sequence currentSerieSeq = currentItem.getSequence(Tag.ReferencedSeriesSequence);
			if (currentSerieSeq == null) {
				Log.warn("Can't get CurrentRequestedProcedureEvidenceSequence from KOS");
				return;
			}

			for (Attributes currentSerieInfo : currentSerieSeq) {
				SeriesInfo seriesInfo = new SeriesInfo();

				Sequence instanceUIDSequence = currentSerieInfo.getSequence(Tag.ReferencedSOPSequence);
				for (Attributes instanceUIDObject : instanceUIDSequence) {
					String currentInstanceUID = instanceUIDObject.getString(Tag.ReferencedSOPInstanceUID);
					if (currentInstanceUID == null) {
						Log.warn("Can't get ReferencedSOPInstanceUID in ReferencedSOPSequence.");
					} else {
						seriesInfo.instancesUID.add(currentInstanceUID);
					}
				}

				seriesInfo.retrieveURL = currentSerieInfo.getString(Tag.RetrieveURL);

				seriesURL.put(
						currentSerieInfo.getString(Tag.SeriesInstanceUID),
						seriesInfo
						);
			}

			dis.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public String getStudyUID() {
		return studyUID;
	}

	public String getPatientINS() {
		return patientINS;
	}

	public String getSopInstanceUID() {
		return sopInstanceUID;
	}
	
	public Boolean getExist() {
		return exist;
	}


	/**
	 * A map that contains data associated with the series in the KOS
	 * @return { seriesUID => SeriesInfo, ... }
	 */
	public Map<String, SeriesInfo> getSeriesInfo() {
		return seriesURL;
	}

	public byte[] getB64RawData() {
		return Base64.getEncoder().encode(fileContent);
	}

	public byte[] getRawData() {
		return fileContent;
	}



}
