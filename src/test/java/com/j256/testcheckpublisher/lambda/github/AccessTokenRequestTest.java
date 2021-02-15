package com.j256.testcheckpublisher.lambda.github;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AccessTokenRequestTest {

	@Test
	public void testStuff() {
		int id = 13312;
		String repo = "fwejpjfwp";
		AccessTokenRequest request = new AccessTokenRequest(id, repo);
		assertEquals(id, request.getInstallationId());
		assertArrayEquals(new String[] { repo }, request.getRepositories());

		String[] repos = new String[] { "repo1", "repo2" };
		request = new AccessTokenRequest(id, repos);
		assertEquals(id, request.getInstallationId());
		assertArrayEquals(repos, request.getRepositories());
	}
}
