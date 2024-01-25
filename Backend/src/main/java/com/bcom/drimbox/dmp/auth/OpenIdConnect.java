/*
 *  OpenIdConnect.java - DRIMBox
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

package com.bcom.drimbox.dmp.auth;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;

import org.apache.http.client.ClientProtocolException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.bcom.drimbox.psc.ProSanteConnect;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;

@Path("/api/")
public class OpenIdConnect {

	@ConfigProperty(name = "quarkus.oidc.client-id")
	String clientID;

	@ConfigProperty(name="quarkus.oidc.authorization-path")
	String baseURL;

	@ConfigProperty(name="quarkus.oidc.authentication.scopes")
	String scopes;

	@ConfigProperty(name="quarkus.oidc.authentication.extra-params.acr_values")
	String acrValues;

	@Inject
	WebTokenAuth webTokenAuth;

	@Inject
	ProSanteConnect proSanteConnect;

	@GET()
	@Path(".well-known/openid-configuration")
	public JsonObject configuration() {
		InputStream is = getFileFromResourceAsStream("CISIS/configopenid.json");
		JsonReader rdr = Json.createReader(is);
		JsonObject json = rdr.readObject();
		return json;
	}


	@GET()
	@Path("ohif/checks")
	public Response checks(@CookieParam("SessionToken") Cookie cookieSession, @QueryParam("state") String stateL) {
		Log.info("danslafoncqsdfqsfqsfdqsdfqdston");
		if (cookieSession == null)
			return Response.serverError().build();

		String cookieID = cookieSession.getValue();
		Log.info("oussssi");

		String url = "http://localhost:3000/redirect?state="+stateL + "&code=123";

		if(webTokenAuth.clientRegistered(cookieID)) {
			Log.info("if");
			return Response //seeOther = 303 redirect
					.seeOther(UriBuilder.fromUri(url).build())
					.build();
		}
		else {
			Log.info("else");
			var nonce = UUID.randomUUID();

			UserData authentServ = new UserData();
			authentServ.setNonce(nonce);
			authentServ.setState(stateL);
			var responseType = "code";

			String redirectURI = "http://localhost:4200/api-source/ohif/redirect";

			url = baseURL + "?response_type=" + responseType + "&client_id=" + clientID + "&redirect_uri=" + redirectURI + "&scope=openid " + scopes + "&acr_values=" + acrValues + "&state=" + stateL + "&nonce=" + nonce;
			return Response //seeOther = 303 redirect
					.seeOther(UriBuilder.fromUri(url).build())
					.build();
		}
	}


	@GET
	@Path("ohif/redirect")
	@Produces(MediaType.TEXT_HTML)
	public Response getLandingPage(@QueryParam("code") String code, @CookieParam("SessionToken") Cookie cookieSession, @QueryParam("state") String state, @QueryParam("uuid") String uuid) {
		Log.info("IM HERER");
		if (cookieSession != null) {
			String cookieID = cookieSession.getValue();
			if(webTokenAuth.clientRegistered(cookieID)) {
				if(code != null && state != null && state.equals(webTokenAuth.getState(cookieID).toString())) {
					Boolean tokenCreated = proSanteConnect.createAuthToken(code, cookieID);

					if(!tokenCreated && webTokenAuth.clientRegistered(cookieID))
						webTokenAuth.removeClient(cookieID);
				}
			}
		}

		String url = "http://localhost:3000/redirect?state="+state+"&code=" + code;

		try {
			URI uri = new URI(url);
			return Response.seeOther(uri).build();
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			throw new RuntimeException(e);
		}
	}

	@POST()
	@Produces("application/json")
	@Path("ohif/token")
	public JsonObject simulToken(@CookieParam("SessionToken") Cookie cookieSession, @QueryParam("state") UUID stateL) {
		Log.info("token here");
		InputStream is = getFileFromResourceAsStream("CISIS/configopenid.json");
		JsonReader rdr = Json.createReader(is);
		JsonObject json = rdr.readObject();
		return json;

	}

	/**
	 * Get file from the resource folder
	 * @param fileName File to load
	 * @return Opened file
	 */
	private InputStream getFileFromResourceAsStream(String fileName) {

		// The class loader that loaded the class
		ClassLoader classLoader = getClass().getClassLoader();
		InputStream inputStream = classLoader.getResourceAsStream(fileName);

		// the stream holding the file content
		if (inputStream == null) {
			throw new IllegalArgumentException("file not found! " + fileName);
		} else {
			return inputStream;
		}
	}

	@GET
	@Path("ohif/deco")
	@Produces(MediaType.TEXT_XML)
	public Response deco(@CookieParam("SessionToken") Cookie cookieSession) throws ClientProtocolException, IOException  {
		Log.info("ouiouioui");
		Log.info(cookieSession.getValue());
		String url = "https://auth.bas.psc.esante.gouv.fr/auth/realms/esante-wallet/protocol/openid-connect/logout?id_token_hint=" + webTokenAuth.getIdToken(cookieSession.getValue()).getRawIdToken();
		webTokenAuth.removeClient(cookieSession.getValue());
		URI uri;
		try {
			uri = new URI(url);
			return Response.seeOther(uri).build();
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			throw new RuntimeException(e);
		}
	}
}

