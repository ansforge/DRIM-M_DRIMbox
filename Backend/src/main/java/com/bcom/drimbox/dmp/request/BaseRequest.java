/*
 *  BaseRequest.java - DRIMBox
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

package com.bcom.drimbox.dmp.request;

import com.bcom.drimbox.dmp.security.DMPKeyStore;
import com.bcom.drimbox.dmp.vihf.VIHF;
import com.bcom.drimbox.dmp.vihf.VIHFBase;
import io.quarkus.logging.Log;
import com.bcom.drimbox.dmp.vihf.VIHFField;
import org.eclipse.microprofile.config.ConfigProvider;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import jakarta.enterprise.inject.spi.CDI;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Base class for SOAP Request.
 *
 * It initializes the base structure for a SOAP request using raw xml.
 * You can create a subclass to customize the body.
 */
public abstract class BaseRequest {

	/**
	 * Base XML document
	 * You can create new nodes with soapRequest.createElement()
	 */
	protected Document soapRequest;

	/**
	 * Envelope XML element
	 */
	protected Element envelope;

	/**
	 * Header XML element
	 */
	protected Element header;

	/**
	 * Security node XML element
	 */
	protected Element securityNode;

	/**
	 * Body XML element
	 */
	protected Element body;


	// Field definition
	protected static final String FIELD_CODE_PROFESSION = "codeProfession";
	protected static final String FIELD_CODE_SAVOIR_FAIRE = "codeSavoirFaire";
	protected static final String FIELD_ACTIVITIES = "activities";


	/**
	 * Create the base structure of the document
	 */
	protected BaseRequest() {
		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder;
		try {
			docBuilder = docFactory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			throw new RuntimeException(e);
		}

		soapRequest = docBuilder.newDocument();
		//         // Omit standalone declaration https://stackoverflow.com/questions/8438105/how-to-remove-standalone-attribute-declaration-in-xml-document
		soapRequest.setXmlStandalone(true);

		initEnvelope();
		initHeader();
		initBody();
	}

	/**
	 * Initialize the body component
	 */
	private void initBody() {
		body = soapRequest.createElement("soap:Body");
		envelope.appendChild(body);
	}

	/**
	 * Initialize the header component
	 */
	private void initHeader() {
		final String xmlNamespace = "xmlns";
		final String xmlNamespaceValue = "http://www.w3.org/2005/08/addressing";

		header = soapRequest.createElement("soap:Header");

		Element messageID = soapRequest.createElement("MessageID");
		messageID.setAttribute(xmlNamespace, xmlNamespaceValue);
		messageID.appendChild(soapRequest.createTextNode("urn:uuid:" + UUID.randomUUID()));
		header.appendChild(messageID);


		String dmpURL = getServiceURL();
		Element to = soapRequest.createElement("To");
		to.setAttribute(xmlNamespace, xmlNamespaceValue);
		to.appendChild(soapRequest.createTextNode(dmpURL));
		header.appendChild(to);


		Element action = soapRequest.createElement("Action");
		action.setAttribute("mustUnderstand", "true");
		action.setAttribute(xmlNamespace, xmlNamespaceValue);
		action.appendChild(soapRequest.createTextNode(actionName()));
		header.appendChild(action);


		envelope.appendChild(header);
	}

	/**
	 * Initialize the Envelope component
	 */
	protected void initEnvelope() {
		envelope = soapRequest.createElement("soap:Envelope");
		envelope.setAttribute("xmlns:soap", "http://www.w3.org/2003/05/soap-envelope");
		envelope.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
		envelope.setAttribute("xmlns:xsd", "http://www.w3.org/2001/XMLSchema");
		envelope.setAttribute("xmlns:urn", "urn:hl7-org:v3");

		soapRequest.appendChild(envelope);
	}

	/**
	 * Create Slot element (with given Name and Value) and add it to the given element
	 *
	 * @param element The slot element created will be appended to this element
	 * @param name  Name attribute of the Slot
	 * @param value Value of the Value node
	 */
	protected void createSlot(Element element, String name, String value) {
		Element slot = soapRequest.createElement("Slot");
		slot.setAttribute("name", name);


		Element valueList = soapRequest.createElement("ValueList");
		slot.appendChild(valueList);

		var valueElement = soapRequest.createElement("Value");
		valueElement.appendChild(soapRequest.createTextNode(value));
		valueList.appendChild(valueElement);

		element.appendChild(slot);
	}

