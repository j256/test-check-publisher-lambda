package com.j256.testcheckpublisher.lambda;

import java.util.Map;

import com.google.gson.annotations.SerializedName;

/**
 * API gateway request in stream.
 * 
 * @author graywatson
 */
public class ApiGatewayRequest {

	private String rawPath;
	private String rawQueryString;
	private Map<String, String> headers;
	@SerializedName("requestContext")
	private RequestContext context;
	private String body;
	@SerializedName("isBase64Encoded")
	private boolean isBodyBase64Encoded;

	public String getRawPath() {
		return rawPath;
	}

	public String getRawQueryString() {
		return rawQueryString;
	}

	public Map<String, String> getHeaders() {
		return headers;
	}

	public RequestContext getContext() {
		return context;
	}

	public String getBody() {
		return body;
	}

	public boolean isBodyBase64Encoded() {
		return isBodyBase64Encoded;
	}

	/**
	 * Request context.
	 */
	public static class RequestContext {
		private String domainName;
		private String requestId;
		private HttpContext http;

		public String getDomainName() {
			return domainName;
		}

		public String getRequestId() {
			return requestId;
		}

		public HttpContext getHttp() {
			return http;
		}
	}

	/**
	 * HTTP request information.
	 */
	public static class HttpContext {

		private String method;
		private String path;
		private String sourceIp;
		private String userAgent;

		public String getMethod() {
			return method;
		}

		public String getPath() {
			return path;
		}

		public String getSourceIp() {
			return sourceIp;
		}

		public String getUserAgent() {
			return userAgent;
		}
	}
}
