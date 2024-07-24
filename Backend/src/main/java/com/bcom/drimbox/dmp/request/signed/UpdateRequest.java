/*
 *  UpdateRequest.java - DRIMBox
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

package com.bcom.drimbox.dmp.request.signed;

import static com.bcom.drimbox.dmp.utils.XDSUUID.XDSAssoc_RPLC;
import static com.bcom.drimbox.dmp.utils.XDSUUID.XDSAssoc_hasMember;
import static com.bcom.drimbox.dmp.utils.XDSUUID.XDSAssoc_signs;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.commons.codec.digest.DigestUtils;
import org.dcm4che3.util.UIDUtils;
import org.w3c.dom.Element;

import com.bcom.drimbox.dmp.security.DMPKeyStore;
import com.bcom.drimbox.dmp.utils.BaseElement;
import com.bcom.drimbox.dmp.utils.DocumentEntry;
import com.bcom.drimbox.dmp.utils.SubmissionSet;
import com.bcom.drimbox.dmp.utils.XadesSign;
import com.bcom.drimbox.dmp.vihf.VIHF;
import com.bcom.drimbox.dmp.vihf.VIHFBase;
import com.bcom.drimbox.dmp.vihf.VIHFField;
import com.bcom.drimbox.document.CDAFile;
import com.bcom.drimbox.document.KOSFile;
import com.bcom.drimbox.utils.XMLUtils;

import jakarta.enterprise.inject.spi.CDI;

public class UpdateRequest extends BaseXadesRequest {

	final String BOUNDARY = "MIMEBoundary_Bound";
	final String ID_PREFIX = "0.";
	final int ID_METADATA = 1;
	int currentID = ID_METADATA;
	private String signDOC = "";

	private String getNextID() {
		String newID = ID_PREFIX + currentID;
		// Remember id prefixes for later
		idPrefixes.add(newID);
		currentID++;
		return newID;
	}
	List<String> idPrefixes = new ArrayList<>();

	@Override
	protected String actionName() {
		return "urn:ihe:iti:2007:ProvideAndRegisterDocumentSet-b";
	}

	@Override
	protected String serviceName() {
		return "repository";
	}


	public UpdateRequest(CDAFile referenceCDA, KOSFile kos, String entryUUID) throws Exception {
		super();

		// Create first ID (0.1) for this document
		getNextID();

		createVIHF(referenceCDA);

		var provideAndRegisterDocumentSetRequest = soapRequest.createElement("ns4:ProvideAndRegisterDocumentSetRequest");
		provideAndRegisterDocumentSetRequest.setAttribute("xmlns", "urn:oasis:names:tc:ebxml-regrep:xsd:rim:3.0");
		provideAndRegisterDocumentSetRequest.setAttribute("xmlns:ns2", "urn:oasis:names:tc:ebxml-regrep:xsd:rs:3.0");
		provideAndRegisterDocumentSetRequest.setAttribute("xmlns:ns3", "urn:oasis:names:tc:ebxml-regrep:xsd:lcm:3.0");
		provideAndRegisterDocumentSetRequest.setAttribute("xmlns:ns4", "urn:ihe:iti:xds-b:2007");
		provideAndRegisterDocumentSetRequest.setAttribute("xmlns:ns5", "urn:oasis:names:tc:ebxml-regrep:xsd:query:3.0");
		body.appendChild(provideAndRegisterDocumentSetRequest);


		var submitObjectsRequest = soapRequest.createElement("ns3:SubmitObjectsRequest");
		provideAndRegisterDocumentSetRequest.appendChild(submitObjectsRequest);

		var registryObjectList = soapRequest.createElement("RegistryObjectList");
		submitObjectsRequest.appendChild(registryObjectList);

		// Submission set
		SubmissionSet submissionSet = new SubmissionSet(referenceCDA);
		addBaseElementToNode(registryObjectList, submissionSet);

		// DocumentEntry fields
		DocumentEntry kosEntry = new DocumentEntry(referenceCDA, DocumentEntry.FileType.KOS, kos);

		String signUniqueID = UIDUtils.createUID();
		// Signature
		XadesSign s = new XadesSign();
		// WARNING : we use rawData for signing, but we send the KOS in B64
		s.addDocument(kosEntry, kos.getRawData());
		String xadesSignature = s.sign(submissionSet.getUniqueID(), signUniqueID);
		xadesSignature = xadesSignature.replaceFirst("[\n\r]+$", "");

		DocumentEntry signatureEntry = new DocumentEntry(submissionSet, referenceCDA, signUniqueID, DigestUtils.sha1Hex(xadesSignature + "\n"), xadesSignature.length());


		// Add document entry to the request
		addBaseElementToNode(registryObjectList, kosEntry);
		addBaseElementToNode(registryObjectList, signatureEntry);

		// Associations
		registryObjectList.appendChild(createXMLAssociation(submissionSet.getEntryID(), kosEntry.getEntryID(), XDSAssoc_hasMember));
		registryObjectList.appendChild(createXMLAssociation(submissionSet.getEntryID(), signatureEntry.getEntryID(), XDSAssoc_hasMember));
		registryObjectList.appendChild(createXMLAssociation(signatureEntry.getEntryID(), submissionSet.getEntryID(), XDSAssoc_signs));
		registryObjectList.appendChild(createXMLAssociation(kosEntry.getEntryID(), entryUUID, XDSAssoc_RPLC));

		// Append ID docs
		provideAndRegisterDocumentSetRequest.appendChild(createDocumentDescription(kosEntry.getEntryID()));
		provideAndRegisterDocumentSetRequest.appendChild(createDocumentDescription(signatureEntry.getEntryID()));

		addRequestPart("application/xop+xml; charset=UTF-8; type=\"application/soap+xml\"; action=\"DocumentRepository_ProvideAndRegisterDocumentSet-b\"",
				getRequestID(), XMLUtils.xmlToString(soapRequest), "binary");



		addRequestPart("application/octet-stream", getSignatureID(), xadesSignature, "binary");
		List<String> ids = getDocumentIDs();
		addRequestPart("application/octet-stream", ids.get(0), new String(kos.getB64RawData(), StandardCharsets.UTF_8), "BASE64");


		this.signDOC = xadesSignature;
		endMimeRequest();
	}

	@Override
	public String getRequest() {
		try {
			Files.write( Paths.get("allReq.xml"), mimeBoundaryRequest.toString().getBytes());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		return mimeBoundaryRequest.toString();
	}

	/**
	 * Return the request ID (the first one generated)
	 */
	private String getRequestID() {
		return idPrefixes.get(0);
	}
	/**
	 * Return the signature ID (the first one generated)
	 */
	private String getSignatureID() {
		return idPrefixes.get(idPrefixes.size()-1);
	}
	private List<String> getDocumentIDs() {
		return idPrefixes.subList(1,idPrefixes.size()-1);
	}


	StringWriter mimeBoundaryRequest = new StringWriter();
	void addRequestPart(String contentType, String contentID, String contents, String encoding) {
		mimeBoundaryRequest.append("--" + BOUNDARY).append(System.lineSeparator());
		mimeBoundaryRequest.append("Content-Type: " + contentType).append(System.lineSeparator());
		mimeBoundaryRequest.append("Content-Transfer-Encoding: " + encoding).append(System.lineSeparator());
		mimeBoundaryRequest.append("Content-ID: <" + contentID+ ">")
		.append(System.lineSeparator())
		.append(System.lineSeparator()); // two new line is intended here
		mimeBoundaryRequest.append(contents).append(System.lineSeparator());
	}

	private void endMimeRequest() {
		mimeBoundaryRequest.write("--"+ BOUNDARY +"--");
	}
	private void addBaseElementToNode(Element parentNode, BaseElement element) {
		var exportedNode = element.getXmlDocument().getFirstChild();
		parentNode.appendChild(soapRequest.importNode(exportedNode, true));
	}

	private void createVIHF(CDAFile referenceCDA) {
		VIHF vihf = new VIHF();

		var hcf = referenceCDA.getHealthcareFacilityType();
		String secteurActivite = hcf.code + "^" + hcf.codingScheme;
		vihf.setNameID(referenceCDA.getAuthorID());

		DMPKeyStore dmpKeyStore = CDI.current().select(DMPKeyStore.class).get();
		vihf.setIssuer(dmpKeyStore.getVIHFIssuer());

		vihf.setAuthContext(VIHFBase.AuthContext.TLS);

		vihf.setSimpleAttribute(VIHFField.VIHF_VERSION, "3.0");
		vihf.setSimpleAttribute(VIHFField.IDENTIFIANT_STRUCTURE, referenceCDA.getStructureID());
		vihf.setSimpleAttribute(VIHFField.SECTEUR_ACTIVITE, secteurActivite);
		vihf.setSimpleAttribute(VIHFField.AUTHENTIFICATION_MODE, VIHFBase.AuthentificationMode.INDIRECT.toString());
		vihf.setSimpleAttribute(VIHFField.RESSOURCE_ID, referenceCDA.getPatientID());
		vihf.setSimpleAttribute(VIHFField.SUBJECT_ID, referenceCDA.getPatientGiven() + " " + referenceCDA.getPatientName());
		vihf.setSimpleAttribute(VIHFField.RESSOURCE_URN, "urn:dmp");
		vihf.setSimpleAttribute(VIHFField.LPS_ID, "01.12.12");
		vihf.setSimpleAttribute(VIHFField.LPS_NOM, "DRIMbox");
		vihf.setSimpleAttribute(VIHFField.LPS_VERSION, "1.0");
		vihf.setSimpleAttribute(VIHFField.LPS_HOMOLOGATION_DMP, "BCO-465897-tmp2");

		vihf.addRole(new VIHFBase.CommonVIHFAttribute("10", "1.2.250.1.71.1.2.7", "", "Medecin"));
		vihf.addRole(new VIHFBase.CommonVIHFAttribute("SM26", "1.2.250.1.71.4.2.5", "", "Qualifie en Medecine Generale (SM)"));

		vihf.setPurposeOfUse(new VIHFBase.CommonVIHFAttribute("normal", "1.2.250.1.213.1.1.4.248", "mode acces VIHF 1.0", "Acces normal"));

		vihf.build();
		//vihf.exportVIHFToXML("opensml-notsigned.xml");
		vihf.sign();
		//vihf.exportVIHFToXML("opensml-signed.xml");

		setVIHF(vihf);
	}


	private Element createXMLAssociation(String source, String target, String type) {
		var association = soapRequest.createElement("Association");
		association.setAttribute("associationType", type);
		association.setAttribute("sourceObject", source);
		association.setAttribute("targetObject", target);
		association.setAttribute("id", UUID.randomUUID().toString());

		var slot = soapRequest.createElement("Slot");
		slot.setAttribute("name", "SubmissionSetStatus");

		var valueList = soapRequest.createElement("ValueList");
		slot.appendChild(valueList);

		var value = soapRequest.createElement("Value");
		value.appendChild(soapRequest.createTextNode("Original"));
		valueList.appendChild(value);
		association.appendChild(slot);

		return association;
	}

	private Element createDocumentDescription(String docUUID) {
		var document = soapRequest.createElement("ns4:Document");
		document.setAttribute("id", docUUID);
		var include = soapRequest.createElement("xop:Include");
		include.setAttribute("xmlns:xop", "http://www.w3.org/2004/08/xop/include");
		include.setAttribute("href", "cid:" + getNextID());
		document.appendChild(include);

		return document;
	}

	@Override
	public String getContentType() {
		return "multipart/related;boundary=\"" + BOUNDARY + "\"; type=\"application/xop+xml\"; start=\"<" + getRequestID() + ">\"; start-info=\"application/soap+xml\"; action=\""+ actionName() + "\"";
	}

	@Override
	public int getContentLength() {
		return mimeBoundaryRequest.toString().length();
	}

	public String getSignDoc() {
		return this.signDOC;
	}
}
