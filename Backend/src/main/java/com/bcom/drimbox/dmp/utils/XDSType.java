/*
 *  XadesType.java - DRIMBox
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

import java.nio.charset.StandardCharsets;

public class XDSType {

    public static class ClassificationCode {
        public String code;
        public String displayName;
        public String codingScheme;

        public ClassificationCode(String code, String displayName, String codingScheme) {
            this.code = code;
            // Todo : see if needed for all the fields ?
            // Prevent encoding errors
            this.displayName = new String(displayName.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            this.codingScheme = codingScheme;
        }

        public Boolean isEmpty() {
            return code.isEmpty() || displayName.isEmpty() || codingScheme.isEmpty();
        }

        @Override
        public String toString() {
            return "ClassificationCode{" +
                    "code='" + code + '\'' +
                    ", displayName='" + displayName + '\'' +
                    ", codingScheme='" + codingScheme + '\'' +
                    '}';
        }
    }

    public static class Author {
        public String authorInstitution;
        public String authorPerson;
        public String authorRole;
        public String authorSpecialty;

        public Author(String authorInstitution, String authorPerson, String authorRole, String authorSpecialty) {
            this.authorInstitution = authorInstitution;
            this.authorPerson = authorPerson;
            this.authorRole = authorRole;
            this.authorSpecialty = authorSpecialty;
        }
    }

    public static class SourcePatientInfo {
        public String PID5;
        public String PID7;
        public String PID8;

        public SourcePatientInfo(String PID5, String PID7, String PID8) {
            this.PID5 = PID5;
            this.PID7 = PID7;
            this.PID8 = PID8;
        }

        public boolean isEmpty() {
            return PID5.isEmpty() || PID7.isEmpty() || PID8.isEmpty();
        }
    }
}
