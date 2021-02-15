package com.j256.testcheckpublisher.lambda.github;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class IdResponseTest {

	@Test
	public void testStuff() {
		IdResponse response = new IdResponse();
		int id = 1032132321;
		response.setId(id);
		assertEquals(id, response.getId());
	}
}
