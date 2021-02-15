package com.j256.testcheckpublisher.lambda;

import com.j256.testcheckpublisher.plugin.frameworks.TestFileResult.TestLevel;

/**
 * Isolation for any UTF8 characters that screw up Eclipse.
 * 
 * @author graywatson
 */
public class EmojiUtils {

	/**
	 * Return the emoji for the level and the format or null if none.
	 */
	public static String levelToEmoji(TestLevel level, GithubFormat format) {
		if (!format.isShowEmoji()) {
			return null;
		}
		// ✔️ :heavy_check_mark:️
		// 🟢 :green_circle:
		// ✅ :white_check_mark:
		// 🛑 :stop_sign:
		// 🟠 :orange_circle:
		// ⚠️ :warning:
		// 🔴 :red_circle:
		// ❗ :exclamation:
		// 🔥 :fire:
		// 🚩 :triangular_flag_on_post:
		// 🚫 :no_entry_sign:
		// 📍 :round_pushpin:
		// 🔺 :small_red_triangle:
		// ❌ :x:
		// ℹ️ :information_source:
		// 🆗 :ok:	
		switch (level) {
			case FAILURE:
				return ":x:";
			case ERROR:
				return ":warning:";
			case NOTICE:
				return ":heavy_check_mark:";
			default:
				return null;
		}
	}
}
