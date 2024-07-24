/*
 *  XadesSign.java - DRIMBox
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

import com.bcom.drimbox.dmp.security.DMPKeyStore;

import nu.xom.canonical.Canonicalizer;
import org.joda.time.format.ISODateTimeFormat;
import nu.xom.*;

import jakarta.enterprise.inject.spi.CDI;
import java.io.*;
import java.security.*;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.*;

public class XadesSign {

    DMPKeyStore keyStore;
    PrivateKey signatureKey;
    X509Certificate certificate;

    Document xadesFile;

    public XadesSign() {
        keyStore = CDI.current().select(DMPKeyStore.class).get();

        var pkEntry = keyStore.privateKeySignature();
        signatureKey = pkEntry.getPrivateKey();
        certificate = (X509Certificate) pkEntry.getCertificate();

    }

    Map<DocumentEntry, byte[]> fileReferences = new HashMap<>();
    public void addDocument(DocumentEntry documentEntry, byte[] fileData) {
        fileReferences.put(documentEntry, fileData);
    }

    private final String ID_MANIFEST = "IHEManifest";
    private final String ID_SIGNED_PROPERTIES = "S0-SignedProperties";

    private final String TRANSFORM_ALGORITHM = Canonicalizer.CANONICAL_XML_WITH_COMMENTS;


    private final String XMLDSIG_NAMESPACE = "http://www.w3.org/2000/09/xmldsig#";
    private final String XADES_NAMESPACE = "http://uri.etsi.org/01903/v1.1.1#";

    public String sign(String submissionSetID, String signatureDocumentEntryID)  {
        // Implementation note :
        // We are using XOM instead of the default XML library because we need proper namespace management
        // + proper canonicalization

        // Create root element
        var signatureNode = new Element("Signature", XMLDSIG_NAMESPACE);

        signatureNode.addAttribute( new Attribute("Id", signatureDocumentEntryID) );
        xadesFile = new Document(signatureNode);


        // Todo : SignedInfo fonction
        var signedInfoNode = new Element("SignedInfo" , XMLDSIG_NAMESPACE);
        signatureNode.appendChild(signedInfoNode);

        var canonicalNode = new Element("CanonicalizationMethod", XMLDSIG_NAMESPACE);
        canonicalNode.addAttribute( new Attribute("Algorithm", TRANSFORM_ALGORITHM));
        signedInfoNode.appendChild(canonicalNode);

        var signatureMethodNode = new Element("SignatureMethod", XMLDSIG_NAMESPACE);
        signatureMethodNode.addAttribute( new Attribute("Algorithm", "http://www.w3.org/2000/09/xmldsig#rsa-sha1"));
        signedInfoNode.appendChild(signatureMethodNode);

        var manifestDigestValueNode = addReferenceNode(signedInfoNode, "#" + ID_MANIFEST,
                "http://www.w3.org/2000/09/xmldsig#Manifest", "", "");

        var signedPropertiesDigestValueNode = addReferenceNode(signedInfoNode, "#" + ID_SIGNED_PROPERTIES,
                "http://uri.etsi.org/01903/v1.1.1#SignedProperties", "", "");
       //  End SignInfo


        // Signature Value
        var signatureValueNode = new Element("SignatureValue", XMLDSIG_NAMESPACE);
        signatureNode.appendChild(signatureValueNode);
        // End Signature Value



        // Key Info
        var keyInfoNode = new Element("KeyInfo", XMLDSIG_NAMESPACE);
        var x509DataNode = new Element("X509Data", XMLDSIG_NAMESPACE);
        var x509CertificateNode = new Element("X509Certificate", XMLDSIG_NAMESPACE);
        signatureNode.appendChild(keyInfoNode);
        keyInfoNode.appendChild(x509DataNode);
        x509DataNode.appendChild(x509CertificateNode);
        try {
            String x509b64Encoded = java.util.Base64.getEncoder().encodeToString(certificate.getEncoded());
            x509CertificateNode.appendChild(new Text(x509b64Encoded));
        } catch (CertificateEncodingException e) {
            throw new RuntimeException(e);
        }

        // End Key Info

        // Object Signature properties
        var objectSignatureNode = new Element("Object", XMLDSIG_NAMESPACE);
        signatureNode.appendChild(objectSignatureNode);

        var signaturePropertiesNode = new Element("SignatureProperties", XMLDSIG_NAMESPACE);
        objectSignatureNode.appendChild(signaturePropertiesNode);

        var signaturePropertyNode = new Element("SignatureProperty", XMLDSIG_NAMESPACE);
        signaturePropertiesNode.appendChild(signaturePropertyNode);
        signaturePropertyNode.addAttribute( new Attribute("Id", "purposeOfSignature"));
        signaturePropertyNode.addAttribute( new Attribute("Target", "#" + signatureDocumentEntryID));
        signaturePropertyNode.appendChild(new Text("1.2.840.10065.1.12.1.14"));
        //End Object Signature properties


        //Object manifest
        var manifestObjectNode = new Element("Object", XMLDSIG_NAMESPACE);
        signatureNode.appendChild(manifestObjectNode);
        var manifestNode = new Element("Manifest", XMLDSIG_NAMESPACE);
        // todo: move this to the top
        String xsiNamespace = "http://www.w3.org/2001/XMLSchema-instance";
        manifestNode.addNamespaceDeclaration("xsi", xsiNamespace);
        manifestNode.addAttribute( new Attribute("Id", "IHEManifest"));
        manifestNode.addAttribute( new Attribute("xsi:schemaLocation", xsiNamespace, "http://www.w3.org/2000/09/xmldsig# http://docs.oasis-open.org/security/saml/v2.0/saml-schema-assertion-2.0.xsd"));
        manifestObjectNode.appendChild(manifestNode);
        //End Object manifest

        // Ref objects
        addReferenceNode(manifestNode, "urn:oid:" + submissionSetID, "", "AA==", "");
        for(var fileEntry: fileReferences.entrySet()) {
            DocumentEntry currentDocumentEntry = fileEntry.getKey();
            byte[] currentFile = fileEntry.getValue();

            String transformAlgorithm;
            switch (currentDocumentEntry.getFileType()) {
                case KOS:
                    transformAlgorithm = "";
                    break;
                case CDA:
                    // The canonicalization is done in CDAFile class
                    transformAlgorithm = TRANSFORM_ALGORITHM;
                    break;
                default:
                    throw new IllegalArgumentException("Unexpected document entry type");
            }

            addReferenceNode(manifestNode, "urn:oid:" + currentDocumentEntry.getUniqueID(), "", digestSha1(currentFile), transformAlgorithm);
        }
        
        // Ref objects
        var qualifyingPropertiesObjectNode = new Element("Object", XMLDSIG_NAMESPACE);
        signatureNode.appendChild(qualifyingPropertiesObjectNode);

        var qualifyingPropertiesNode = new Element("QualifyingProperties", XADES_NAMESPACE);
        qualifyingPropertiesNode.addAttribute( new Attribute("Target", "#" + signatureDocumentEntryID));
        qualifyingPropertiesObjectNode.appendChild(qualifyingPropertiesNode);

        var signedPropertiesNode = new Element("SignedProperties", XADES_NAMESPACE);
        signedPropertiesNode.addNamespaceDeclaration("xsi","http://www.w3.org/2001/XMLSchema-instance");
        signedPropertiesNode.addAttribute( new Attribute("Id", ID_SIGNED_PROPERTIES));
        signedPropertiesNode.addAttribute( new Attribute("xsi:schemaLocation", xsiNamespace, "http://uri.etsi.org/01903/v1.1.1# http://docs.oasis-open.org/security/saml/v2.0/saml-schema-assertion-2.0.xsd"));
        qualifyingPropertiesNode.appendChild(signedPropertiesNode);

        var signedSignatureProperties = new Element("SignedSignatureProperties", XADES_NAMESPACE);
        signedPropertiesNode.appendChild(signedSignatureProperties);

        var signingTimeNode = new Element("SigningTime", XADES_NAMESPACE);
        String currentTime =  ISODateTimeFormat.dateTime().print(new Date().getTime());
        signingTimeNode.appendChild(new Text(currentTime));
        signedSignatureProperties.appendChild(signingTimeNode);

        var signingCertificateNode = new Element("SigningCertificate", XADES_NAMESPACE);
        signedSignatureProperties.appendChild(signingCertificateNode);

        // Cert
        var certNode = new Element("Cert", XADES_NAMESPACE);
        signingCertificateNode.appendChild(certNode);

        var certNodeDigest = new Element("CertDigest", XADES_NAMESPACE);
        certNode.appendChild(certNodeDigest);

        var digestMethodeNode = new Element("DigestMethod", XADES_NAMESPACE);
        digestMethodeNode.addAttribute( new Attribute("Algorithm", "http://www.w3.org/2000/09/xmldsig#sha1"));
        certNodeDigest.appendChild(digestMethodeNode);

        var digestValueNode = new Element("DigestValue", XADES_NAMESPACE);
        try {
            digestValueNode.appendChild(new Text(digestSha1(certificate.getEncoded())));
        } catch (CertificateEncodingException e) {
            throw new RuntimeException(e);
        }
        certNodeDigest.appendChild(digestValueNode);

        var issuerSerialNode = new Element("IssuerSerial", XADES_NAMESPACE);
        certNode.appendChild(issuerSerialNode);

        var x509IssuerNameNode = new Element("X509IssuerName", XMLDSIG_NAMESPACE);
        x509IssuerNameNode.appendChild(new Text( keyStore.getIssuerDN() ));
        issuerSerialNode.appendChild(x509IssuerNameNode);


        var x509SerialNumberNode = new Element("X509SerialNumber", XMLDSIG_NAMESPACE);
        x509SerialNumberNode.appendChild(new Text( certificate.getSerialNumber().toString() ));
        issuerSerialNode.appendChild(x509SerialNumberNode);
        // End CERT

        var signaturePolicyIdentifierNode = new Element("SignaturePolicyIdentifier", XADES_NAMESPACE);
        signedSignatureProperties.appendChild(signaturePolicyIdentifierNode);

        var signaturePolicyImpliedNode = new Element("SignaturePolicyImplied", XADES_NAMESPACE);
        signaturePolicyIdentifierNode.appendChild(signaturePolicyImpliedNode);

        var signedDataObjectPropertiesNode = new Element("SignedDataObjectProperties", XADES_NAMESPACE);
        signedPropertiesNode.appendChild(signedDataObjectPropertiesNode);

        // END Object QualifiingPorpoerties

        // Manifest digest
        String manifestNodeDigest = digestNode(manifestNode);
        manifestDigestValueNode.appendChild(new Text(manifestNodeDigest));

        // Manifest Signed properties
        String signedPropertiesDigest = digestNode(signedPropertiesNode);
        signedPropertiesDigestValueNode.appendChild(new Text(signedPropertiesDigest));


        try {
            Signature signature = Signature.getInstance("SHA1withRSA");
            signature.initSign(signatureKey);

            // First canonicalize the node
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            Canonicalizer c  = new Canonicalizer(os, TRANSFORM_ALGORITHM);
            c.write(signedInfoNode);

            // Sign the contents
            signature.update(os.toByteArray());
            byte[] signatureValue = signature.sign();
            String signatureB64 = Base64.getEncoder().encodeToString(signatureValue);
            signatureValueNode.appendChild(new Text(signatureB64));

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        return xadesFile.toXML();
    }

    private String digestNode(Node node) {
        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            Canonicalizer c  = new Canonicalizer(os, nu.xom.canonical.Canonicalizer.CANONICAL_XML_WITH_COMMENTS);
            c.write(node);
            return digestSha1(os.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
    }

    /**
     * Create and add a reference node to parentNode
     * @param parentNode ParentNode of the reference node
     * @param uri Reference URI
     * @param referenceType Can be empty. Will be set at the Type attribute.
     * @param digestValue Can be empty. Digest value of the reference.
     * @param transformAlgorithm Can be empty. Set the transform algorithm
     * @return Return the digestValueNode so you can fill it later
     */
    private Element addReferenceNode(Element parentNode, String uri, String referenceType, String digestValue, String transformAlgorithm) {
        var referenceNode = new Element("Reference", XMLDSIG_NAMESPACE);
        if (!referenceType.isEmpty())
            referenceNode.addAttribute( new Attribute("Type", referenceType));

        referenceNode.addAttribute( new Attribute("URI", uri));
        parentNode.appendChild(referenceNode);


        if (!transformAlgorithm.isEmpty()) {
            var transformsNode = new Element("Transforms", XMLDSIG_NAMESPACE);
            referenceNode.appendChild(transformsNode);

            var transformNode = new Element("Transform", XMLDSIG_NAMESPACE);
            transformNode.addAttribute( new Attribute("Algorithm", transformAlgorithm));
            transformsNode.appendChild(transformNode);
        }


        var digestMethodNode = new Element("DigestMethod", XMLDSIG_NAMESPACE);
        digestMethodNode.addAttribute( new Attribute("Algorithm", "http://www.w3.org/2000/09/xmldsig#sha1"));
        referenceNode.appendChild(digestMethodNode);

        var digestValueNode = new Element("DigestValue", XMLDSIG_NAMESPACE);
        if (!digestValue.isEmpty()) {
            digestValueNode.appendChild(new Text(digestValue));
        }
        referenceNode.appendChild(digestValueNode);

        return digestValueNode;
    }


    private String digestSha1(byte[] value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA1");
            byte[] digest = md.digest(value);
            return java.util.Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
    }




}
