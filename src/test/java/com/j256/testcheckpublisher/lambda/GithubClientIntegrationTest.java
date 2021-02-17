package com.j256.testcheckpublisher.lambda;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.security.PrivateKey;
import java.util.ArrayList;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import com.amazonaws.services.lambda.runtime.LambdaRuntime;
import com.j256.testcheckpublisher.lambda.KeyHandling;
import com.j256.testcheckpublisher.lambda.github.CheckRunRequest;
import com.j256.testcheckpublisher.lambda.github.GithubClient;
import com.j256.testcheckpublisher.lambda.github.GithubClientImpl;
import com.j256.testcheckpublisher.lambda.github.CheckRunRequest.CheckRunAnnotation;
import com.j256.testcheckpublisher.lambda.github.CheckRunRequest.CheckRunOutput;

public class GithubClientIntegrationTest {

	private static final String APP_ID_ENV_NAME = "APP_ID";
	private static final String PEM_PATH_ENV_NAME = "PEM_PATH";

	public static void main(String[] args) throws Exception {

		String appIdStr = System.getenv(APP_ID_ENV_NAME);
		assertNotNull(appIdStr);
		String pemPath = System.getenv(PEM_PATH_ENV_NAME);
		assertNotNull(pemPath);

		PrivateKey key = KeyHandling.loadKey(pemPath);

		GithubClientImpl.setGithubAppId(appIdStr);
		CloseableHttpClient httpClient = HttpClientBuilder.create().build();
		GithubClient githubClient = GithubClientImpl.createClient(httpClient, "j256",
				"test-check-publisher-maven-plugin", key, LambdaRuntime.getLogger(), "test");

		assertNotEquals(-1, githubClient.findInstallationId());
		assertTrue(githubClient.login());

		CheckRunOutput output =
				new CheckRunOutput("23 tests, 2 failures, 1 errors", "summary", "text", new ArrayList<CheckRunAnnotation>(), 1, 2, 3);
		CheckRunRequest request =
				new CheckRunRequest("Surefire test results", "eb8fae1ccc4411d0139507c26446fc41ac867c71", output);
		assertTrue(githubClient.addCheckRun(request));
	}
}
