package com.j256.testcheckpublisher.lambda;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.j256.testcheckpublisher.plugin.frameworks.TestFileResult.TestLevel;

public class Utf8UtilsTest {

	@Test
	public void testCoverage() {
		GithubFormat format = GithubFormat.fromString(null);
		for (TestLevel level : TestLevel.values()) {
			assertNotNull(Utf8Utils.testLevelToEmoji(level, format));
		}

		format = GithubFormat.fromString("noemoji");
		assertNull(Utf8Utils.testLevelToEmoji(TestLevel.ERROR, format));
	}
}
