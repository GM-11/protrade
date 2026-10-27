package protrader.funds.utils;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** RFC 3339 UTC with milliseconds: 2026-10-17T08:30:00.123Z. */
public final class Timestamps {

	private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

	private Timestamps() {
	}

	public static String format(Instant instant) {
		return instant == null ? null : FORMAT.format(instant);
	}
}
