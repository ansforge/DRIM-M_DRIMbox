/*
 *  ProSanteConnect.java - DRIMBox
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

package com.bcom.drimbox.psc;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.bcom.drimbox.DRIMbox;
import com.bcom.drimbox.auth.WebTokenAuth;
import com.bcom.drimbox.psc.tokens.AccessToken;
import com.bcom.drimbox.psc.tokens.IdToken;
import com.bcom.drimbox.psc.tokens.RefreshToken;

import io.quarkus.logging.Log;

@Singleton
public class ProSanteConnect {
	@ConfigProperty(name="pcs.baseurl")
	String baseURL;

	@ConfigProperty(name = "quarkus.oidc.credentials.secret")
	String clientSecret;

	@ConfigProperty(name = "quarkus.oidc.client-id")
	String clientID;

	@Inject
	WebTokenAuth webTokenAuth;

	@Inject
	DRIMbox drimbox;

	@ConfigProperty(name="conso.host")
	String consoHost;

	@ConfigProperty(name="source.host")
	String sourceHost;

	/**
	 * Get user info based on the accessToken
	 *
	 * You can check beforehand if the token is valid with ProSanteConnect.introspectToken
	 *
	 * @param rawAccessToken PCS raw accessToken
	 * @return The UserInfo associated with the AccessToken. If something went wrong with the token the object will contain an error field (e.g. "error" and "error_description"). If something else went wrong the return value can be empty.
	 */
	public JsonObject getUserInfo(String rawAccessToken) {
		try {
			HttpClient httpclient = HttpClients.createDefault();
			HttpPost httpPost = new HttpPost(baseURL + "/userinfo");
			httpPost.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + rawAccessToken);

			HttpResponse response = httpclient.execute(httpPost);
			HttpEntity entity = response.getEntity();

			if (entity != null) {
				try (InputStream is = entity.getContent()) {
					JsonReader rdr = Json.createReader(is);

					return rdr.readObject();
				}
			}
		} catch (Exception e) {
			Log.error(e.getMessage());
		}

		return Json.createObjectBuilder().build();
	}
	/**
	 * Make an introspect request to the Pro Sante Connect endpoint
	 * @param rawAccessToken PSC raw accessToken
	 * @return True if token is valid, false otherwise
	 */
	public Boolean introspectToken(String rawAccessToken) {
		try {
			String encoding = Base64.getEncoder().encodeToString((clientID + ":" + clientSecret).getBytes());


			HttpClient httpclient = HttpClients.createDefault();
			HttpPost httpPost = new HttpPost(baseURL + "/token/introspect");
			httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded");
			httpPost.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + encoding);
			List<NameValuePair> params = new ArrayList<>(1);
			if(rawAccessToken.contains("Bearer")) {
				rawAccessToken = rawAccessToken.split("Bearer ")[1];
			}
			params.add(new BasicNameValuePair("token", rawAccessToken));

			httpPost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
			HttpResponse response = httpclient.execute(httpPost);
			HttpEntity entity = response.getEntity();

			if (entity != null) {
				try (InputStream is = entity.getContent()) {
					JsonReader rdr = Json.createReader(is);
					JsonObject obj = rdr.readObject();

					return obj.getBoolean("active");
				}
			}
		} catch (Exception e) {
			Log.error(e.getMessage());
		}

		return false;
	}


	/**
	 *  Create authentication token in the backend to re-use later
	 *
	 * @param code Code value send from PSC
	 * @param cookieID Cookie ID of the client
	 *
	 * @return True if success, false otherwise
	 */
	public Boolean createAuthToken(String code, String cookieID) {
		try {

			String redirectURI = "";

			if(drimbox.getType() == DRIMbox.DRIMboxMode.SOURCE) {
				redirectURI = sourceHost + "/api-source/auth/redirect";
			}
			else if (drimbox.getType() == DRIMbox.DRIMboxMode.CONSO) {
				redirectURI = consoHost + "/api-conso/auth/redirect";
			}			
			Log.info("code : " + code);
			HttpClient httpclient = HttpClients.createDefault();
			HttpPost httppost = new HttpPost(baseURL + "token");
			httppost.setHeader("Content-Type", "application/x-www-form-urlencoded");
			List<NameValuePair> params = new ArrayList<>(2);
			params.add(new BasicNameValuePair("grant_type", "authorization_code"));
			params.add(new BasicNameValuePair("redirect_uri", redirectURI));
			params.add(new BasicNameValuePair("client_id", clientID));
			params.add(new BasicNameValuePair("client_secret", clientSecret));
			params.add(new BasicNameValuePair("code", code));

			httppost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
			HttpResponse response = httpclient.execute(httppost);
			HttpEntity entity = response.getEntity();

			if (entity != null) {
				try (InputStream is = entity.getContent()) {
					JsonReader rdr = Json.createReader(is);
					JsonObject obj = rdr.readObject();
					String rawAccessToken = obj.getString("access_token", "");
					if (rawAccessToken.isEmpty()) {
						Log.error("Can't get access token from PSC");
						return false;
					}
					AccessToken accessToken = new AccessToken(rawAccessToken);

					String rawIDToken = obj.getString("id_token", "");
					IdToken idToken = new IdToken(rawIDToken);
					if (rawIDToken.isEmpty()) {
						Log.error("Can't get ID token from PSC");
						return false;
					}

					String rawRefreshToken = obj.getString("refresh_token", "");
					RefreshToken refreshToken = new RefreshToken(rawRefreshToken);
					if (rawRefreshToken.isEmpty()) {
						Log.error("Can't get refresh token from PSC");
						return false;
					}

					String nonce = webTokenAuth.getNonce(cookieID).toString();

					if(nonce.equals(accessToken.getNonce()) && nonce.equals(idToken.getNonce())) {
						webTokenAuth.setAccessToken(cookieID, accessToken);
						webTokenAuth.setIdToken(cookieID, idToken);
						webTokenAuth.setRefreshToken(cookieID, refreshToken);
						webTokenAuth.setUserInfo(cookieID, getUserInfo(rawAccessToken));
						webTokenAuth.setSecteurActivite(cookieID, "empty");
						return true;
					}
				}
			}
		} catch (Exception e) {
			Log.error(e.getMessage());
		}

		return false;
	}
}
