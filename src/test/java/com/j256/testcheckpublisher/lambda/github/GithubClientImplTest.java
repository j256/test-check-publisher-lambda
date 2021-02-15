package com.j256.testcheckpublisher.lambda.github;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;

import org.apache.http.HttpStatus;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Test;

import com.amazonaws.services.lambda.runtime.LambdaRuntime;
import com.j256.testcheckpublisher.lambda.KeyHandlingTest;
import com.j256.testcheckpublisher.lambda.github.CheckRunRequest.CheckRunAnnotation;
import com.j256.testcheckpublisher.lambda.github.CheckRunRequest.CheckRunOutput;

public class GithubClientImplTest {

	@Test
	public void testStuff() throws IOException, GeneralSecurityException {
		GithubClientImpl.setGithubAppId("id");
		CloseableHttpClient httpClient = HttpClientBuilder.create().build();
		GithubClient githubClient = GithubClientImpl.createClient(httpClient, "owner", "repo",
				KeyHandlingTest.readPrivateKey(), LambdaRuntime.getLogger(), "label");
		assertEquals(-1, githubClient.findInstallationId());
		assertEquals(HttpStatus.SC_UNAUTHORIZED, githubClient.getLastStatusLine().getStatusCode());
		assertFalse(githubClient.login());
		assertNull(githubClient.requestCommitInfo("sha"));
		assertNull(githubClient.requestTreeFiles("sha"));
		CheckRunRequest request = new CheckRunRequest("name", "sha",
				new CheckRunOutput("title", "summary", "text", new ArrayList<CheckRunAnnotation>(), 1, 2, 3));
		assertFalse(githubClient.addCheckRun(request));
	}
}
