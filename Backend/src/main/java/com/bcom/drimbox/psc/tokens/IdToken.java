/*
 *  IdToken.java - DRIMBox
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

package com.bcom.drimbox.psc.tokens;

import jakarta.json.JsonObject;

public class IdToken extends PscToken {

	private String rawIdToken;
	private String aud;
	private String atHash;
	private String subjectNameID;
	private Long authTime;
	private String acr;
	private Boolean emailVerified;
	private String preferredUsername;

	public IdToken(String token) {
		super(token);
		JsonObject jsonObject = decodeJWTToJsonObject(token);
		this.rawIdToken = token;
		this.aud = jsonObject.getString("aud", "");
		this.atHash = jsonObject.getString("at_hash", "");
		this.subjectNameID = jsonObject.getString("family_name", "");
		this.authTime = jsonObject.getJsonNumber("auth_time").longValue();
		this.acr = jsonObject.getString("acr", "");
		this.emailVerified = jsonObject.getBoolean("email_verified", false);
		this.preferredUsername = jsonObject.getString("preferred_username", "");

	}

	public String getAud() {
		return aud;
	}

	public void setAud(String aud) {
		this.aud = aud;
	}

	public String getAtHash() {
		return atHash;
	}

	public void setAtHash(String atHash) {
		this.atHash = atHash;
	}

	public String getSubjectNameID() {
		return subjectNameID;
	}

	public void setSubjectNameID(String subjectNameID) {
		this.subjectNameID = subjectNameID;
	}

	public Long getAuthTime() {
		return authTime;
	}

	public void setAuthTime(Long authTime) {
		this.authTime = authTime;
	}

	public String getAcr() {
		return acr;
	}

	public void setAcr(String acr) {
		this.acr = acr;
	}

	public Boolean getEmailVerified() {
		return emailVerified;
	}

	public void setEmailVerified(Boolean emailVerified) {
		this.emailVerified = emailVerified;
	}

	public String getPreferredUsername() {
		return preferredUsername;
	}

	public void setPreferredUsername(String preferredUsername) {
		this.preferredUsername = preferredUsername;
	}

	public String getRawIdToken() {
		return rawIdToken;
	}

	public void setRawIdToken(String rawIdToken) {
		this.rawIdToken = rawIdToken;
	}
}
