/*
 *  SubmissionSet.java - DRIMBox
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

import com.bcom.drimbox.document.CDAFile;

import static com.bcom.drimbox.dmp.utils.XDSUUID.*;

import java.util.ArrayList;
import java.util.List;

public class SubmissionSet extends BaseElement {


    List<XDSType.Author> authors = new ArrayList<>();

    XDSType.ClassificationCode contentType = new XDSType.ClassificationCode("04", "Hospitalisation de jour", "1.2.250.1.213.2.2");


    String sourceID = "1.2.250.1.999.1.1.8121";


    public SubmissionSet(CDAFile referenceCDA) {
        super();

        // Generate unique ID
        uniqueID = "1.2.250.1.999.1.1.8121." + getRandomInt(1,10) + "." + getRandomInt(0, 10000);
        entryID = "SubmissionSet01";
        title = "submissionset title";
        comments = "submissionset comments";

        authors.add(new XDSType.Author(referenceCDA.getAuthorInstitution(),
                referenceCDA.getAuthorPerson(),
                "",""));

        patientID = referenceCDA.getPatientID();

        var registryPackage = xmlDocument.createElement("RegistryPackage");
        xmlDocument.appendChild(registryPackage);

        // ID
        registryPackage.setAttribute("id", entryID);

        // submissionTime
        registryPackage.appendChild(createSlotField("submissionTime", getCurrentTime()) );

        // Title
        registryPackage.appendChild(createTitle());
        // Comments
        registryPackage.appendChild(createComments());

        // Author
        for(XDSType.Author author : authors) {
            registryPackage.appendChild(createAuthorField(XDSSubmissionSet_author, author));
        }

        // Content type code
        var contentTypeElement =createSchemeClassificationField(XDSSubmissionSet_contentTypeCode, contentType);
        registryPackage.appendChild(contentTypeElement);

        // Classification
        registryPackage.appendChild(createNodeClassificationField(XDSSubmissionSet));

        // External Identifier
        registryPackage.appendChild(createExternalIdentifierField(XDSSubmissionSet_patientId, patientID, "XDSSubmissionSet.patientId"));
        registryPackage.appendChild(createExternalIdentifierField(XDSSubmissionSet_sourceId, sourceID, "XDSSubmissionSet.sourceId"));
        registryPackage.appendChild(createExternalIdentifierField(XDSSubmissionSet_uniqueId, uniqueID, "XDSSubmissionSet.uniqueId"));

    }

}
