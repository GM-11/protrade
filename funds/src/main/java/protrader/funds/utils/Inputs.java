package protrader.funds.utils;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/** Checks and converts public input. Every failure is 422 invalid_field with the field's name. */
public final class Inputs {

	/** Same rules as client_order_id: 1–48 chars of [A-Za-z0-9_-], case-sensitive. */
	private static final Pattern TRANSFER_ID = Pattern.compile("[A-Za-z0-9_-]{1,48}");
	/** Rupees as a decimal string with at most 2 decimals: "5000", "5000.5", "5000.50". No sign, no exponent. */
	private static final Pattern AMOUNT = Pattern.compile("(0|[1-9][0-9]{0,15})(\\.[0-9]{1,2})?");
	private static final Pattern ACCOUNT_ID = Pattern.compile("[1-9][0-9]{0,18}");

	private Inputs() {
	}

	public static String transferId(String value) {
		if (value == null || !TRANSFER_ID.matcher(value).matches()) {
			throw ApiException.invalidField("transfer_id", "transfer_id must be 1-48 characters of A-Z a-z 0-9 _ -");
		}
		return value;
	}

	/** "5000.50" → 500050 paise; must be > 0 and <= maxMinor. */
	public static long amountMinor(String value, long maxMinor) {
		if (value == null || !AMOUNT.matcher(value).matches()) {
			throw ApiException.invalidField("amount", "amount must be a rupee string with at most 2 decimals, e.g. \"5000.00\"");
		}
		BigDecimal minor = new BigDecimal(value).movePointRight(2);
		if (minor.signum() <= 0) {
			throw ApiException.invalidField("amount", "amount must be greater than 0");
		}
		if (minor.compareTo(BigDecimal.valueOf(maxMinor)) > 0) {
			throw ApiException.invalidField("amount", "amount must be at most " + Money.format(maxMinor));
		}
		return minor.longValueExact();
	}

	public static long accountId(String value) {
		if (value == null || !ACCOUNT_ID.matcher(value).matches()) {
			throw ApiException.invalidField("account_id", "account_id must be a positive decimal id");
		}
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			throw ApiException.invalidField("account_id", "account_id is too large");
		}
	}

	/** Page size: default 50, 1..500. */
	public static int limit(Integer value) {
		if (value == null) {
			return 50;
		}
		if (value < 1 || value > 500) {
			throw ApiException.invalidField("limit", "limit must be between 1 and 500");
		}
		return value;
	}
}
