package com.j256.testcheckpublisher.lambda;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GithubFormatTest {

	@Test
	public void testStuff() {
		testFormatString(null, true, true, true, false);
		testFormatString("", true, true, true, false);
		testFormatString("ignored", true, true, true, false);
		testFormatString("nodetails", false, true, true, false);
		testFormatString("noemoji", true, false, true, false);
		testFormatString("nonotice", true, true, false, false);
		testFormatString("grid", true, true, true, true);

		testFormatString("noemoji,nonotice", true, false, false, false);
	}

	private void testFormatString(String formatStr, boolean showDetails, boolean showEmoji, boolean showNotice,
			boolean gridOnly) {
		GithubFormat format = GithubFormat.fromString(formatStr);
		assertEquals(showDetails, format.isShowDetails());
		assertEquals(showEmoji, format.isShowEmoji());
		assertEquals(showNotice, format.isShowNotice());
		assertEquals(gridOnly, format.isGridOnly());
	}
}
