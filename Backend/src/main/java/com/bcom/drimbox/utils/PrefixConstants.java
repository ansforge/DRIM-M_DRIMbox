/*
 *  PrefixConstants.java - DRIMBox
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

package com.bcom.drimbox.utils;

/**
 * Collection of constants that are defined in path of Pacs and Drimbox requests
 */
public final class PrefixConstants {

    private PrefixConstants() {}

    /**
     * Drimbox requests prefix
     */
    public static final String DRIMBOX_PREFIX = "drimbox";
    /**
     * Studies prefix (as defined in the dicom standard)
     */
    public static final String STUDIES_PREFIX = "studies";
    /**
     * Series prefix (as defined in the dicom standard)
     */
    public static final String SERIES_PREFIX = "series";
    /**
     * Metadata prefix (as defined in the dicom standard)
     */
    public static final String METADATA_PREFIX = "metadata";

}
