/*
 *  DeleteRequest.java - DRIMBox
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

package com.bcom.drimbox.dmp.xades.request;

import static com.bcom.drimbox.dmp.xades.utils.XadesUUID.XDSAssoc_Update;

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
import com.bcom.drimbox.dmp.vihf.VIHF;
import com.bcom.drimbox.dmp.vihf.VIHFBase;
import com.bcom.drimbox.dmp.vihf.VIHFField;
import com.bcom.drimbox.dmp.xades.BaseElement;
import com.bcom.drimbox.dmp.xades.DocumentEntry;
import com.bcom.drimbox.dmp.xades.SubmissionSet;
import com.bcom.drimbox.dmp.xades.file.CDAFile;
import com.bcom.drimbox.dmp.xades.file.KOSFile;
import com.bcom.drimbox.dmp.xades.sign.XadesSign;
import com.bcom.drimbox.utils.XMLUtils;

import jakarta.enterprise.inject.spi.CDI;

public class DeleteRequest extends BaseXadesRequest {

	@Override
	protected String actionName() {
		return "urn:ihe:iti:2010:UpdateDocumentSet";
	}

	@Override
	protected String serviceName() {
		return "registry";
	}


	// TODO: generic way to put file
	public DeleteRequest(CDAFile referenceCDA, KOSFile kos, String entryUUID) throws Exception {
		super();


		createVIHF(referenceCDA);

		var submitObjectsRequest = soapRequest.createElement("ns4:SubmitObjectsRequest");
		submitObjectsRequest.setAttribute("xmlns", "urn:oasis:names:tc:ebxml-regrep:xsd:rim:3.0");
		submitObjectsRequest.setAttribute("xmlns:ns2", "urn:oasis:names:tc:ebxml-regrep:xsd:rs:3.0");
		submitObjectsRequest.setAttribute("xmlns:ns3", "urn:oasis:names:tc:ebxml-regrep:xsd:query:3.0");
		submitObjectsRequest.setAttribute("xmlns:ns4", "urn:oasis:names:tc:ebxml-regrep:xsd:lcm:3.0");
		body.appendChild(submitObjectsRequest);
		var registryObjectList = soapRequest.createElement("RegistryObjectList");
		submitObjectsRequest.appendChild(registryObjectList);

		// Submission set
		SubmissionSet submissionSet = new SubmissionSet(referenceCDA);
		addBaseElementToNode(registryObjectList, submissionSet);

		// Associations
		registryObjectList.appendChild(createXMLAssociation(submissionSet.getEntryID(), entryUUID, XDSAssoc_Update));

		addRequestPart(XMLUtils.xmlToString(soapRequest));
	}

	@Override
	public String getRequest() {
		try {
			Files.write( Paths.get("allReqDelete.xml"), mimeBoundaryRequest.toString().getBytes());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		return mimeBoundaryRequest.toString();
	}


	StringWriter mimeBoundaryRequest = new StringWriter();
	void addRequestPart(String contents) {
		mimeBoundaryRequest.append(contents);
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

		vihf.build(); // TODO : check return value
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

		var slot2 = soapRequest.createElement("Slot");
		slot2.setAttribute("name", "OriginalStatus");

		var valueList2 = soapRequest.createElement("ValueList");
		slot2.appendChild(valueList2);

		var value2 = soapRequest.createElement("Value");
		value2.appendChild(soapRequest.createTextNode("urn:oasis:names:tc:ebxml-regrep:StatusType:Approved"));
		valueList2.appendChild(value2);
		association.appendChild(slot2);

		var slot = soapRequest.createElement("Slot");
		slot.setAttribute("name", "NewStatus");

		var valueList = soapRequest.createElement("ValueList");
		slot.appendChild(valueList);

		var value = soapRequest.createElement("Value");
		value.appendChild(soapRequest.createTextNode("urn:asip:ci-sis:2010:StatusType:Deleted"));
		valueList.appendChild(value);
		association.appendChild(slot);	

		return association;
	}

	@Override
	public String getContentType() {
		return "application/octet-stream";
	}

	@Override
	public int getContentLength() {
		return mimeBoundaryRequest.toString().length();
	}
}
