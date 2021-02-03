package com.j256.testcheckpublisher.lambda;

import org.apache.commons.logging.impl.SimpleLog;

/**
 * Our little implementation of commons logging to be able to pull up HTTP logging if necessary.
 * 
 * @author graywatson
 */
public class CommonsLog extends SimpleLog {

	private static final long serialVersionUID = -6952819026547720192L;
	private static final String COMMONS_LOG_LEVEL_ENV_NAME = "LOG_LEVEL";

	{
		setLevel(levelToType(System.getenv(COMMONS_LOG_LEVEL_ENV_NAME)));
	}

	public CommonsLog(String name) {
		super(name);
	}

	@Override
	protected void log(int type, Object message, Throwable throwable) {
		System.out.println(typeToString(type) + " " + message);
		if (throwable != null) {
			throwable.printStackTrace(System.out);
		}
	}

	private String typeToString(int type) {
		switch (type) {
			case SimpleLog.LOG_LEVEL_OFF:
				return "OFF";
			case SimpleLog.LOG_LEVEL_TRACE:
				return "TRACE";
			case SimpleLog.LOG_LEVEL_DEBUG:
				return "DEBUG";
			/* info below */
			case SimpleLog.LOG_LEVEL_WARN:
				return "WARN";
			case SimpleLog.LOG_LEVEL_ERROR:
				return "ERROR";
			case SimpleLog.LOG_LEVEL_FATAL:
				return "FATAL";
			case SimpleLog.LOG_LEVEL_INFO:
			default:
				return "INFO";
		}
	}

	private static int levelToType(String str) {
		if (str == null) {
			return SimpleLog.LOG_LEVEL_INFO;
		}
		switch (str) {
			case "OFF":
				return SimpleLog.LOG_LEVEL_OFF;
			case "TRACE":
				return SimpleLog.LOG_LEVEL_TRACE;
			case "DEBUG":
				return SimpleLog.LOG_LEVEL_DEBUG;
			/* info below */
			case "WARN":
				return SimpleLog.LOG_LEVEL_WARN;
			case "ERROR":
				return SimpleLog.LOG_LEVEL_ERROR;
			case "FATAL":
				return SimpleLog.LOG_LEVEL_FATAL;
			case "INFO":
			default:
				return SimpleLog.LOG_LEVEL_INFO;
		}
	}
}
