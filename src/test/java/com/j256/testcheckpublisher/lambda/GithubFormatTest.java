package com.j256.testcheckpublisher.lambda;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GithubFormatTest {

	@Test
	public void testStuff() {
		testFormatString(null, false, false, false, false, false, false);
		testFormatString("", false, false, false, false, false, false);
		testFormatString("ignored", false, false, false, false, false, false);
		testFormatString("nodetails", true, false, false, false, false, false);
		testFormatString("noemoji", false, true, false, false, false, false);
		testFormatString("nonotice", false, false, true, false, false, false);
		testFormatString("nopass", false, false, true, false, false, false);
		testFormatString("alwaysannotate", false, false, false, true, false, false);
		testFormatString("noannotate", false, false, false, false, true, false);
		testFormatString("alldetails", false, false, false, false, false, true);
		testFormatString("passdetails", false, false, false, false, false, true);

		testFormatString("noemoji,nopass", false, true, true, false, false, false);
	}

	private void testFormatString(String formatStr, boolean showDetails, boolean showEmoji, boolean showNotice,
			boolean alwaysAnnotate, boolean noAnnotate, boolean passDetails) {
		GithubFormat format = GithubFormat.fromString(formatStr);
		assertEquals(showDetails, format.isNoDetails());
		assertEquals(showEmoji, format.isNoEmoji());
		assertEquals(showNotice, format.isNoPass());
		assertEquals(alwaysAnnotate, format.isAlwaysAnnotate());
		assertEquals(noAnnotate, format.isNoAnnotate());
		assertEquals(passDetails, format.isPassDetails());
	}
}
