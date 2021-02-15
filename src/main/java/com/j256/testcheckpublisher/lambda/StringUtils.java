package com.j256.testcheckpublisher.lambda;

import java.util.ArrayList;
import java.util.List;

/**
 * Local string utils so we don't have to have dependencies.
 * 
 * @author graywatson
 */
public class StringUtils {

	private final static String[] EMPTY_STRINGS = new String[0];

	/**
	 * Split a string by a separator character. Empty parts will be preserved.
	 */
	public static String[] split(String str, char separatorChar) {
		return split(str, separatorChar, false);
	}

	/**
	 * Split a string by a separator character.
	 */
	public static String[] split(String str, char separatorChar, boolean preserveAllTokens) {
		if (str == null) {
			return null;
		}
		int len = str.length();
		if (len == 0) {
			return EMPTY_STRINGS;
		}
		List<String> list = new ArrayList<String>();
		int argStart = 0;
		for (int i = 0; i < len; i++) {
			if (str.charAt(i) == separatorChar) {
				if (preserveAllTokens || i > argStart) {
					list.add(str.substring(argStart, i));
				}
				argStart = i + 1;
			}
		}
		if (preserveAllTokens || argStart < len) {
			list.add(str.substring(argStart, len));
		}
		return list.toArray(new String[list.size()]);
	}

	/**
	 * Return true if the argument is null or has a 0 length.
	 */
	public static boolean isEmpty(CharSequence seq) {
		return seq == null || seq.length() == 0;
	}

	/**
	 * Return true if the string is null, empty, or all whitespace, otherwise false.
	 */
	public static boolean isBlank(CharSequence cs) {
		if (cs == null || cs.length() == 0) {
			return true;
		}
		for (int i = 0; i < cs.length(); i++) {
			if (!Character.isWhitespace(cs.charAt(i))) {
				return false;
			}
		}
		return true;
	}
}
