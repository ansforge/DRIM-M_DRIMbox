/*
 *  RequestHelper.java - DRIMBox
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

package com.bcom.drimbox.utils;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.dcm4che3.mime.MultipartParser;
import org.jboss.resteasy.reactive.RestResponse;

import com.bcom.drimbox.pacs.CMoveSCU;
import com.bcom.drimbox.utils.exceptions.RequestErrorException;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;

@Singleton
public class RequestHelper {

	// Cache of instance datas
	@Inject
	CMoveSCU cMoveSCU;

	/**
	 * Extract query params from UriInfo and append it to base URL
	 *  e.g. if your initial request have params like ?param1=12&param2=DOUZE
	 *  with a baseUrl = http://pacs.dcm4che.org/wado it will return :
	 *  http://pacs.dcm4che.org/wado?param1=12&param2=DOUZE
	 *
	 *  We need this function because quarkus doesn't capture parameters in the @Path,
	 *  but it is stored in uriInfo.
	 *
	 * @param baseUrl Base url of the service you want to reach
	 * @param uriInfo UriInfo object obtained in the request (@Context UriInfo uriInfo)
	 *
	 * @return BaseUrl appended with the params in UriInfo
	 */
	public String constructUrlWithParam(String baseUrl, UriInfo uriInfo) {
		// Add ? to base URL (default separator for get params)
		baseUrl += "?";

		MultivaluedMap<String, String> queryParameters = uriInfo.getQueryParameters();
		StringBuilder baseUrlBuilder = new StringBuilder(baseUrl);
		for(String str : queryParameters.keySet()){
			baseUrlBuilder.append(str).append("=").append(queryParameters.getFirst(str)).append("&");
		}
		baseUrl = baseUrlBuilder.toString();

		// Remove the last &
		return baseUrl.substring(0, baseUrl.length() - 1);
	}

	/**
	 * Get String representation of connection
	 * @param connection HttpURLConnection of the server
	 * @return Empty string if an error occurred, contents of the input stream otherwise
	 */
	public RestResponse<String> readStringResponse(HttpURLConnection connection) {
		try {
			String response =new BufferedReader(
					new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))
					.lines()
					.collect(Collectors.joining(System.lineSeparator()));

			return RestResponse.ResponseBuilder
					.ok(response)
					.header("Content-Type", connection.getContentType())
					.build();

		} catch (Exception e) {
			Log.fatal("error while reading server string response");
		}
		return getErrorStringResponse();
	}

	public RestResponse<byte[]> readFileResponse(HttpURLConnection connection) {
		try {
			return RestResponse.ResponseBuilder
					.ok(connection.getInputStream().readAllBytes())
					.header("Content-Type", connection.getContentType())
					.build();
		} catch (Exception e) {
			Log.fatal("error while reading server file response");
			return RestResponse.ResponseBuilder.ok(new byte[0]).status(500).build();
		}
	}

	/**
	 * Convenience function that returns 500 RestResponse for String
	 */
	public RestResponse<String> getErrorStringResponse() {
		return getErrorStringResponse("Internal server error", 500);
	}

	public RestResponse<String> getErrorStringResponse(String message, int code) {
		return RestResponse.ResponseBuilder.ok(message).status(code).build();
	}

	/**
	 * Convenience function that returns 500 RestResponse for files
	 */
	public RestResponse<byte[]> getDeniedFileResponse() {
		return getDeniedFileResponse(500);
	}

	public RestResponse<byte[]> getDeniedFileResponse(int code) {
		return RestResponse.ResponseBuilder.ok(new byte[0]).status(code).build();
	}

	// Todo : add String authToken
	public interface ServiceConnection {
		HttpURLConnection connect(String url) throws RequestErrorException;
	}

	// Todo : see if Response<> can do the work instead to avoid duplicate functions
	public RestResponse<String> stringRequest(String pacsUrl, ServiceConnection service) {
		try {
			HttpURLConnection connection = service.connect(pacsUrl);
			RestResponse<String> response = readStringResponse(connection);

			connection.disconnect();
			return response;
		} catch (RequestErrorException e) {
			logError("string request", pacsUrl, e.getMessage());
			return getErrorStringResponse(e.getMessage(), e.getErrorCode());
		} catch (Exception e) {
			logError("string request", pacsUrl, e.getMessage());
			return getErrorStringResponse();
		}
	}

	private void logError(String location, String url, String errorMessage) {
		Log.fatal("Error while doing " + location);
		Log.fatal("URL : " + url);
		Log.fatal(errorMessage);
	}


	public RestResponse<byte[]> fileRequest(String pacsUrl, ServiceConnection service) {
		try {
			HttpURLConnection connection = service.connect(pacsUrl);
			RestResponse<byte[]> response = readFileResponse(connection);

			connection.disconnect();
			return response;
		} catch (Exception e) {
			logError("file request", pacsUrl, e.getMessage());
			return getDeniedFileResponse();
		}
	}

	private interface BoundaryFunc { String getBoundary(String contentType); }
	public List<byte[]> multipartFileRequest(String pacsUrl, ServiceConnection service) {
		try {
			HttpURLConnection connection = service.connect(pacsUrl);

			BoundaryFunc boundaryManager = (String contentType) -> {
				String[] respContentTypeParams = contentType.split(";");
				for (String respContentTypeParam : respContentTypeParams)
					if (respContentTypeParam.replace(" ", "").startsWith("boundary="))
						return respContentTypeParam
								.substring(respContentTypeParam.indexOf('=') + 1)
								.replaceAll("\"", "");

				return null;
			};

			String boundary = boundaryManager.getBoundary(connection.getContentType());
			if (boundary == null) {
				Log.fatal("Invalid response. Unpacking of parts not possible.");
				throw new RequestErrorException("Multipart boundary cannot be determined", 500);
			}

			List<byte[]> fileList = new ArrayList<>();

			new MultipartParser(boundary).parse(new BufferedInputStream(connection.getInputStream()), (partNumber, multipartInputStream) -> {
				Map<String, List<String>> headerParams = multipartInputStream.readHeaderParams();
				try {
					//Log.info("Image time : " + Duration.between(startTime, Instant.now()).toString());
					fileList.add(multipartInputStream.readAllBytes());
				} catch (Exception e) {
					Log.fatal("Failed to process Part #" + partNumber + headerParams, e);
				}
			});


			connection.disconnect();
			return fileList;
		} catch (Exception e) {
			logError("file request", pacsUrl, e.getMessage());
			return new ArrayList<>();
		}
	}

	public Multi<byte[]> fileRequestCMove(String pacsUrl, List<String> supportedTransferSyntax, List<String> preferredTransferSyntax, String boundary, String config) {
		String studyUID = pacsUrl.split("/studies/")[1].split("/")[0];
		String serieUID = pacsUrl.split("/series/")[1].split("/")[0];

		try {
			return cMoveSCU.cMove(studyUID, serieUID, supportedTransferSyntax, preferredTransferSyntax, boundary, config);
		} catch (Exception e) {
			logError("CMove request", "cMove " + studyUID + " / " + serieUID, e.getMessage());

			return Multi.createFrom().empty();
		}
	}
}
