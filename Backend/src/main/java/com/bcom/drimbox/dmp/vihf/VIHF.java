/*
 *  VIHF.java - DRIMBox
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

package com.bcom.drimbox.dmp.vihf;

import com.bcom.drimbox.dmp.security.DMPKeyStore;
import org.joda.time.DateTime;
import org.opensaml.Configuration;
import org.opensaml.DefaultBootstrap;
import org.opensaml.common.SAMLVersion;
import org.opensaml.saml2.core.*;
import org.opensaml.saml2.core.impl.*;
import org.opensaml.xml.ConfigurationException;
import org.opensaml.xml.Namespace;
import org.opensaml.xml.XMLObject;
import org.opensaml.xml.XMLObjectBuilder;
import org.opensaml.xml.io.Marshaller;
import org.opensaml.xml.io.MarshallerFactory;
import org.opensaml.xml.io.MarshallingException;
import org.opensaml.xml.schema.XSAny;
import org.opensaml.xml.security.SecurityException;
import org.opensaml.xml.security.credential.Credential;
import org.opensaml.xml.security.x509.BasicX509Credential;
import org.opensaml.xml.security.x509.X509KeyInfoGeneratorFactory;
import org.opensaml.xml.signature.KeyInfo;
import org.opensaml.xml.signature.SignatureConstants;
import org.opensaml.xml.signature.SignatureException;
import org.opensaml.xml.signature.impl.SignatureBuilder;
import org.w3c.dom.Element;

import jakarta.enterprise.inject.spi.CDI;
import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 *
 */
public class VIHF extends VIHFBase {

    private Assertion assertion;
    // Builder for assertions
    private final AssertionBuilder assertionBuilder = new AssertionBuilder();

    // Builder for attribute statement in SAML
    private final AttributeStatementBuilder attributeStatementBuilder = new AttributeStatementBuilder();

    // Builder for attributes
    private final AttributeBuilder attributeBuilder = new AttributeBuilder();

    private AttributeStatement attributeStatement;


