/*
 *  CDAFile.java - DRIMBox
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import com.bcom.drimbox.dmp.xades.utils.XadesType;

import io.quarkus.logging.Log;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Nodes;
import nu.xom.XPathContext;
import nu.xom.canonical.Canonicalizer;

public class CDAFile {
	private String cdaID;
	private String authorInstitution;
	private String authorPerson;
	private String legalAuthenticator;
	private XadesType.ClassificationCode eventCode;
	private String sourcePatientID;
	private String patientID;
	private String patientBirthDate;
	private String patientSex;
	private XadesType.SourcePatientInfo sourcePatientInfo;
	private String serviceStartTime;
	private String serviceStopTime;
	private XadesType.ClassificationCode practiceSetting;
	private XadesType.ClassificationCode healthcareFacilityType;
	private String studyID;
	private String orderRoot;
	private String orderExtension;
	private String accessionNumberRoot;
	private String accessionNumberExtension;
	private XadesType.ClassificationCode confidentiality; 
	private String ipp;

	public String getAuthorID() {
		return authorID;
	}

	public String getPatientGiven() {
		return patientGiven;
	}

	public String getStructureID() {
		return structureID;
	}

	// VIHF purposes
	private String authorID;
	private String patientGiven;

	public String getPatientName() {
		return patientName;
	}

	private String patientName;
	private String structureID;

	private String cdaContents;

	/**
	 * New CDA based on a file
	 *
	 * @param file CDA file
	 */
	public CDAFile(File file) {
		try {
			cdaContents = Files.readString(file.toPath());
			parseCDA();
		} catch (IOException e) {
			Log.error("Can't parse CDA from file : " + file);
			e.printStackTrace();
		}
	}

	/**
	 * New CDA with the raw contents
	 * @param cdaContents CDA XML contents
	 */
	public CDAFile(String cdaContents) {
		this.cdaContents = cdaContents;
		parseCDA();
	}

	public byte[] getRawData() {
		try {
			// Canonicalization of the CDA before sending
			Builder builder = new Builder();
			nu.xom.Document cda = builder.build(cdaContents, null);

			ByteArrayOutputStream os = new ByteArrayOutputStream();
			nu.xom.canonical.Canonicalizer c = new nu.xom.canonical.Canonicalizer(os, Canonicalizer.CANONICAL_XML_WITH_COMMENTS);
			c.write(cda);

			//String contentWithoutLineBreaks = os.toString().replaceAll("\\s+","");

			return os.toByteArray();
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException();
		}
	}



	private String stringValueOfPath(String xpath) {
		return xpathRequest(xpath).get(0).getValue();
	}

	private Nodes xpathRequest(String xpath) {
		// Replace all /str by /xpathNamespacePrefix:str
		// e.g : //oui/douze => //ns:oui/ns:douze
		// We need a regular expression to prevent replacing // (// => /ns:/ is not good)
		xpath = xpath.replaceAll("\\/(\\w)", "/"+ xpathNamespacePrefix + ":$1");
		var xpathResult = cda.query(xpath, xpathContext);
		if (xpathResult.size() == 0) {
			Log.error("Can't get value of xpath " + xpath + " while parsing CDA");
			return null;
		}

		return xpathResult;
	}
	private Element getElement(String xpath) {
		return (Element) xpathRequest(xpath).get(0);
	}

	private XadesType.ClassificationCode getClassificationCode(String xpath) {
		Element n = getElement(xpath);
		return new XadesType.ClassificationCode(n.getAttributeValue("code"), n.getAttributeValue("displayName"), n.getAttributeValue("codeSystem"));
	}

	private Document cda;
	XPathContext xpathContext;
	// This is needed to search in the XML document. This is bc XOM enforce namespace resolution, and we need to tell him the current namespace. For now it doesn't support the *: syntax so we need to do this.
	final static String xpathNamespacePrefix = "ns";
	private void parseCDA() {
		try {
			Builder parser = new Builder();
			cda = parser.build(cdaContents, null);
			// We need to create a context with the right namespace
			xpathContext = XPathContext.makeNamespaceContext(cda.getRootElement());
			xpathContext.addNamespace(xpathNamespacePrefix, cda.getRootElement().getNamespaceURI());

			patientName = stringValueOfPath("//recordTarget/patientRole/patient/name/family");
			patientGiven = stringValueOfPath("//recordTarget/patientRole/patient/name/given");


			confidentiality = getClassificationCode("//confidentialityCode");

			var authorAttr = getElement("//author/assignedAuthor/representedOrganization/id");
			structureID = authorAttr.getAttributeValue("extension");
			authorInstitution = stringValueOfPath("//author/assignedAuthor/representedOrganization/name") + "^^^^^&" + authorAttr.getAttributeValue("root") + "&ISO^IDNST^^^" + structureID;

			var authorPersonAttr = getElement("//author/assignedAuthor/id");
			authorID = authorPersonAttr.getAttributeValue("extension");
			authorPerson = authorID + "^" + stringValueOfPath("//author/assignedAuthor/assignedPerson/name/family")+ "^" + stringValueOfPath("//author/assignedAuthor/assignedPerson/name/given") + "^^^^^^&" + authorPersonAttr.getAttributeValue("root") + "&ISO^D^^^IDNPS";


			var legalAuthAttr = getElement("//legalAuthenticator/assignedEntity/id");
			legalAuthenticator = legalAuthAttr.getAttributeValue("extension") + "^" + patientName + "^" + patientGiven + "^^^^^^&" + legalAuthAttr.getAttributeValue("root") + "&ISO^D^^^IDNPS";


			var patientIDAttr = getElement("//recordTarget/patientRole/id");
			sourcePatientID = patientIDAttr.getAttributeValue("extension") + "^^^&" + patientIDAttr.getAttributeValue("root") + "&ISO^PI";
			patientID = patientIDAttr.getAttributeValue("extension") + "^^^&" + patientIDAttr.getAttributeValue("root") + "&ISO^NH";
			
			var patientIPPAttr = getElement("//recordTarget/patientRole/id[2]");
			ipp = patientIPPAttr.getAttributeValue("extension");
			
			var patientAgeAttr = getElement("//recordTarget/patientRole/patient/birthTime");
			var patientSexAttr = getElement("//recordTarget/patientRole/patient/administrativeGenderCode");
			sourcePatientInfo = new XadesType.SourcePatientInfo(patientName + "^" + patientGiven + "^^^^^L", patientAgeAttr.getAttributeValue("value"), patientSexAttr.getAttributeValue("code"));
			patientBirthDate = patientAgeAttr.getAttributeValue("value");
			patientSex = patientSexAttr.getAttributeValue("code");

			
			serviceStartTime = getElement("//documentationOf/serviceEvent/effectiveTime/low").getAttributeValue("value");
			serviceStopTime = getElement("//documentationOf/serviceEvent/effectiveTime/high").getAttributeValue("value");

			practiceSetting = getClassificationCode("//documentationOf/serviceEvent/performer/assignedEntity/representedOrganization/standardIndustryClassCode");
			healthcareFacilityType = getClassificationCode("//componentOf/encompassingEncounter/location/healthCareFacility/code");


			studyID = getElement("//documentationOf/serviceEvent/id").getAttributeValue("root");
			accessionNumberExtension = getElement("//inFulfillmentOf/order/*[local-name()='accessionNumber']").getAttributeValue("extension");
			accessionNumberRoot = getElement("//inFulfillmentOf/order/*[local-name()='accessionNumber']").getAttributeValue("root");
			orderExtension = getElement("//inFulfillmentOf/order/id").getAttributeValue("extension");
			orderRoot = getElement("//inFulfillmentOf/order/id").getAttributeValue("root");

			eventCode = getClassificationCode("//documentationOf/serviceEvent/code");

			this.cdaID = getElement("//id").getAttributeValue("root");

		} catch (Exception e) {
			Log.error("Can't parse CDA");
			e.printStackTrace();
		}
	}

	public XadesType.ClassificationCode getConfidentiality() {
		return confidentiality;
	}


	public String getAuthorInstitution() {
		return authorInstitution;
	}

	public String getAuthorPerson() {
		return authorPerson;
	}

	public String getLegalAuthenticator() {
		return legalAuthenticator;
	}

	public XadesType.ClassificationCode getEventCode() {
		return eventCode;
	}

	public String getSourcePatientID() {
		return sourcePatientID;
	}

	public String getPatientID() {
		return patientID;
	}

	public XadesType.SourcePatientInfo getSourcePatientInfo() {
		return sourcePatientInfo;
	}

	public String getServiceStartTime() {
		return serviceStartTime;
	}

	public String getServiceStopTime() {
		return serviceStopTime;
	}

	public XadesType.ClassificationCode getPracticeSetting() {
		return practiceSetting;
	}

	public XadesType.ClassificationCode getHealthcareFacilityType() {
		return healthcareFacilityType;
	}

	public String getStudyID() {
		return studyID;
	}

	public String getCdaID() {
		return cdaID;
	}

	public void setCdaID(String cdaID) {
		this.cdaID = cdaID;
	}

	public String getPatientBirthDate() {
		return patientBirthDate;
	}

	public void setPatientBirthDate(String patientBirthDate) {
		this.patientBirthDate = patientBirthDate;
	}

	public String getPatientSex() {
		return patientSex;
	}

	public void setPatientSex(String patientSex) {
		this.patientSex = patientSex;
	}

	public String getOrderRoot() {
		return orderRoot;
	}

	public void setOrderRoot(String orderRoot) {
		this.orderRoot = orderRoot;
	}

	public String getOrderExtension() {
		return orderExtension;
	}

	public void setOrderExtension(String orderExtension) {
		this.orderExtension = orderExtension;
	}

	public String getAccessionNumberRoot() {
		return accessionNumberRoot;
	}

	public void setAccessionNumberRoot(String accessionNumberRoot) {
		this.accessionNumberRoot = accessionNumberRoot;
	}

	public String getAccessionNumberExtension() {
		return accessionNumberExtension;
	}

	public void setAccessionNumberExtension(String accessionNumberExtension) {
		this.accessionNumberExtension = accessionNumberExtension;
	}

	public String getIpp() {
		return ipp;
	}

	public void setIpp(String ipp) {
		this.ipp = ipp;
	}
}
