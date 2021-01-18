package com.j256.testcheckpublisher;

import java.util.Map;

import com.google.gson.annotations.SerializedName;

/**
 * Response to API gateway.
 * 
 * @author graywatson
 */
public class ApiGatewayResponse {

	private int statusCode;
	private String[] cookies;
	// "Content-Type": "application/json"
	private Map<String, String> headers;
	private String body;
	@SerializedName("isBase64Encoded")
	private boolean isBodyBase64Encoded;

	public ApiGatewayResponse(int statusCode, String[] cookies, Map<String, String> headers, String body,
			boolean isBodyBase64Encoded) {
		this.statusCode = statusCode;
		this.cookies = cookies;
		this.headers = headers;
		this.body = body;
		this.isBodyBase64Encoded = isBodyBase64Encoded;
	}

	public int getStatusCode() {
		return statusCode;
	}

	public String[] getCookies() {
		return cookies;
	}

	public Map<String, String> getHeaders() {
		return headers;
	}

	public String getBody() {
		return body;
	}

	public boolean isBodyBase64Encoded() {
		return isBodyBase64Encoded;
	}
}
