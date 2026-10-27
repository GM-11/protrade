package protrader.funds.utils;

import java.math.BigDecimal;

/** Paise (int64) ↔ the contract's rupee string with exactly 2 decimals: 500050 → "5000.50". */
public final class Money {

	private Money() {
	}

	public static String format(long minor) {
		return BigDecimal.valueOf(minor, 2).toPlainString();
	}
}
