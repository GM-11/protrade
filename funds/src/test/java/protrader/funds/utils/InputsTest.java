package protrader.funds.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import protrader.funds.repositories.TransferRepository.Cursor;

/** Plain unit tests: money strings ↔ paise, ids, page tokens. */
class InputsTest {

	private static final long MAX = 1_000_000_000L; // Rs 1 crore in paise

	@Test
	void amountsBecomePaise() {
		assertEquals(500_000, Inputs.amountMinor("5000", MAX));
		assertEquals(500_050, Inputs.amountMinor("5000.5", MAX));
		assertEquals(500_050, Inputs.amountMinor("5000.50", MAX));
		assertEquals(1, Inputs.amountMinor("0.01", MAX));
		assertEquals(MAX, Inputs.amountMinor("10000000.00", MAX));
	}

	@Test
	void badAmounts() {
		for (String bad : new String[] {"0", "0.00", "-1", "+1", "1.234", "1e3", " 1", "01", "", ".5", "1.", "10000000.01"}) {
			ApiException e = assertThrows(ApiException.class, () -> Inputs.amountMinor(bad, MAX), bad);
			assertEquals("amount", e.field(), bad);
		}
		assertThrows(ApiException.class, () -> Inputs.amountMinor(null, MAX));
	}

	@Test
	void paiseBecomeRupeeStrings() {
		assertEquals("5000.50", Money.format(500_050));
		assertEquals("0.01", Money.format(1));
		assertEquals("100000.00", Money.format(10_000_000));
	}

	@Test
	void transferIds() {
		assertEquals("grant-s1_1000", Inputs.transferId("grant-s1_1000"));
		assertEquals("a".repeat(48), Inputs.transferId("a".repeat(48)));
		for (String bad : new String[] {"", "a".repeat(49), "has space", "slash/", "ünï"}) {
			assertEquals("transfer_id", assertThrows(ApiException.class, () -> Inputs.transferId(bad)).field(), bad);
		}
	}

	@Test
	void pageTokensRoundTrip() {
		Cursor c = new Cursor(Instant.parse("2026-10-25T10:00:00.123Z"), "wd-1");
		assertEquals(c, PageTokens.decode(PageTokens.encode(c)));
		assertNull(PageTokens.decode(null));
		assertNull(PageTokens.decode(""));
		assertEquals("page_token", assertThrows(ApiException.class, () -> PageTokens.decode("not-a-token")).field());
	}
}
