/*
 *  WebTokenAuth.java - DRIMBox
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

package com.bcom.drimbox.auth;

import com.bcom.drimbox.psc.tokens.AccessToken;
import com.bcom.drimbox.psc.tokens.IdToken;
import com.bcom.drimbox.psc.tokens.RefreshToken;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Singleton;
import jakarta.json.JsonObject;


@Singleton
public class WebTokenAuth {

	private final Map<String, UserData> usersMap = new HashMap<>();


	WebTokenAuth(){	}

	/***
	 * Return the status of registration.
	 * This function doesn't check if the tokens are valid, only is the client is registered.
	 *
	 * @param cookieID Cookie ID from front end
	 *
	 * @return true if cookie ID is currently registered false otherwise
	 */
	public boolean clientRegistered(String cookieID) {
		return usersMap.containsKey(cookieID);
	}

	public String getState(String cookieID) {
		if (!clientRegistered(cookieID))
			return null;

		return usersMap.get(cookieID).getState();
	}

	public UUID getNonce(String cookieID) {
		if (!clientRegistered(cookieID))
			return null;

		return usersMap.get(cookieID).getNonce();
	}

	public JsonObject getUserInfo(String cookieID) {
		if (!clientRegistered(cookieID))
			return null;

		return usersMap.get(cookieID).getUserInfo();
	}

	public String getSecteurActivite(String cookieID) {
		if (!clientRegistered(cookieID))
			return null;

		return usersMap.get(cookieID).getSecteurActivite();
	}

	/***
	 * Set secteur activite
	 * @param cookieID Cookie ID from front end
	 * @param secteurActivite Secteur activite to set
	 * @return True if success, false otherwise
	 */
	public boolean setSecteurActivite(String cookieID, String secteurActivite) {
		if (!clientRegistered(cookieID))
			return false;

		usersMap.get(cookieID).setSecteurActivite(secteurActivite);
		return true;
	}

	public boolean setUserInfo(String cookieID, JsonObject userInfo) {
		if (!clientRegistered(cookieID))
			return false;

		usersMap.get(cookieID).setUserInfo(userInfo);
		return true;
	}

	public boolean setRefreshToken(String cookieID, RefreshToken refreshToken) {
		if (!clientRegistered(cookieID))
			return false;

		usersMap.get(cookieID).setRefreshToken(refreshToken);
		return true;
	}

	public boolean setIdToken(String cookieID, IdToken idToken) {
		if (!clientRegistered(cookieID))
			return false;

		usersMap.get(cookieID).setIdToken(idToken);
		return true;
	}
	
	public IdToken getIdToken(String cookieID) {
		if (!clientRegistered(cookieID))
			return null;

		return usersMap.get(cookieID).getIdToken();
	}

	public boolean setAccessToken(String cookieID, AccessToken accessToken) {
		if (!clientRegistered(cookieID))
			return false;

		usersMap.get(cookieID).setAccessToken(accessToken);
		return true;
	}


	public AccessToken getAccessToken(String cookieID) {
		if (!clientRegistered(cookieID))
			return null;

		return usersMap.get(cookieID).getAccessToken();
	}

	/***
	 * Register new client
	 * @param cookieID Cookie ID from frontend
	 * @param data Userdata that includes all the tokens
	 * @return True if success, false otherwise
	 */
	public boolean registerClient(String cookieID, UserData data) {
		if (clientRegistered(cookieID))
			return false;

		usersMap.put(cookieID, data);

		return true;
	}


	public void removeClient(String cookieID) {
		usersMap.remove(cookieID);
	}


}
