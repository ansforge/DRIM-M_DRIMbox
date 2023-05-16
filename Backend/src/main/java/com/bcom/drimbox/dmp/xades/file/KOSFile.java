/*
 *  KOSFile.java - DRIMBox
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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;

import io.quarkus.logging.Log;

public class KOSFile {
	byte[] fileContent;


	String studyUID;

	String patientINS;

	public static class SeriesInfo {
		public String retrieveURL;
		public List<String> instancesUID = new ArrayList<>();
	}
	// StudyID => { SeriesID => RetrieveURL, SeriesID => RetrieveURL,... }
	Map<String, SeriesInfo> seriesURL = new HashMap<>();

	// This is used for testing purpose only for now.
	@Deprecated
	public KOSFile(File file) {
		try {
			fileContent = Files.readAllBytes(file.toPath());
			parseKOS(new DicomInputStream(file));
		} catch (IOException e) {
			throw new IllegalStateException("could not read file " + file, e);
		}
	}

	public KOSFile(byte[] rawData) {
		fileContent = rawData;
		try {
			parseKOS(new DicomInputStream(new ByteArrayInputStream(fileContent)));
		} catch (IOException e) {
			throw new RuntimeException("cannot create input stream : " + e.getMessage());
		}

	}

	private void parseKOS(DicomInputStream dis) {
		try {
			Attributes attributes = dis.readDataset();

			patientINS = attributes.getString(Tag.PatientID);

			studyUID = attributes.getString(Tag.StudyInstanceUID);
			//            CurrentRequestedProcedureEvidenceSequence :                               // allStudies
			//              - Item #0                                                               // currentItem
			//                  - ReferencedSeriesSequence :                                        // currentSerieSeq
			//                      - Item #0                                                       // currentSerieInfo
			//                          - RetrieveURL
			//                          - ReferencedSOPSequence
			//								- ReferencedSOPClassUID : "1.2.840.10008.5.1.4.1.1.2" [ CT Image Storage ]
			//								- ReferencedSOPInstanceUID : "1.2.276.0.7230010.3.1.4.8323329.4426.1666943459.930423"
			//                          - SeriesInstanceUID
			Sequence allStudies = attributes.getSequence(Tag.CurrentRequestedProcedureEvidenceSequence);
			if (allStudies == null) {
				Log.error("Can't get CurrentRequestedProcedureEvidenceSequence from KOS");
				return;
			}

			if (allStudies.size() !=1) {
				Log.error("There should be only 1 study in the sequence");
				return;
			}
			// We make sure before that we only get one study
			Attributes currentItem = allStudies.get(0);

			Sequence currentSerieSeq = currentItem.getSequence(Tag.ReferencedSeriesSequence);
			if (currentSerieSeq == null) {
				Log.warn("Can't get CurrentRequestedProcedureEvidenceSequence from KOS");
				return;
			}

			for (Attributes currentSerieInfo : currentSerieSeq) {
				SeriesInfo seriesInfo = new SeriesInfo();

				Sequence instanceUIDSequence = currentSerieInfo.getSequence(Tag.ReferencedSOPSequence);
				for (Attributes instanceUIDObject : instanceUIDSequence) {
					String currentInstanceUID = instanceUIDObject.getString(Tag.ReferencedSOPInstanceUID);
					if (currentInstanceUID == null) {
						Log.warn("Can't get ReferencedSOPInstanceUID in ReferencedSOPSequence.");
					} else {
						seriesInfo.instancesUID.add(currentInstanceUID);
					}
				}

				seriesInfo.retrieveURL = currentSerieInfo.getString(Tag.RetrieveURL);

				seriesURL.put(
						currentSerieInfo.getString(Tag.SeriesInstanceUID),
						seriesInfo
				);
			}

			dis.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public String getStudyUID() {
		return studyUID;
	}
	public String getPatientINS() {
		return patientINS;
	}

	/**
	 * A map that contains data associated with the series in the KOS
	 * @return { seriesUID => SeriesInfo, ... }
	 */
	public Map<String, SeriesInfo> getSeriesInfo() {
		return seriesURL;
	}

	public byte[] getB64RawData() {
		return Base64.getEncoder().encode(fileContent);
	}

	public byte[] getRawData() {
		return fileContent;
	}



}
