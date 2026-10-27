package protrader.funds.utils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import protrader.funds.repositories.TransferRepository.Cursor;

/**
 * page_token = base64url("<created_at epoch ms>:<transfer_id>") of the last row on the page.
 * Opaque to clients; a token that doesn't decode is 422 invalid_field page_token.
 * Keyset paging (not OFFSET): new transfers arriving between page loads don't shift or repeat rows.
 */
public final class PageTokens {

	private PageTokens() {
	}

	public static String encode(Cursor cursor) {
		String raw = cursor.createdAt().toEpochMilli() + ":" + cursor.transferId();
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	public static Cursor decode(String token) {
		if (token == null || token.isEmpty()) {
			return null;
		}
		try {
			String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
			int colon = raw.indexOf(':');
			long millis = Long.parseLong(raw.substring(0, colon));
			String transferId = Inputs.transferId(raw.substring(colon + 1));
			return new Cursor(Instant.ofEpochMilli(millis), transferId);
		} catch (RuntimeException e) {
			throw ApiException.invalidField("page_token", "page_token is not valid");
		}
	}
}