	/**
	 * Define the action name (put in the Header/Action node)
	 * @return Action name defined by the SOAP request
	 */
	protected abstract String actionName();

	/**
	 * Define the service name that will be appended to the dmp url.
	 *
	 * The DMP url is retrieved by getting the dmp.baseurl property in application.properties
	 * The service name will be appended to this url.
	 *
	 * Example :
	 *
	 * Base url defined in application.properties :
	 * <code>dmp.baseurl=https://dev9.lps2.dmp.gouv.fr/si-dmp-server/v2/services<code>
	 *
	 * Service name :
	 * <code> protected String serviceName() { return "registry";}</code>
	 *
	 * getServiceURL() will return :
	 * <code>https://dev9.lps2.dmp.gouv.fr/si-dmp-server/v2/services/registry</code>
	 *
	 * @return DMP service name
	 */
	protected abstract String serviceName();

	/**
	 * Get service url based on dmp.baseurl and serviceName()
	 *
	 * This method is used for the SOAP message construction and to know to which url that the SOAP message will be sent.
	 *
	 * @see #serviceName()
	 * @return Complete service URL
	 */
	public String getServiceURL() {
		return ConfigProvider.getConfig().getValue("dmp.baseurl", String.class) + "/" + serviceName();
	}

	/**
	 * Get final SOAP request
	 * @return SOAP request
	 */
	public String getRequest() {
		try {
			DOMSource domSource = new DOMSource(soapRequest);

			// An XML External Entity or XSLT External Entity (XXE) vulnerability can occur when a
			// javax.xml.transform.Transformer is created without enabling "Secure Processing" or when one is created without disabling external DTDs.
			TransformerFactory factory = TransformerFactory.newInstance();
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

			Transformer transformer = factory.newTransformer();


			StringWriter sw = new StringWriter();
			StreamResult sr = new StreamResult(sw);
			transformer.transform(domSource, sr);

			// For debug purposes
			//            PrintWriter printWriter = new PrintWriter("request.xml");
			//            Objects.requireNonNull(printWriter).println(sw.toString());
			//            printWriter.close();

			return sw.toString();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Convenience function to create a signed VIHF base on a nameID and an INS.
	 * The VIHF will be added to the soap request automatically.
	 *
	 * @param userInfo Current user info retrieved from PSC
	 * @param ins    Patient INS (used for VIHFField.RessourceID)
	 *
	 * @return True if success, false otherwise
	 */
	public Boolean createVIHF(JsonObject userInfo, String ins, String secteurActivite) {
		if (userInfo == null) {
			Log.warn("Can't create VIHF when userInfo is null.");
			return false;
		}

		VIHF vihf = new VIHF();

		JsonObject exercices = userInfo.getJsonObject("SubjectRefPro").getJsonArray("exercices").getJsonObject(0);
		String valueProfession = getCisisValue(FIELD_CODE_PROFESSION, exercices.getString(FIELD_CODE_PROFESSION));
		String valueSavoirFaire = getCisisValue(FIELD_CODE_SAVOIR_FAIRE, exercices.getString(FIELD_CODE_SAVOIR_FAIRE));
		JsonObject activites = null;


		for (int i=0; i < exercices.getJsonArray(FIELD_ACTIVITIES).size(); i++) {
			if(exercices.getJsonArray(FIELD_ACTIVITIES).getJsonObject(i).getString("raisonSocialeSite").equals(secteurActivite))
				activites = exercices.getJsonArray(FIELD_ACTIVITIES).getJsonObject(i);
		}

		if (activites == null) {
			Log.fatal("Couldn't find activities field in userInfo while creating VIHF");
			return false;
		}

		vihf.setNameID(userInfo.getString("SubjectNameID"));

		DMPKeyStore dmpKeyStore = CDI.current().select(DMPKeyStore.class).get();
		vihf.setIssuer(dmpKeyStore.getVIHFIssuer());

		vihf.setAuthContext(VIHFBase.AuthContext.TLS);

		vihf.setSimpleAttribute(VIHFField.VIHF_VERSION, "3.0");
		vihf.setSimpleAttribute(VIHFField.IDENTIFIANT_STRUCTURE, activites.getString("ancienIdentifiantDeLaStructure"));
		vihf.setSimpleAttribute(VIHFField.SECTEUR_ACTIVITE, activites.getString("codeSecteurDactivite") + "^1.2.250.1.71.4.2.4");
		vihf.setSimpleAttribute(VIHFField.AUTHENTIFICATION_MODE, VIHFBase.AuthentificationMode.AIR.toString());
		vihf.setSimpleAttribute(VIHFField.RESSOURCE_ID, ins + "^^^&1.2.250.1.213.1.4.10&ISO^NH");
		vihf.setSimpleAttribute(VIHFField.SUBJECT_ID, exercices.getString("prenomDexercice") + " " + exercices.getString("nomDexercice"));
		vihf.setSimpleAttribute(VIHFField.RESSOURCE_URN, "urn:dmp");  //279035121518989^^^&1.2.250.1.213.1.4.10&ISO^NH
		vihf.setSimpleAttribute(VIHFField.LPS_ID, "01.12.12");
		vihf.setSimpleAttribute(VIHFField.LPS_NOM, "DRIMbox");
		vihf.setSimpleAttribute(VIHFField.LPS_VERSION, "1.0");
		vihf.setSimpleAttribute(VIHFField.LPS_HOMOLOGATION_DMP, "BCO-465897-tmp2");

		vihf.addRole(new VIHFBase.CommonVIHFAttribute(exercices.getString(FIELD_CODE_PROFESSION).replaceAll("é", "e"), "1.2.250.1.71.1.2.7", "professions", valueProfession.replaceAll("é", "e")));
		vihf.addRole(new VIHFBase.CommonVIHFAttribute(exercices.getString(FIELD_CODE_SAVOIR_FAIRE).replaceAll("é", "e"), "1.2.250.1.71.4.2.5", "specialites RPPS", valueSavoirFaire.replaceAll("é", "e")));

		vihf.setPurposeOfUse(new VIHFBase.CommonVIHFAttribute("normal", "1.2.250.1.213.1.1.4.248", "mode acces VIHF 1.0", "Acces normal"));

		vihf.build();
		//vihf.exportVIHFToXML("opensml-notsigned.xml");
		vihf.sign();
		//vihf.exportVIHFToXML("opensml-signed.xml");

		setVIHF(vihf);

		return true;
	}
	
	/**
	 * Convenience function to create a signed VIHF base on a nameID and an INS.
	 * The VIHF will be added to the soap request automatically.
	 *
	 * @param userInfo Current user info retrieved from PSC
	 * @param ins    Patient INS (used for VIHFField.RessourceID)
	 *
	 * @return True if success, false otherwise
	 */
	public Boolean createVIHF(byte[] rawMetadata, String ins) {
		String metadatas = new String(rawMetadata, StandardCharsets.UTF_8);
		
		VIHF vihf = new VIHF();

		vihf.setNameID(metadatas.split("<saml2:NameID>")[1].split("<")[0]);

		DMPKeyStore dmpKeyStore = CDI.current().select(DMPKeyStore.class).get();
		vihf.setIssuer(dmpKeyStore.getVIHFIssuer());

		vihf.setAuthContext(VIHFBase.AuthContext.TLS);
	
		vihf.setSimpleAttribute(VIHFField.VIHF_VERSION, "3.0");
		vihf.setSimpleAttribute(VIHFField.IDENTIFIANT_STRUCTURE, metadatas.split("Identifiant_Structure")[1].split("<saml2:AttributeValue>")[1].split("<")[0]);
		vihf.setSimpleAttribute(VIHFField.SECTEUR_ACTIVITE, metadatas.split("Secteur_Activite")[1].split("<saml2:AttributeValue>")[1].split("<")[0]);
		vihf.setSimpleAttribute(VIHFField.AUTHENTIFICATION_MODE, VIHFBase.AuthentificationMode.AIR.toString());
		vihf.setSimpleAttribute(VIHFField.RESSOURCE_ID, ins + "^^^&1.2.250.1.213.1.4.10&ISO^NH");
		vihf.setSimpleAttribute(VIHFField.SUBJECT_ID, metadatas.split("subject-id")[1].split("<saml2:AttributeValue>")[1].split("<")[0]);
		vihf.setSimpleAttribute(VIHFField.RESSOURCE_URN, "urn:dmp");  //279035121518989^^^&1.2.250.1.213.1.4.10&ISO^NH
		vihf.setSimpleAttribute(VIHFField.LPS_ID, "01.12.12");
		vihf.setSimpleAttribute(VIHFField.LPS_NOM, "DRIMbox");
		vihf.setSimpleAttribute(VIHFField.LPS_VERSION, "1.0");
		vihf.setSimpleAttribute(VIHFField.LPS_HOMOLOGATION_DMP, "BCO-465897-tmp2");

		vihf.addRole(new VIHFBase.CommonVIHFAttribute(metadatas.split("<Role")[1].split("code=\"")[1].split("\"")[0], "1.2.250.1.71.1.2.7", "professions", metadatas.split("<Role")[1].split("displayName=\"")[1].split("\"")[0]));
		vihf.addRole(new VIHFBase.CommonVIHFAttribute(metadatas.split("<Role")[2].split("code=\"")[1].split("\"")[0], "1.2.250.1.71.4.2.5", "specialites RPPS", metadatas.split("<Role")[2].split("displayName=\"")[1].split("\"")[0]));

		vihf.setPurposeOfUse(new VIHFBase.CommonVIHFAttribute("normal", "1.2.250.1.213.1.1.4.248", "mode acces VIHF 1.0", "Acces normal"));

		vihf.build();
		//vihf.exportVIHFToXML("opensml-notsigned.xml");
		vihf.sign();
		//vihf.exportVIHFToXML("opensml-signed.xml");

		setVIHF(vihf);

		return true;
	}
	
	

	/**
	 * Retrieve field "professions" and "specialites RPPS" from esante json file
	 *
	 * @param type FIELD_CODE_PROFESSION or FIELD_CODE_SAVOIR_FAIRE
	 * @param value code associated with type
	 * @return value found in json file for the value param given
	 */
	private String getCisisValue(String type, String value) {
		if(type.equals(FIELD_CODE_PROFESSION)) {
			InputStream is = getFileFromResourceAsStream("CISIS/TRE_G15-ProfessionSante-FHIR.json");
			JsonReader rdr = Json.createReader(is);
			JsonObject json = rdr.readObject();
			JsonArray jArr = json.getJsonArray("concept");
			for (int i=0; i < jArr.size(); i++) {
				if(jArr.getJsonObject(i).getString("code").equals(value))
					return jArr.getJsonObject(i).getString("display");
			}
		}
		else if (type.equals(FIELD_CODE_SAVOIR_FAIRE)) {
			InputStream is = getFileFromResourceAsStream("CISIS/TRE_R01-EnsembleSavoirFaire-CISIS-FHIR.json");
			JsonReader rdr = Json.createReader(is);
			JsonObject json = rdr.readObject();
			JsonArray jArr = json.getJsonArray("concept");
			for (int i=0; i < jArr.size(); i++) {
				if(jArr.getJsonObject(i).getString("code").equals(value))
					return jArr.getJsonObject(i).getString("display");
			}
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


	/**
	 * Set a VIHF to the SOAP request
	 * @param vihf VIHF to add
	 */
	public void setVIHF(VIHFBase vihf) {
		if (securityNode == null) {
			securityNode = soapRequest.createElement("wsse:Security");
			securityNode.setAttribute("xmlns:wsse", "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd");
			header.appendChild(securityNode);
		}

		Node vihfXMLNode = soapRequest.importNode(vihf.getVIHF(), true);
		securityNode.appendChild(vihfXMLNode);
	}


}
