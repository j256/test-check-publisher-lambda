package com.j256.testcheckpublisher.lambda;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.j256.testcheckpublisher.plugin.frameworks.TestFileResult.TestLevel;

public class EmojiUtilsTest {

	@Test
	public void testCoverage() {
		GithubFormat format = GithubFormat.fromString(null);
		for (TestLevel level : TestLevel.values()) {
			assertNotNull(level + " should not be null", EmojiUtils.levelToEmoji(level, format));
		}

		format = GithubFormat.fromString("noemoji");
		assertNull(EmojiUtils.levelToEmoji(TestLevel.ERROR, format));
	}
}
