package com.j256.testcheckpublisher.lambda;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StringUtilsTest {

	@Test
	public void testSplit() {
		assertNull(StringUtils.split(null, ','));
		assertEquals(0, StringUtils.split("", ',').length);
		String one = "hello";
		String two = "there";
		testSplit(one + "," + two, false, one, two);
		testSplit("," + one + "," + two, false, one, two);
		testSplit("," + one + ",," + two, false, one, two);
		testSplit("," + one + ",," + two + ",,", false, one, two);
		testSplit("," + one + ",," + two + ",,", true, "", one, "", two, "", "");
	}

	@Test
	public void testIsEmpty() {
		assertTrue(StringUtils.isEmpty(null));
		assertTrue(StringUtils.isEmpty(""));
		assertFalse(StringUtils.isEmpty("a"));
	}

	private void testSplit(String pattern, boolean preserveAll, String... parts) {
		String[] result = StringUtils.split(pattern, ',', preserveAll);
		assertArrayEquals(parts, result);
	}

	@Test
	public void testIsBlank() {
		assertTrue(StringUtils.isBlank(null));
		assertTrue(StringUtils.isBlank(""));
		assertTrue(StringUtils.isBlank(" "));
		assertFalse(StringUtils.isBlank("s"));
		// coverage
		new StringUtils();
	}
}
