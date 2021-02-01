package com.j256.testcheckpublisher.lambda.github;

/**
 * Response from the access-token request:
 * https://docs.github.com/en/rest/reference/apps#create-an-installation-access-token-for-an-app
 * 
 * @author graywatson
 */
public class AccessTokensResponse {

	private String token;

	public String getToken() {
		return token;
	}
}
