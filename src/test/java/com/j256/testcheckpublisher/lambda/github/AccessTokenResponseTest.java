package com.j256.testcheckpublisher.lambda.github;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AccessTokenResponseTest {

	@Test
	public void testStuff() {
		AccessTokenResponse response = new AccessTokenResponse();
		String token = "fjwpofewj";
		response.setToken(token);
		assertEquals(token, response.getToken());
	}
}
