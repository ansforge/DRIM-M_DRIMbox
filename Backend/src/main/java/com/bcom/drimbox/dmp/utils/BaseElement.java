/*
 *  BaseElement.java - DRIMBox
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

package com.bcom.drimbox.dmp.utils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;

public abstract class BaseElement {

    private static int idCLA = 1;
    protected String getNextCLAIdentifier() {
        return "cla" + idCLA++;
    }

    private static int idEi = 1;
    protected String getNextEIIdentifier() {
        return "ei" + idEi++;
    }

    protected Document xmlDocument;

    protected String patientID;
    protected String uniqueID;
    protected String title;
    protected String comments;

    public String getEntryID() {
        return entryID;
    }

    /**
     * Custom ID associated with the entry and defined by the class that derives from it
     */
    protected String entryID;

    protected BaseElement() {
        // XML node creation
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder;
        try {
            docBuilder = docFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
        xmlDocument = docBuilder.newDocument();

    }

    public String getUniqueID() {
        return uniqueID;
    }

    protected Element createNameField(String name) {
        return createLocalizedStringField("Name", name);
    }

    protected Element createDescriptionField(String description) {
        return createLocalizedStringField("Description", description);
    }
    protected Element createLocalizedStringField(String fieldName, String name) {
        var nameElement = xmlDocument.createElement(fieldName);
        var localizedString = xmlDocument.createElement("LocalizedString");
        localizedString.setAttribute("charset", "UTF8");
        localizedString.setAttribute("xml:lang", "FR");
        localizedString.setAttribute("value", name);
        nameElement.appendChild(localizedString);
        return nameElement;
    }


    /**
     * Create base classification node with nodeRepresentation
     * @param nodeUUID
     * @param nodeRepresentation If "" argument will be omitted
     * @return
     */
    protected Element createSchemeClassificationField(String nodeUUID, String nodeRepresentation) {
        return createBaseClassificationField(nodeUUID, "classificationScheme", nodeRepresentation);
    }

    protected Element createNodeClassificationField(String nodeUUID) {
        return createBaseClassificationField(nodeUUID, "classificationNode", "");
    }
    protected Element createSchemeClassificationField(String nodeUUID, XDSType.ClassificationCode classificationCode) {
        var contentTypeElement = createBaseClassificationField(nodeUUID, "classificationScheme", classificationCode.code);
        contentTypeElement.appendChild(createSlotField("codingScheme", classificationCode.codingScheme));
        contentTypeElement.appendChild(createNameField(classificationCode.displayName));

        return contentTypeElement;
    }

    private Element createBaseClassificationField(String nodeUUID, String classificationType, String nodeRepresentation) {
        var classificationElement = xmlDocument.createElement("Classification");

        classificationElement.setAttribute(classificationType, nodeUUID);
        classificationElement.setAttribute("classifiedObject", entryID);
        if (!nodeRepresentation.isEmpty()) {
            classificationElement.setAttribute("nodeRepresentation", nodeRepresentation);
        }
        classificationElement.setAttribute("id", getNextCLAIdentifier());


        return classificationElement;
    }


    protected Element createExternalIdentifierField(String nodeUUID, String value, String name) {
        var externalIdentifierElement = xmlDocument.createElement("ExternalIdentifier");
        externalIdentifierElement.setAttribute("id", getNextEIIdentifier());
        externalIdentifierElement.setAttribute("identificationScheme", nodeUUID);
        externalIdentifierElement.setAttribute("registryObject", entryID);
        externalIdentifierElement.setAttribute("value", value);
        externalIdentifierElement.appendChild(createNameField(name));

        return externalIdentifierElement;
    }

    protected Element createSlotField(String name, String value) {

        var slotElement = xmlDocument.createElement("Slot");
        slotElement.setAttribute("name", name);

        var valueList = xmlDocument.createElement("ValueList");
        slotElement.appendChild(valueList);

        var valueField = xmlDocument.createElement("Value");
        valueField.appendChild(xmlDocument.createTextNode(value));
        valueList.appendChild(valueField);
        return slotElement;
    }

    protected Element createSlotField(String name, List<String> values) {

        var slotElement = xmlDocument.createElement("Slot");
        slotElement.setAttribute("name", name);

        var valueList = xmlDocument.createElement("ValueList");
        slotElement.appendChild(valueList);

        for(String value : values) {
            var valueField = xmlDocument.createElement("Value");
            valueField.appendChild(xmlDocument.createTextNode(value));
            valueList.appendChild(valueField);
        }

        return slotElement;
    }

    protected Element createAuthorField(String authorXDSEntryID, XDSType.Author author) {
        var authorElement = createSchemeClassificationField(authorXDSEntryID, "");
        if ( !author.authorPerson.isEmpty() ) {
            var authorPerson = createSlotField("authorPerson", author.authorPerson);
            authorElement.appendChild(authorPerson);
        }
        if ( !author.authorInstitution.isEmpty() ) {
            var authorInstitution = createSlotField("authorInstitution", author.authorInstitution);
            authorElement.appendChild(authorInstitution);
        }
        if ( !author.authorSpecialty.isEmpty() ) {
            var authorSpecialty = createSlotField("authorSpecialty", author.authorSpecialty);
            authorElement.appendChild(authorSpecialty);
        }

        return authorElement;
    }

    protected int getRandomInt(int min, int max) {
        Random rand = new Random();
        return rand.nextInt((max - min) + 1) + min;
    }

    protected String getCurrentTime() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        return formatter.format(LocalDateTime.now());
    }

    protected String getPatientID() {
        return patientID;
    }


    protected Element createTitle() {
        return createNameField(title);
    }
    protected Element createComments() {
        return createDescriptionField(comments);
    }


    public Document getXmlDocument() {
        return xmlDocument;
    }
}
