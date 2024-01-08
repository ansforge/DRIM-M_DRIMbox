/*
 *  DMPAuthentication.java - DRIMBox
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


import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.bcom.drimbox.DbMain;
import com.bcom.drimbox.psc.ProSanteConnect;

import io.quarkus.logging.Log;

@Path("/api/auth")
public class DMPAuthentication {

	@Inject
	ProSanteConnect proSanteConnect;

	@Inject
	WebTokenAuth webTokenAuth;

	@Inject
	DbMain dbMain;

	@ConfigProperty(name = "quarkus.oidc.client-id")
	String clientID;

	@ConfigProperty(name="quarkus.oidc.authorization-path")
	String baseURL;

	@ConfigProperty(name="quarkus.oidc.authentication.scopes")
	String scopes;

	@ConfigProperty(name="quarkus.oidc.authentication.extra-params.acr_values")
	String acrValues;

	@ConfigProperty(name="ris.host")
	String risHost;

	@ConfigProperty(name="conso.host")
	String consoHost;

	@ConfigProperty(name="source.host")
	String sourceHost;

	String uuid;

	/**
	 * Redirect to RIS page after PSC authentication
	 *
	 * @param code Code value sent from PSC to authenticate
	 * @param cookieSession Cookie sent from front app
	 * @param state Query param sent from PSC (used for security)
	 * @return 301 redirection
	 */
	@GET
	@Path("/redirect")
	@Produces(MediaType.TEXT_HTML)
	public Response getLandingPage(@QueryParam("code") String code, @CookieParam("SessionToken") Cookie cookieSession, @QueryParam("state") String state, @QueryParam("uuid") String uuid) {
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

		try {
			String url = "";

			if(dbMain.getTypeDrimbBOX() == DbMain.DrimBOXMode.SOURCE) {
				url = sourceHost + "/IHEInvokeImageDisplay";
			}
			else if (dbMain.getTypeDrimbBOX() == DbMain.DrimBOXMode.CONSO) {
				url = consoHost + "?uuid=" + this.uuid;
			}
			else if (dbMain.getTypeDrimbBOX() == DbMain.DrimBOXMode.RIS) {
				url = risHost + "/ris";
			}			
			URI uri = new URI(url);
			return Response.temporaryRedirect(uri).build();
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			throw new RuntimeException(e);
		}
	}


	/**
	 * Check if user is already connected or need to be redirected to PSC
	 *
	 * @param cookieSession Cookie sent from front app
	 * @return 200 ok with connected state
	 */
	@GET
	public Response authCheck(@CookieParam("SessionToken") Cookie cookieSession, @QueryParam("uuid") String uuid) {
		if (cookieSession == null)
			return Response.serverError().build();

		String cookieID = cookieSession.getValue();

		if(webTokenAuth.clientRegistered(cookieID)) {
			JsonObject userInfo = webTokenAuth.getUserInfo(cookieID);

			JsonObject temp = userInfo.getJsonObject("SubjectRefPro").getJsonArray("exercices").getJsonObject(0);
			String name = temp.getString("prenomDexercice") + " " + temp.getString("nomDexercice");
			Long epoch = webTokenAuth.getAccessToken(cookieID).getAuthTime();
			String date = new java.text.SimpleDateFormat("MM/dd/yyyy HH:mm:ss").format(new java.util.Date (epoch*1000));

			if(!Objects.equals(webTokenAuth.getSecteurActivite(cookieID), "empty"))
				return Response.ok("connected").build();

			else
				return Response.ok("connected but no structure : &" + name + "&" + date).build();
		}
		else {
			var responseType = "code";
			String state = UUID.randomUUID().toString();
			var nonce = UUID.randomUUID();

			UserData authentServ = new UserData();
			authentServ.setNonce(nonce);
			authentServ.setState(state);
			webTokenAuth.registerClient(cookieID, authentServ);
			String redirectURI = "";
			if(dbMain.getTypeDrimbBOX() == DbMain.DrimBOXMode.SOURCE) {
				Log.info("source auth");
				redirectURI = sourceHost + "/api-source/auth/redirect";
			}
			else if (dbMain.getTypeDrimbBOX() == DbMain.DrimBOXMode.CONSO) {
				Log.info("conso auth");
				redirectURI = consoHost + "/api-conso/auth/redirect";
			}
			else if (dbMain.getTypeDrimbBOX() == DbMain.DrimBOXMode.RIS) {
				redirectURI = risHost + "/api/auth/redirect";
			}
			this.uuid = uuid;

			var url = baseURL + "?response_type=" + responseType + "&client_id=" + clientID + "&redirect_uri=" + redirectURI + "&scope=openid " + scopes + "&acr_values=" + acrValues + "&state=" + state + "&nonce=" + nonce;
			return Response.ok("no connected : " + url).build();

		}
	}
}







