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

import com.j256.testcheckpublisher.lambda.KeyHandlingTest;
import com.j256.testcheckpublisher.lambda.github.CheckRunRequest.CheckRunAnnotation;
import com.j256.testcheckpublisher.lambda.github.CheckRunRequest.CheckRunOutput;

public class GithubClientImplTest {

	@Test
	public void testStuff() throws IOException, GeneralSecurityException {
		GithubClientImpl.setGithubAppId("id");
		CloseableHttpClient httpClient = HttpClientBuilder.create().build();
		String owner = "owner";
		String repo = "repo";
		String sha = "sha";
		GithubClient githubClient =
				GithubClientImpl.createClient(httpClient, KeyHandlingTest.readPrivateKey(), "label");
		assertFalse(githubClient.login(owner, repo));
		assertNull(githubClient.findInstallationOwner(123));
		assertEquals(-1, githubClient.getInstallationId());
		assertEquals(HttpStatus.SC_UNAUTHORIZED, githubClient.getLastStatusLine().getStatusCode());
		assertFalse(githubClient.login(owner, repo));
		assertNull(githubClient.requestCommitInfo(sha));
		assertNull(githubClient.requestTreeFiles(sha));
		CheckRunRequest request = new CheckRunRequest("name", sha,
				new CheckRunOutput("title", "summary", "text", new ArrayList<CheckRunAnnotation>(), 1, 2, 3));
		assertFalse(githubClient.addCheckRun(request));
	}

	@Test
	public void testCoverage() {
		GithubClientImpl.setGithubAppId(null);
		assertNull(GithubClientImpl.createClient(null, null, "label"));
		GithubClientImpl.setGithubAppId("id");
		assertNull(GithubClientImpl.createClient(null, null, "label"));
	}
}
