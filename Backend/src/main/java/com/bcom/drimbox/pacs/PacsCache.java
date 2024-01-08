/*
 *  PacsCache.java - DRIMBox
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

package com.bcom.drimbox.pacs;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.NotFoundException;

import com.bcom.drimbox.api.DRIMboxConsoAPI;
import com.bcom.drimbox.utils.PrefixConstants;

import com.bcom.drimbox.utils.exceptions.RequestErrorException;
import org.apache.http.HttpHeaders;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.mime.MultipartParser;

import io.quarkus.logging.Log;
import io.vertx.core.Vertx;

@Singleton
public class PacsCache {
	static class DicomCacheInstance {
		// Instance UID => file
		Map<String, byte[]> dicomFiles = new HashMap<>();
		Boolean complete = false;
	}

	// StudyUID =>
	//  - SeriesUID
	//      - InstanceUID
	//      - InstanceUID
	//  - SeriesUID
	//      - InstanceUID
	// StudyUID =>
	//  - SeriesUID
	//  - SeriesUID
	Map<String, Map<String, DicomCacheInstance>> dicomCache = new HashMap<>();
	
	// Boundary for multipart request
	private static final String BOUNDARY = "myBoundary";
	
	private final Vertx vertx;


	@Inject
	public PacsCache(Vertx vertx) {
		this.vertx = vertx;
	}


	public static String getEventBusID(String studyUID, String seriesUID) { return studyUID + "/" + seriesUID; }


	/**
	 * Add new entry to the cache. It will fetch all instances of given study and
	 * series UID to store it in the cache.
	 *
	 * This function is non-blocking and will be executed in another thread
	 *
	 * @param drimboxSourceURL Source drimbox URL
	 * @param accessToken PCS access token that will be verified
	 * @param studyUID Study UID
	 * @param seriesUID Series UID
	 *
	 * @return Return a future that contains the # of instance added to the cache. It might be 0 if the data are already
	 * in the cache. It will raise an RequestErrorException if something goes wrong.
	 */
	public io.vertx.core.Future<Integer> addNewEntry(String drimboxSourceURL, String accessToken, String studyUID, String seriesUID, String sopInstanceUID) {
		// Do not rebuild if already here
		if (dicomCache.containsKey(studyUID) && dicomCache.get(studyUID).containsKey(seriesUID))
			return io.vertx.core.Future.succeededFuture(0);

		io.vertx.core.Future<Integer> future = vertx.executeBlocking(promise -> {
			Log.info("Starting cache build...");
			Log.info("Starting WADO (series) request : " + seriesUID);
			Map<String, DicomCacheInstance> map = new HashMap<>();
			map.put(seriesUID, new DicomCacheInstance());

			if(dicomCache.containsKey(studyUID)) {
				dicomCache.get(studyUID).put(seriesUID, new DicomCacheInstance());
			}
			else {
				dicomCache.put(studyUID, map);
			}
			
			try {
				buildEntry(drimboxSourceURL, accessToken, studyUID, seriesUID, sopInstanceUID);
				// Since something was added we set the # of added items
				promise.complete( dicomCache.get(studyUID).get(seriesUID).dicomFiles.size() );
			} catch (RequestErrorException e) {
				promise.fail(e);
			}
		});

		future.onComplete(status -> {
			if (status.succeeded()) {
				Log.info("Cache created.");
			}
		});

		future.onFailure(e -> {
			Log.error("Cache creation aborted due to an error : " + e.getMessage());
			Log.error(String.format("Removing %s / %s from cache", studyUID, seriesUID));
			// Remove cache since something went wrong
			removeFromCache(studyUID, seriesUID);
		});

		return future;
	}

	private void removeFromCache(String studyUID, String seriesUID) {
		dicomCache.get(studyUID).remove(seriesUID);
		if (dicomCache.get(studyUID).isEmpty()) {
			dicomCache.remove(studyUID);
		}
	}

	// TODO : handle study/series not present

	private DicomCacheInstance getCacheInstance(String studyUID, String seriesUID) {
		if (!dicomCache.containsKey(studyUID))
			return null;

		return dicomCache.get(studyUID).get(seriesUID);
	}

	/**
	 * Return first instance number of studyUID/seriesUID
	 *
	 * This is used for retrieved metadata in OHIFv3 and will be gone as soon as we find another working solution
	 *
	 * @param studyUID Study UID
	 * @param seriesUID Series UID
	 * @return first instance number of studyUID/seriesUID or null if not present
	 */
	public String getFirstInstanceNumber(String studyUID, String seriesUID) {
		if (!dicomCache.containsKey(studyUID) || !dicomCache.get(studyUID).containsKey(seriesUID) )
			return null;

		return dicomCache.get(studyUID).get(seriesUID).dicomFiles.keySet().stream().findFirst().get();
	}

	Map<String, CompletableFuture<byte[]>> waitingFutures = new HashMap<>();
	/**
	 * Get dicom file in cache.
	 *
	 * If it is not available it will wait for the buildCache to emit the requested
	 * file.
	 *
	 * @param studyUID Study UID
	 * @param seriesUID Series UID
	 * @param instanceUID Instance UID
	 *
	 * @return Dicom file corresponding to the UIDs.
	 * It may not be available right away as the cache can take some time to be built.
	 */
	public Future<byte[]> getDicomFile(String studyUID, String seriesUID, String instanceUID) {
		CompletableFuture<byte[]> completableFuture = new CompletableFuture<>();

		DicomCacheInstance instance = getCacheInstance(studyUID, seriesUID);
		if (instance == null) {
			Log.error(String.format("No instance found for %s / %s / %s ", studyUID, seriesUID, instanceUID));
			return CompletableFuture.failedFuture(new NotFoundException());
		}

		if (instance.dicomFiles.containsKey(instanceUID)) {
			Log.info("[CACHE] Available " + instanceUID);
			completableFuture.complete(instance.dicomFiles.get(instanceUID));
		} else {
			Log.info("[CACHE] Waiting for : " + instanceUID);
			waitingFutures.put(instanceUID, completableFuture);
		}

		return completableFuture;
	}

	/**
	 * Checks if some instance UID are still waiting and mark them a not found. This will not affect valid cached images.
	 * It also set all instanceUID that are not already to an empty image marking them as not found.
	 *
	 * This function is useful if the cache entry number is != from the KOS entry number. It means that all images
	 * in the KOS that are not in the cache are not in the pacs and we need to mark them a "not found" (empty image)
	 */
	public void markInstanceAsNotFound(String studyUID, String seriesUID, List<String> instanceUIDs) {
		// Check if some images are already pending
		for (String instanceUID : instanceUIDs) {
			if (waitingFutures.containsKey(instanceUID)) {
				waitingFutures.get(instanceUID).complete(new byte[0]);
			}
		}

		// For all images that are in the instanceUIDs but not in valid images, we make them empty so we can return an
		// error code.
		if (dicomCache.containsKey(studyUID) && dicomCache.get(studyUID).containsKey(seriesUID)) {
			var validImages = dicomCache.get(studyUID).get(seriesUID).dicomFiles;

			Set<String> instanceSet = new HashSet<>(instanceUIDs);
			instanceSet.removeAll( validImages.keySet());

			for(String instanceUID : instanceSet) {
				validImages.put(instanceUID, new byte[0]);
			}
		}
	}


	private interface BoundaryFunc { String getBoundary(String contentType); }
	private void buildEntry(String drimboxSourceURL, String accessToken, String studyUID, String seriesUID, String sopInstanceUID) throws RequestErrorException {
		String serviceURL = drimboxSourceURL + "/" + PrefixConstants.STUDIES_PREFIX + "/" + studyUID + "/series/" + seriesUID;

		// TODO : Compatibility with OHIFv2 and OHIFv3 without KOS (need to remove this asap)
		if (!drimboxSourceURL.startsWith("http")) {
			serviceURL = DRIMboxConsoAPI.HTTP_PROTOCOL + serviceURL;
		}

		try {
			Map<String, String> transferSyntaxes = Map.of(UID.JPEGBaseline8Bit, "0.9",
					UID.JPEGExtended12Bit, "0.8",
					UID.JPEG2000, "0.8",
					UID.JPEGLosslessSV1, "0.7",
					UID.JPEGLSLossless, "0.6",
					UID.ExplicitVRLittleEndian, "0.5",
					UID.MPEG2MPML, "0.4",
					UID.MPEG2MPHL, "0.3",
					UID.MPEG4HP41, "0.3",
					UID.MPEG4HP41BD, "0.3");
			//final URL url = new URL("http://172.19.45.196:4200/api/source/studies/1.3.6.1.4.1.5962.99.1.3711725607.829148806.1687338938407.3.116/series/1.3.6.1.4.1.5962.99.1.3711725607.829148806.1687338938407.6.116");
			final URL url = new URL(serviceURL);
			Log.info("url : " + url);
			final HttpURLConnection connection = (HttpURLConnection) url.openConnection();

			for (Map.Entry<String, String> entry : transferSyntaxes.entrySet()) {
				connection.addRequestProperty(HttpHeaders.ACCEPT, "multipart/related; type=\"application/dicom\";transfer-syntax=" + entry.getKey() + ";q=" + entry.getValue() + ";boundary=" + BOUNDARY);
			}
			connection.setRequestProperty(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
			connection.setRequestProperty("KOS-SOPInstanceUID", sopInstanceUID);

			BoundaryFunc boundaryManager = (String contentType) -> {
				String[] respContentTypeParams = contentType.split(";");
				for (String respContentTypeParam : respContentTypeParams)
					if (respContentTypeParam.replace(" ", "").startsWith("boundary="))
						return respContentTypeParam
								.substring(respContentTypeParam.indexOf('=') + 1)
								.replaceAll("\"", "");

				return null;
			};

			// Trigger exception if something went wrong
			switch(connection.getResponseCode()) {
				case 200:
				case 206:
					break;
				case 404:
					throw new RequestErrorException("Series (or study) cannot be found ", 404);
				default:
				//	throw new RequestErrorException("Error : " + connection.getErrorStream().readAllBytes(), connection.getResponseCode());
					Log.warn("ohif : " + connection.getResponseCode());

			}
			Log.info("content-Type : " + connection.getContentType());
			Log.info("Response message : " + connection.getResponseMessage());
			
			String boundary = boundaryManager.getBoundary(connection.getContentType());
			if (boundary == null) {
				Log.fatal("Invalid response. Unpacking of parts not possible.");
				throw new RequestErrorException("Multipart boundary cannot be determined", 500);
			}

			DicomCacheInstance dc = getCacheInstance(studyUID, seriesUID);

			if (dc == null) {
				Log.fatal("Can't get DicomCacheInstance for specified study and series UID");
				throw new RequestErrorException("Internal server error", 500);
			}

			new MultipartParser(boundary).parse(new BufferedInputStream(connection.getInputStream()), (partNumber, multipartInputStream) -> {
				Map<String, List<String>> headerParams = multipartInputStream.readHeaderParams();
				try {
					//Log.info("Image time : " + Duration.between(startTime, Instant.now()).toString());
					byte[] rawDicomFile = multipartInputStream.readAllBytes();
					DicomInputStream dis = new DicomInputStream(new ByteArrayInputStream(rawDicomFile));
					Attributes dataSet = dis.readDataset();
					String instanceUID = dataSet.getString(Tag.SOPInstanceUID);

					Log.info("[CACHE] Received file " + instanceUID);
					dc.dicomFiles.put(instanceUID, rawDicomFile);

					if (waitingFutures.containsKey(instanceUID)) {
						Log.info("[CACHE] Publish file " + instanceUID);
						waitingFutures.get(instanceUID).complete(rawDicomFile);
						waitingFutures.remove(instanceUID);
					}

					// Say that instance is now available
					// This is used to populate metadata for OHIF
					// Todo : see if we only need to trigger this once or if performance is ok like that
					vertx.eventBus().publish(getEventBusID(studyUID, seriesUID), instanceUID);

				} catch (Exception e) {
					Log.fatal("Failed to process Part #" + partNumber + headerParams, e);
				}
			});

			if (dc.dicomFiles.isEmpty()) {
				Log.error("C-MOVE data is empty.");
				throw new RequestErrorException("C-MOVE was empty", 404);
			}

			dc.complete = true;
			Log.info("[CACHE] Complete");
		} catch (ConnectException e) {
			Log.error(String.format("DRIMbox at %s is not responding.", serviceURL));
			Log.error(String.format("Error : %s", e.getMessage()));

			throw new RequestErrorException("DRIMbox source is not responding", 502);
		} catch (EOFException e) {
			// TODO : see how it's working with new error handling in multi<>
			// This is mainly bc the SOPInstance UID was not found in the database
			// We don't have any rest code for Multi<> types yet, so we need to do this workaround
			// + special code for this error since it's not a specific 404
			throw new RequestErrorException("SOPInstance not found in database", 1404);
		// Allow to throw RequestErrorException in the body
		} catch (RequestErrorException e) {
			throw e;
		} catch (Exception e) {
			Log.error("Unknown error : ");
			e.printStackTrace();
			throw new RequestErrorException("Unknown error", 500);
		}



	}

}
