package com.j256.testcheckpublisher.lambda;

import org.junit.Test;

public class CommonsLogTest {

	@Test
	public void testStuff() {
		CommonsLog log = new CommonsLog("name");
		log.setLevel(0);
		log.trace("trace");
		log.trace("trace", new Exception());
		log.debug("debug");
		log.debug("debug", new Exception());
		log.info("info");
		log.info("info", new Exception());
		log.warn("warn");
		log.warn("warn", new Exception());
		log.error("error");
		log.error("error", new Exception());
		log.fatal("fatal");
		log.fatal("fatal", new Exception());
	}
}
