package com.j256.testcheckpublisher.lambda;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GithubFormatTest {

	@Test
	public void testStuff() {
		testFormatString(null, true, true, true, false, false, false);
		testFormatString("", true, true, true, false, false, false);
		testFormatString("ignored", true, true, true, false, false, false);
		testFormatString("nodetails", false, true, true, false, false, false);
		testFormatString("noemoji", true, false, true, false, false, false);
		testFormatString("nonotice", true, true, false, false, false, false);
		testFormatString("alwaysannotate", true, true, true, true, false, false);
		testFormatString("noannotate", true, true, true, false, true, false);
		testFormatString("alldetails", true, true, true, false, false, true);

		testFormatString("noemoji,nonotice", true, false, false, false, false, false);
	}

	private void testFormatString(String formatStr, boolean showDetails, boolean showEmoji, boolean showNotice,
			boolean alwaysAnnotate, boolean noAnnotate, boolean allDetails) {
		GithubFormat format = GithubFormat.fromString(formatStr);
		assertEquals(showDetails, format.isShowDetails());
		assertEquals(showEmoji, format.isShowEmoji());
		assertEquals(showNotice, format.isShowNotice());
		assertEquals(alwaysAnnotate, format.isAlwaysAnnotate());
		assertEquals(noAnnotate, format.isNoAnnotate());
		assertEquals(allDetails, format.isAllDetails());
	}
}
