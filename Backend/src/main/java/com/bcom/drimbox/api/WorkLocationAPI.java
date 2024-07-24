/*
 *  WorkLocation.java - DRIMBox
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

package com.bcom.drimbox.api;

import com.bcom.drimbox.auth.WebTokenAuth;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.Response;

/**
 * API to manage work location for healthcare practitioners
 *
 */
@Path("/api")
public class WorkLocationAPI {

	@Inject
	WebTokenAuth webTokenAuth;

	protected static final String FIELD_ACTIVITIES = "activities";
	
	/**
	 * Retrieve all work locations
	 */
	@Path("/location")
	@GET
	public Response retrieveWorkLocations(@CookieParam("SessionToken") Cookie cookieSession) {

		if(cookieSession != null && webTokenAuth.clientRegistered(cookieSession.getValue())) {
			String sectActivite;
			JsonArray jArr = webTokenAuth.getUserInfo(cookieSession.getValue()).getJsonObject("SubjectRefPro").getJsonArray("exercices").getJsonObject(0).getJsonArray("activities");
			StringBuilder sectActiviteBuilder = new StringBuilder();
			for (int i = 0; i < jArr.size(); i++) {
				sectActiviteBuilder.append("/").append(jArr.getJsonObject(i).getString("raisonSocialeSite"));
			}
			sectActivite = sectActiviteBuilder.toString();
			sectActivite = sectActivite.replaceFirst("/", "");
			return Response.ok(sectActivite).build();
		}

		else return Response.status(401).build();
	}

	/**
	 * Set selected location
	 */
	@POST
	@Path("/location")
	public Response setWorkLocation(String requestBody, @CookieParam("SessionToken") Cookie cookieSession) {

		if(cookieSession != null && webTokenAuth.setSecteurActivite(cookieSession.getValue(), requestBody)) {
			
			JsonObject exercices = webTokenAuth.getUserInfo(cookieSession.getValue()).getJsonObject("SubjectRefPro").getJsonArray("exercices").getJsonObject(0);
			JsonObject activites = null;
			Log.info(exercices.getJsonArray(FIELD_ACTIVITIES).size());
			for (int i=0; i < exercices.getJsonArray(FIELD_ACTIVITIES).size(); i++) {
				if(exercices.getJsonArray(FIELD_ACTIVITIES).getJsonObject(i).getString("raisonSocialeSite").equals(requestBody))
					activites = exercices.getJsonArray(FIELD_ACTIVITIES).getJsonObject(i);
			}

			if (activites != null) {
				return Response.ok("Success " + activites.getString("ancienIdentifiantDeLaStructure")).build();
			}
		}

		return Response.status(401).build();
	}
}
