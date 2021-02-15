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
	private RequestContext requestContext;
	private String body;
	@SerializedName("isBase64Encoded")
	private boolean isBodyBase64Encoded;

	public ApiGatewayRequest() {
		// for gson
	}

	public ApiGatewayRequest(String rawPath, String rawQueryString, Map<String, String> headers,
			RequestContext requestContext, String body, boolean isBodyBase64Encoded) {
		this.rawPath = rawPath;
		this.rawQueryString = rawQueryString;
		this.headers = headers;
		this.requestContext = requestContext;
		this.body = body;
		this.isBodyBase64Encoded = isBodyBase64Encoded;
	}

	public String getRawPath() {
		return rawPath;
	}

	public String getRawQueryString() {
		return rawQueryString;
	}

	public Map<String, String> getHeaders() {
		return headers;
	}

	public RequestContext getRequestContext() {
		return requestContext;
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
		@SerializedName("http")
		private HttpContext httpContext;

		public RequestContext() {
			// for gson
		}

		public RequestContext(String domainName, String requestId, HttpContext httpContext) {
			this.domainName = domainName;
			this.requestId = requestId;
			this.httpContext = httpContext;
		}

		public String getDomainName() {
			return domainName;
		}

		public String getRequestId() {
			return requestId;
		}

		public HttpContext getHttpContext() {
			return httpContext;
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

		public HttpContext() {
			// for gson
		}

		public HttpContext(String method, String path, String sourceIp, String userAgent) {
			this.method = method;
			this.path = path;
			this.sourceIp = sourceIp;
			this.userAgent = userAgent;
		}

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

		public String asString() {
			return "method '" + method + "', path '" + path + "', ip='" + sourceIp + "'";
		}

		@Override
		public String toString() {
			return "HttpContext [method=" + method + ", path=" + path + ", ip=" + sourceIp + "]";
		}
	}
}
