package com.j256.testcheckpublisher.lambda;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.LambdaRuntime;

/**
 * Implementation of the lambda context for testing purposes.
 * 
 * @author graywatson
 */
public class ContextImpl implements Context {

	@Override
	public int getRemainingTimeInMillis() {
		return 0;
	}

	@Override
	public int getMemoryLimitInMB() {
		return 0;
	}

	@Override
	public LambdaLogger getLogger() {
		return LambdaRuntime.getLogger();
	}

	@Override
	public String getLogStreamName() {
		return "log-stream";
	}

	@Override
	public String getLogGroupName() {
		return "log-group";
	}

	@Override
	public String getInvokedFunctionArn() {
		return null;
	}

	@Override
	public CognitoIdentity getIdentity() {
		return null;
	}

	@Override
	public String getFunctionVersion() {
		return "version";
	}

	@Override
	public String getFunctionName() {
		return "name";
	}

	@Override
	public ClientContext getClientContext() {
		return null;
	}

	@Override
	public String getAwsRequestId() {
		return "request-id";
	}
};
