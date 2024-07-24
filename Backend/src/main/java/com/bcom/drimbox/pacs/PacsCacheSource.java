package com.bcom.drimbox.pacs;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.bcom.drimbox.utils.exceptions.RequestErrorException;

import io.quarkus.logging.Log;
import io.vertx.core.Vertx;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.NotFoundException;

@Singleton
public class PacsCacheSource {

	static class DicomCacheInstanceSource {
		// Instance UID => file
		Map<String, byte[]> dicomFiles = new HashMap<>();
		Boolean complete = false;
		int size = 0;
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
	Map<String, Map<String, DicomCacheInstanceSource>> dicomCacheSource = new HashMap<>();

	Map<String, CompletableFuture<byte[]>> waitingFutures = new HashMap<>();

	private final Vertx vertx;

	@ConfigProperty(name = "pacs.wado")
	String wadoSuffix;

	@ConfigProperty(name = "pacs.baseUrl")
	String pacsUrl;

	@Inject
	public PacsCacheSource(Vertx vertx) {
		this.vertx = vertx;
	}

	public static String getEventBusID(String studyUID, String seriesUID) { return studyUID + "/" + seriesUID; }


	public Future<byte[]> getDicomFile(String studyUID, String seriesUID, String instanceUID) {
		CompletableFuture<byte[]> completableFuture = new CompletableFuture<>();

		DicomCacheInstanceSource instance = getCacheInstance(studyUID, seriesUID);
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


	private DicomCacheInstanceSource getCacheInstance(String studyUID, String seriesUID) {
		if (!dicomCacheSource.containsKey(studyUID))
			return null;

		return dicomCacheSource.get(studyUID).get(seriesUID);
	}


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
	public io.vertx.core.Future<Integer> addNewEntry(String studyUID, String seriesUID) {
		// Do not rebuild if already here
		if (dicomCacheSource.containsKey(studyUID) && dicomCacheSource.get(studyUID).containsKey(seriesUID))
			return io.vertx.core.Future.succeededFuture(0);

		io.vertx.core.Future<Integer> future = vertx.executeBlocking(promise -> {
			Log.info("Starting cache build...");
			Map<String, DicomCacheInstanceSource> map = new HashMap<>();
			map.put(seriesUID, new DicomCacheInstanceSource());

			if(dicomCacheSource.containsKey(studyUID)) {
				dicomCacheSource.get(studyUID).put(seriesUID, new DicomCacheInstanceSource());
			}
			else {
				dicomCacheSource.put(studyUID, map);
			}
		});

		return future;
	}
	
	public void setInCache(InputStream input) {

		try {
			//Log.info("Image time : " + Duration.between(startTime, Instant.now()).toString());
			byte[] rawDicomFile = input.readAllBytes();
			DicomInputStream dis = new DicomInputStream(new ByteArrayInputStream(rawDicomFile));
			Attributes dataSet = dis.readDataset();
			String instanceUID = dataSet.getString(Tag.SOPInstanceUID);
			String studyUID = dataSet.getString(Tag.StudyInstanceUID);
			String seriesUID = dataSet.getString(Tag.SeriesInstanceUID);
			DicomCacheInstanceSource dc = getCacheInstance(studyUID, seriesUID);
			if (dc == null) {
				Log.fatal("Can't get DicomCacheInstance for specified study and series UID");
				throw new RequestErrorException("Internal server error", 500);
			}

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
			Log.fatal("Failed to process Part #" + e);
		}		
	}
}
