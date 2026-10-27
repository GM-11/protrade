package protrader.funds.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import protrader.funds.support.Http;
import protrader.funds.support.IntegrationTest;
import protrader.funds.support.TestCredentials;

/**
 * Same credential rules as the order gateway:
 *  - no credentials, junk token, wrong key, wrong issuer/audience, expired, lifetime > 1 h → 401
 *  - API key → acts as the key's account; wrong secret / disabled key → 401
 *  - API key headers + bearer token → the API key decides (even if the token is fine)
 *  - /readyz is 200 once the DB, JWKS and key list are loaded
 */
class AuthTest extends IntegrationTest {

	private static final String PATH = "/v2/funds/transfers";

	@Test
	void noCredentials() {
		Http.Response r = withHeaders(Map.of()).get(PATH);
		assertEquals(401, r.status());
		assertEquals("unauthorized", r.error());
	}

	@Test
	void badTokens() {
		Instant now = Instant.now();
		String[] tokens = {
				"garbage",
				TestCredentials.token(TestCredentials.OTHER_KEY, TestCredentials.ISSUER, TestCredentials.AUDIENCE, 1000, now, Duration.ofMinutes(15)),
				TestCredentials.token(TestCredentials.KEY, "someone-else", TestCredentials.AUDIENCE, 1000, now, Duration.ofMinutes(15)),
				TestCredentials.token(TestCredentials.KEY, TestCredentials.ISSUER, "other-service", 1000, now, Duration.ofMinutes(15)),
				TestCredentials.token(TestCredentials.KEY, TestCredentials.ISSUER, TestCredentials.AUDIENCE, 1000, now.minusSeconds(3600), Duration.ofMinutes(15)),
				TestCredentials.token(TestCredentials.KEY, TestCredentials.ISSUER, TestCredentials.AUDIENCE, 1000, now, Duration.ofHours(2)),
		};
		for (String token : tokens) {
			assertEquals(401, withHeaders(Map.of("Authorization", "Bearer " + token)).get(PATH).status(), token);
		}
	}

	@Test
	void apiKeyActsAsItsAccount() {
		Http key = withHeaders(Map.of("X-Api-Key-Id", TestCredentials.KEY_ID, "X-Api-Secret", TestCredentials.KEY_SECRET));
		assertEquals(201, key.post("/v2/funds/withdrawals", "{\"transfer_id\":\"k-1\",\"amount\":\"1.00\"}").status());

		assertEquals(200, as(TestCredentials.KEY_ACCOUNT).get("/v2/funds/transfers/k-1").status(), "saved under the key's account");
		assertEquals(404, as(1000).get("/v2/funds/transfers/k-1").status());
	}

	@Test
	void badApiKeys() {
		assertEquals(401, withHeaders(Map.of("X-Api-Key-Id", TestCredentials.KEY_ID, "X-Api-Secret", "wrong")).get(PATH).status());
		assertEquals(401, withHeaders(Map.of("X-Api-Key-Id", "PKNOSUCHKEY", "X-Api-Secret", "x")).get(PATH).status());
		assertEquals(401, withHeaders(Map.of("X-Api-Key-Id", TestCredentials.DISABLED_KEY_ID,
				"X-Api-Secret", TestCredentials.DISABLED_KEY_SECRET)).get(PATH).status());
		assertEquals(401, withHeaders(Map.of("X-Api-Key-Id", TestCredentials.KEY_ID)).get(PATH).status(), "secret missing");
	}

	@Test
	void apiKeyWinsOverBearer() {
		// valid token for 1000, but a wrong API key → 401 (no fallback to the token)
		assertEquals(401, withHeaders(Map.of("Authorization", "Bearer " + TestCredentials.token(1000),
				"X-Api-Key-Id", TestCredentials.KEY_ID, "X-Api-Secret", "wrong")).get(PATH).status());

		// valid token for 1000 + valid key of 2000 → acts as 2000
		Http both = withHeaders(Map.of("Authorization", "Bearer " + TestCredentials.token(1000),
				"X-Api-Key-Id", TestCredentials.KEY_ID, "X-Api-Secret", TestCredentials.KEY_SECRET));
		assertEquals(201, both.post("/v2/funds/withdrawals", "{\"transfer_id\":\"both-1\",\"amount\":\"1.00\"}").status());
		assertEquals(200, as(TestCredentials.KEY_ACCOUNT).get("/v2/funds/transfers/both-1").status());
	}

	@Test
	void readyOnceEverythingIsLoaded() {
		Http.Response r = withHeaders(Map.of()).get("/readyz");
		assertEquals(200, r.status(), r.body());
	}
}
