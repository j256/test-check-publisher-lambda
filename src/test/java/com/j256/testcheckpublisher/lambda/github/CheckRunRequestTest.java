package com.j256.testcheckpublisher.lambda.github;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.j256.testcheckpublisher.lambda.github.CheckRunRequest.CheckLevel;
import com.j256.testcheckpublisher.lambda.github.CheckRunRequest.CheckRunAnnotation;
import com.j256.testcheckpublisher.lambda.github.CheckRunRequest.CheckRunOutput;
import com.j256.testcheckpublisher.lambda.github.CheckRunRequest.Conclusion;

public class CheckRunRequestTest {

	@Test
	public void testStuff() {
		List<CheckRunAnnotation> annotations = new ArrayList<CheckRunAnnotation>();
		CheckRunAnnotation annotation =
				new CheckRunAnnotation("path", 0, 0, CheckLevel.NOTICE, "title", "message", "details");
		assertEquals(annotation, annotation);
		annotations.add(annotation);
		CheckRunOutput output = new CheckRunOutput("title", "summary", "text", annotations, 1, 0, 0);
		CheckRunRequest request = new CheckRunRequest("name", "sha", output);
		assertEquals(Conclusion.SUCCESS, request.getConclusion());
		assertEquals(output, request.getOutput());
		assertEquals(output, output);
		assertEquals(request, request);
		assertEquals(request.hashCode(), request.hashCode());

		CheckRunOutput output2 =
				new CheckRunOutput("title", "summary", "text", new ArrayList<CheckRunAnnotation>(), 1, 1, 0);
		request = new CheckRunRequest("name", "sha", output2);
		assertEquals(Conclusion.FAILURE, request.getConclusion());
		assertNotEquals(output, output2);

		output = new CheckRunOutput("title", "summary", "text", new ArrayList<CheckRunAnnotation>(), 1, 0, 1);
		request = new CheckRunRequest("name", "sha", output);
		assertEquals(Conclusion.FAILURE, request.getConclusion());
	}
}