    public VIHF() {
        // Without it it doesn't find builder classes
        try {
            DefaultBootstrap.bootstrap();
        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Convert VIHF.AuthContext to OpenSAML.AuthnContext
     * @return OpenSAML.AuthnContext
     */
    private String getAuthnContext() {
        switch(authContext) {
            case TLS:
                return AuthnContext.TLS_CLIENT_AUTHN_CTX;
            default:
                throw new RuntimeException("Missing OpenSAML.AuthnContext <-> VIHF.AuthContext conversion");
        }
    }

    /**
     * Create new Saml object based on given name
     * @param qname Node name
     * @return XML object to be converted to node class
     */
    private XMLObject createSamlObject(QName qname) {
        return Configuration.getBuilderFactory().getBuilder(qname).buildObject(
                qname);
    }

    /**
     * Get Credential SAML object based on dmpKeyStore
     * @param dmpKeyStore DMPKeyStore instance (e.g. DMPKeyStore dmpKeyStore = CDI.current().select(DMPKeyStore.class).get();)
     * @return Credential SAML object
     */
    private Credential getSigningCredential(DMPKeyStore dmpKeyStore) {
        // Signing
        KeyStore.PrivateKeyEntry pkEntry = dmpKeyStore.privateKeySignature();
        PrivateKey pk = pkEntry.getPrivateKey();

        X509Certificate certificate = (X509Certificate) pkEntry.getCertificate();
        // Set up credentials
        BasicX509Credential credential = new BasicX509Credential();
        credential.setEntityCertificate(certificate);
        credential.setPrivateKey(pk);
        credential.setPublicKey(certificate.getPublicKey());

        return credential;
    }


    /**
     * Current sign implementation
     */
    @Override
    public void sign() {
        // Get DMPKeyStore class
        // https://stackoverflow.com/a/61154707/7231626
        DMPKeyStore dmpKeyStore = CDI.current().select(DMPKeyStore.class).get();
        Credential signingCredential = getSigningCredential(dmpKeyStore);

        SignatureBuilder signatureBuilder = new SignatureBuilder();
        org.opensaml.xml.signature.Signature signature = signatureBuilder.buildObject();

        signature.setSigningCredential(signingCredential);
        signature.setSignatureAlgorithm(SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA1);
        signature.setCanonicalizationAlgorithm(SignatureConstants.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);
        signature.setKeyInfo(getKeyInfo(signingCredential));

        assertion.setSignature(signature);

        try {
            marshallIntoXML();
            org.opensaml.xml.signature.Signer.signObject(assertion.getSignature());
        } catch (SignatureException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get KeyInfo saml node based on Credential object
     * @param credential OpenSAML credential object
     * @return KeyInfo saml node
     */
    private KeyInfo getKeyInfo(Credential credential) {
        KeyInfo keyinfo;
        X509KeyInfoGeneratorFactory factory;
        factory = new X509KeyInfoGeneratorFactory();
        factory.setEmitEntityCertificate(true);
        // Creating a KeyInfo object and attaching to the Signature object is
        // mandatory for the user certificate to appear at the signature.
        try {
            keyinfo = factory.newInstance().generate(credential);
        } catch (SecurityException e) {
            throw new RuntimeException(e);
        }
        return keyinfo;
    }

    /**
     * Put all the information in Assertion in the XML Java Node
     * If you don't call this function getDom() will not be updated
     */
    private void marshallIntoXML() {
        // Marshall Assertion Java class into XML
        MarshallerFactory marshallerFactory = Configuration.getMarshallerFactory();
        Marshaller marshaller = marshallerFactory.getMarshaller(assertion);

        try {
            marshaller.marshall(assertion);
        } catch (MarshallingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Build all fields in  {@code <saml2:Assertion></saml2:Assertion>} (excluding signature and saml2:AttributeStatement)
     */
    private void buildAssertionFields() {
        // Create new assertion
        assertion = assertionBuilder.buildObject();

        DateTime currentTime = new DateTime();

        // Set assertion statics field
        // Base namespace
        assertion.getNamespaceManager().registerNamespace(
                new Namespace(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "xsi"));

        // Set version saml version
        assertion.setVersion(SAMLVersion.VERSION_20);

        // ID
        assertion.setID(UUID.randomUUID().toString());

        // Current time
        assertion.setIssueInstant(currentTime);

        // Issuer
        Issuer issuer = (Issuer) createSamlObject(Issuer.DEFAULT_ELEMENT_NAME);
        issuer.setFormat("urn:oasis:names:tc:SAML:1.1:nameid-format:X509SubjectName");
        issuer.setValue(getIssuer());
        assertion.setIssuer(issuer);

        // Authnstatement (the date will be filled when building the VIHF)
        AuthnStatementBuilder authnStatementBuilder = new AuthnStatementBuilder();
        AuthnStatement authnStatement = authnStatementBuilder.buildObject();
        AuthnContextBuilder acb = new AuthnContextBuilder();
        AuthnContext myACI = acb.buildObject();
        AuthnContextClassRefBuilder accrb = new AuthnContextClassRefBuilder();
        AuthnContextClassRef accr = accrb.buildObject();
        // Authncontext
        accr.setAuthnContextClassRef(getAuthnContext());
        myACI.setAuthnContextClassRef(accr);
        // Current time
        authnStatement.setAuthnInstant(currentTime);

        // Only in AIR mode
        if (this.vihfSimpleAttributes.containsKey(VIHFField.AUTHENTIFICATION_MODE)
                && this.vihfSimpleAttributes.get(VIHFField.AUTHENTIFICATION_MODE).equals(AuthentificationMode.AIR.toString())) {
            AuthnContextDeclBuilder acdb = new AuthnContextDeclBuilder();
            AuthnContextDecl acd = acdb.buildObject();
            acd.setTextContent("APP_BROWSER_AUTH");
            myACI.setAuthnContextDecl(acd);
        }

        authnStatement.setAuthnContext(myACI);
        assertion.getAuthnStatements().add(authnStatement);

        // Name ID
        Subject subj = (Subject) createSamlObject(Subject.DEFAULT_ELEMENT_NAME);
        NameID nameId = (NameID) createSamlObject(NameID.DEFAULT_ELEMENT_NAME);
        nameId.setValue(getNameID());
        subj.setNameID(nameId);
        assertion.setSubject(subj);
    }

    /**
     * Build all fields in {@code <saml2:AttributeStatement></saml2:AttributeStatement>}
     */
    public void buildVIHFFields() {
        attributeStatement = attributeStatementBuilder.buildObject();

        // Build simple fields
        for ( Map.Entry<VIHFField, String> entry : vihfSimpleAttributes.entrySet() ) {
            VIHFField vihfField = entry.getKey();
            String value = entry.getValue();
            addSamlAttribute(vihfField, value);
        }

        addSamlCommonCodeAttribute("PurposeOfUse", VIHFField.PURPOSE_OF_USE, purposeOfUse);
        addSamlCommonCodeAttribute("Role", VIHFField.ROLE, roles);

        assertion.getAttributeStatements().add(attributeStatement);
    }

    /**
     * Add CommonCodeAttribute to the VHIF
     * @param samlFieldName Node name ({@code <saml2:Attribute><saml2:AttributeValue><samlFieldName/></saml2:AttributeValue></saml2:Attribute>})
     * @param vihfField VIHF field ({@code <saml2:Attribute Name="vihfField">...</saml2:Attribute>})
     * @param commonVIHFAttribute vihf common attribute ({@code <samlFieldName code="xx".../>})
     */
    private void addSamlCommonCodeAttribute(String samlFieldName, VIHFField vihfField, CommonVIHFAttribute commonVIHFAttribute) {
        addSamlCommonCodeAttribute(samlFieldName, vihfField, Collections.singletonList(commonVIHFAttribute));
    }

    /**
     * Add a list of CommonCodeAttribute to the VHIF
     * @param samlFieldName Node name ({@code <saml2:Attribute><saml2:AttributeValue><samlFieldName/></saml2:AttributeValue></saml2:Attribute>})
     * @param vihfField VIHF field ({@code <saml2:Attribute Name="vihfField">...</saml2:Attribute>})
     * @param commonVIHFAttributeList list of vihf common attribute ({@code <samlFieldName code="xx".../>})
     */
    private void addSamlCommonCodeAttribute(String samlFieldName, VIHFField vihfField, List<? extends CommonVIHFAttribute> commonVIHFAttributeList) {
        Attribute attrGroups = attributeBuilder.buildObject();
        attrGroups.setName(vihfField.toString());
        XMLObjectBuilder<XSAny> xsAnyBuilder = Configuration.getBuilderFactory().getBuilder(XSAny.TYPE_NAME);
        for(CommonVIHFAttribute commonCode:commonVIHFAttributeList){
            XSAny xsAnyRole = xsAnyBuilder.buildObject("urn:hl7-org:v3", samlFieldName, null);

            xsAnyRole.getUnknownAttributes().put(new QName(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "type", "xsi"), "CE");
            xsAnyRole.getUnknownAttributes().put(new QName("code"), commonCode.code);
            xsAnyRole.getUnknownAttributes().put(new QName("codeSystem"), commonCode.codeSystem);
            xsAnyRole.getUnknownAttributes().put(new QName("codeSystemName"), commonCode.codeSystemName);
            xsAnyRole.getUnknownAttributes().put(new QName("displayName"), commonCode.displayName);

            XSAny roleAttributeValue = xsAnyBuilder.buildObject(AttributeValue.DEFAULT_ELEMENT_NAME);
            roleAttributeValue.getUnknownXMLObjects().add(xsAnyRole);

            attrGroups.getAttributeValues().add(roleAttributeValue);
        }
        attributeStatement.getAttributes().add(attrGroups);
    }

    /**
     * Create the following structure :
     * <pre>{@code
     * <saml2:Attribute Name="field">
     *  <saml2:AttributeValue>value</saml2:AttributeValue>
     * </saml2:Attribute>
     * }
     * </pre>
     * @param field Field name
     * @param value Attribute value
     */
    private void addSamlAttribute(VIHFField field, String value) {
        Attribute attr = attributeBuilder.buildObject();
        attr.setName(field.toString());
        XMLObjectBuilder<XSAny> xsAnyBuilder = Configuration.getBuilderFactory().getBuilder(XSAny.TYPE_NAME);
        XSAny roleAttributeValue = xsAnyBuilder
                .buildObject(AttributeValue.DEFAULT_ELEMENT_NAME);
        roleAttributeValue.setTextContent(value);
        attr.getAttributeValues().add(roleAttributeValue);
        attributeStatement.getAttributes().add(attr);
    }

    @Override
    public Element getVIHF() {
        return assertion.getDOM();
    }

    @Override
    public Boolean build() {
        buildAssertionFields();
        buildVIHFFields();

        marshallIntoXML();
        return true;
    }
}
