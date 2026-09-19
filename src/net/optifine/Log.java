package net.optifine;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Log {
	private static final Logger LOGGER = LogManager.getLogger();
	public static final boolean logDetail = System.getProperty("log.detail", "false").equals("true");

	public static void detail(final String s) {
		if (logDetail) {
			LOGGER.info("[OptiFine] " + s);
		}
	}

	public static void dbg(final String s) { LOGGER.info("[OptiFine] " + s); }

	public static void warn(final String s) { LOGGER.warn("[OptiFine] " + s); }

	public static void warn(final String s, final Throwable t) { LOGGER.warn("[OptiFine] " + s, t); }

	public static void error(final String s) { LOGGER.error("[OptiFine] " + s); }

	public static void error(final String s, final Throwable t) { LOGGER.error("[OptiFine] " + s, t); }

	/**
	 * Reflector "not present" notices for missing Forge elements. This client
	 * is Forge-less by design, so every one of these would print at startup
	 * (~50 lines of noise). The reflector lookups still return null/false and
	 * callers handle that; the notice itself is dropped.
	 */
	public static void log(final String s) { }
}
