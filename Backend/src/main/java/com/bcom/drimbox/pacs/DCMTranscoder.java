
/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Agfa Healthcare.
 * Portions created by the Initial Developer are Copyright (C) 2011
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package com.bcom.drimbox.pacs;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Fragments;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.imageio.codec.Compressor;
import org.dcm4che3.imageio.codec.Decompressor;
import org.dcm4che3.imageio.codec.TransferSyntaxType;
import org.dcm4che3.io.DicomEncodingOptions;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.Property;
import org.dcm4che3.util.SafeClose;

import io.quarkus.logging.Log;


public class DCMTranscoder {

	private String tsuid;
	private String ins;
	private TransferSyntaxType tstype;
	private boolean retainfmi;
	private boolean nofmi;
	private DicomEncodingOptions encOpts = DicomEncodingOptions.DEFAULT;
	private final List<Property> params = new ArrayList<>();
	private int maxThreads = 1;


	public final void setTransferSyntax(String uid, String ins) {
		this.ins = ins;
		this.tsuid = uid;
		this.tstype = TransferSyntaxType.forUID(uid);
		if (tstype == null) {
			throw new IllegalArgumentException(
					"Unsupported Transfer Syntax: " + tsuid);
		}
	}

	public final void setRetainFileMetaInformation(boolean retainfmi) {
		this.retainfmi = retainfmi;
	}

	public final void setWithoutFileMetaInformation(boolean nofmi) {
		this.nofmi = nofmi;
	}


	public final void setEncodingOptions(DicomEncodingOptions encOpts) {
		this.encOpts = encOpts;
	}

	public void addCompressionParam(String name, Object value) {
		params.add(new Property(name, value));
	}

	public void setMaxThreads(int maxThreads) {
		if (maxThreads <= 0)
			throw new IllegalArgumentException("max-threads: " + maxThreads);
		this.maxThreads = maxThreads;
	}


	public ByteArrayOutputStream transcode(InputStream srcList) throws InterruptedException {
		ExecutorService executorService = maxThreads > 1 ? Executors.newFixedThreadPool(maxThreads) : null;
		if (executorService != null) {
			executorService.shutdown();
		}
		return transcode(srcList, executorService);
	}

	private ByteArrayOutputStream transcode(final InputStream src, Executor executer) {
		if (executer != null) {
			executer.execute(() -> doTranscoding(src));
		} else {
			return doTranscoding(src);
		}
		return null;
	}

	private ByteArrayOutputStream doTranscoding(InputStream src) {
		try {
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			Attributes fmi;
			Attributes dataset;
			DicomInputStream dis = new DicomInputStream(src);
			try (dis) {
				dis.setIncludeBulkData(IncludeBulkData.URI);
				fmi = dis.readFileMetaInformation();
				dataset = dis.readDataset();
				Sequence otherPatientIDsSequence = dataset.newSequence(Tag.OtherPatientIDsSequence, 1);
				Attributes seqOthers = new Attributes();
				seqOthers.setString(Tag.PatientID, VR.LO, this.ins);
				seqOthers.setString(Tag.TypeOfPatientID, VR.CS, "TEXT");

				Sequence issuerOfPatientIDQualifiersSequences = seqOthers.newSequence(Tag.IssuerOfPatientIDQualifiersSequence, 1);
				Attributes seqIssuers = new Attributes();
				seqIssuers.setString(Tag.UniversalEntityID, VR.UT, "1.2.250.1.213.1.4.10");
				seqIssuers.setString(Tag.UniversalEntityIDType, VR.CS, "ISO");
				issuerOfPatientIDQualifiersSequences.add(seqIssuers);
				otherPatientIDsSequence.add(seqOthers);
							}
			Object pixeldata = dataset.getValue(Tag.PixelData);
			Compressor compressor = null;
			DicomOutputStream dos = null;
			try {
				String tsuid1 = this.tsuid;
				if (pixeldata != null) {
					if (tstype.isPixeldataEncapsulated()) {
						tsuid1 = adjustTransferSyntax(tsuid1,
								dataset.getInt(Tag.BitsStored, 8));
						compressor = new Compressor(dataset, dis.getTransferSyntax());
						compressor.compress(tsuid1, params.toArray(new Property[params.size()]));
					} else if (pixeldata instanceof Fragments)
						Decompressor.decompress(dataset, dis.getTransferSyntax());
				}
				if (nofmi)
					fmi = null;
				else if (retainfmi && fmi != null)
					fmi.setString(Tag.TransferSyntaxUID, VR.UI, tsuid1);
				else
					fmi = dataset.createFileMetaInformation(tsuid1);
				
				dos = new DicomOutputStream(output, tsuid1);
				dos.setEncodingOptions(encOpts);
				dos.writeDataset(fmi, dataset);
			} finally {
				SafeClose.close(compressor);
				SafeClose.close(dos);
			}
			return output;
		} catch (Exception e) {
			e.printStackTrace(System.out);
		}
		return null;
	}


	private String adjustTransferSyntax(String tsuid, int bitsStored) {
		switch (tstype) {
		case JPEG_BASELINE:
			if (bitsStored > 8)
				return UID.JPEGExtended12Bit;
			break;
		case JPEG_EXTENDED:
			if (bitsStored <= 8)
				return UID.JPEGBaseline8Bit;
			break;
		default:
		}
		return tsuid;
	}

}