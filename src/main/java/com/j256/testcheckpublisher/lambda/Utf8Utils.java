package com.j256.testcheckpublisher.lambda;

import com.j256.testcheckpublisher.plugin.frameworks.TestFileResult.TestLevel;

/**
 * Isolation for any UTF8 characters that screw up Eclipse.
 * 
 * @author graywatson
 */
public class Utf8Utils {

	/**
	 * Return the emoji for the level and the format or null if none.
	 */
	public static String testLevelToEmoji(TestLevel level, GithubFormat format) {
		if (!format.isShowEmoji()) {
			return null;
		}
		switch (level) {
			case NOTICE:
				// or ✔️✔ 🟢✅
				return "🟢";
			case FAILURE:
				// 🟠⚠️🟡
				return "🔴";
			case ERROR:
				// or ❗🔴🔥🚩🛑🚫📍❌
				return "🔴";
			default:
				return null;
		}
	}
}
