/*
 *  CDAParsing.java - DRIMBox
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

package com.bcom.drimbox.xades;

import org.junit.jupiter.api.Test;

import com.bcom.drimbox.document.CDAFile;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class CDAParsing {

    public static final String CDA_TEST_FILE = "DOC00002.xml";

    @Test
    public void testParse() {
        CDAFile f = new CDAFile(new File(ClassLoader.getSystemResource(CDA_TEST_FILE).getPath()));

        // Todo : check with actual values
        assertFalse(f.getConfidentiality().isEmpty());
        assertFalse(f.getPatientID().isEmpty());
        assertFalse(f.getAccessionNumberRoot().isEmpty());
        assertFalse(f.getAuthorInstitution().isEmpty());
        assertFalse(f.getAuthorPerson().isEmpty());
        assertFalse(f.getLegalAuthenticator().isEmpty());
        assertFalse(f.getOrderRoot().isEmpty());
        assertFalse(f.getServiceStartTime().isEmpty());
        assertFalse(f.getServiceStopTime().isEmpty());
        assertFalse(f.getSourcePatientID().isEmpty());
        assertFalse(f.getEventCode().isEmpty());
        assertFalse(f.getHealthcareFacilityType().isEmpty());
        assertFalse(f.getPracticeSetting().isEmpty());
        assertFalse(f.getStudyID().isEmpty());
        assertFalse(f.getSourcePatientInfo().isEmpty());


    }
}
