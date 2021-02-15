package com.j256.testcheckpublisher.lambda.github;

/**
 * Response from the access-token request:
 * https://docs.github.com/en/rest/reference/apps#create-an-installation-access-token-for-an-app
 * 
 * @author graywatson
 */
public class AccessTokenResponse {

	private String token;

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}
}
