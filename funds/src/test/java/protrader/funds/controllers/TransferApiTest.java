package protrader.funds.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import protrader.funds.support.Http;
import protrader.funds.support.IntegrationTest;
import tools.jackson.databind.JsonNode;

/**
 * Funds REST API end to end (real server, real Postgres).
 *
 *  - withdraw: new → 201 pending; same retry → 200 identical body; reused id → 422 transfer_id_reused
 *  - public deposit → 422 not_supported_yet; internal deposit (internal port) → 201, retry 200
 *  - ports kept apart: /internal on the public port → 404, /v2 on the internal port → 404
 *  - read one: own → 200, someone else's → 404 transfer_not_found
 *  - list: newest first, pages joined by next_page_token, null on the last page
 *  - input checks → 422 invalid_field with the field; bad JSON → 400; wrong content type → 415
 */
class TransferApiTest extends IntegrationTest {

	private static final long ALICE = 1000;
	private static final long BOB = 1001;

	@Test
	void withdrawIsCreatedThenARetryGetsTheSameAnswer() {
		Http.Response first = as(ALICE).post("/v2/funds/withdrawals", "{\"transfer_id\":\"wd-1\",\"amount\":\"500.50\"}");
		assertEquals(201, first.status(), first.body());
		JsonNode t = first.json();
		assertEquals("wd-1", t.get("transfer_id").asString());
		assertEquals("withdraw", t.get("kind").asString());
		assertEquals("500.50", t.get("amount").asString());
		assertEquals("pending", t.get("status").asString());
		assertTrue(t.get("reason").isNull());
		assertTrue(t.get("completed_at").isNull());
		assertTrue(t.get("created_at").asString().matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d\\.\\d{3}Z"));

		Http.Response retry = as(ALICE).post("/v2/funds/withdrawals", "{\"transfer_id\":\"wd-1\",\"amount\":\"500.50\"}");
		assertEquals(200, retry.status());
		assertEquals(first.json(), retry.json(), "a retry gets exactly the first answer");
		assertEquals(1, rowCount());
	}

	@Test
	void sameIdWithAnotherAmountIsRejected() {
		as(ALICE).post("/v2/funds/withdrawals", "{\"transfer_id\":\"wd-1\",\"amount\":\"500.00\"}");

		Http.Response r = as(ALICE).post("/v2/funds/withdrawals", "{\"transfer_id\":\"wd-1\",\"amount\":\"5000.00\"}");
		assertEquals(422, r.status());
		assertEquals("transfer_id_reused", r.error());
		assertEquals("transfer_id", r.field());
	}

	@Test
	void playersCannotDepositThemselves() {
		Http.Response r = as(ALICE).post("/v2/funds/deposits", "{\"transfer_id\":\"dep-1\",\"amount\":\"100000.00\"}");
		assertEquals(422, r.status());
		assertEquals("not_supported_yet", r.error());
		assertEquals(0, rowCount());
	}

	@Test
	void systemDepositOnTheInternalPort() {
		String body = "{\"account_id\":\"1000\",\"transfer_id\":\"grant-s1-1000\",\"amount\":\"100000.00\"}";
		Http.Response first = internal().post("/internal/v1/funds/deposits", body);
		assertEquals(201, first.status(), first.body());
		assertEquals("deposit", first.json().get("kind").asString());
		assertEquals("100000.00", first.json().get("amount").asString());

		Http.Response retry = internal().post("/internal/v1/funds/deposits", body);
		assertEquals(200, retry.status());
		assertEquals(first.json(), retry.json());

		// Alice sees it as her transfer
		Http.Response mine = as(ALICE).get("/v2/funds/transfers/grant-s1-1000");
		assertEquals(200, mine.status());
		assertEquals(first.json(), mine.json());
	}

	@Test
	void portsAreKeptApart() {
		Http.Response internalOnPublic = as(ALICE).post("/internal/v1/funds/deposits",
				"{\"account_id\":\"1000\",\"transfer_id\":\"x\",\"amount\":\"1.00\"}");
		assertEquals(404, internalOnPublic.status());

		Http.Response publicOnInternal = internal().get("/v2/funds/transfers");
		assertEquals(404, publicOnInternal.status());

		assertEquals(200, internal().get("/healthz").status());
		assertEquals(0, rowCount());
	}

	@Test
	void internalDepositChecksItsInput() {
		Http.Response r = internal().post("/internal/v1/funds/deposits", "{\"account_id\":\"0\",\"transfer_id\":\"g-1\",\"amount\":\"1.00\"}");
		assertEquals(422, r.status());
		assertEquals("account_id", r.field());
	}

	@Test
	void readOneOnlyFindsYourOwn() {
		as(ALICE).post("/v2/funds/withdrawals", "{\"transfer_id\":\"wd-1\",\"amount\":\"1.00\"}");

		assertEquals(200, as(ALICE).get("/v2/funds/transfers/wd-1").status());
		Http.Response bob = as(BOB).get("/v2/funds/transfers/wd-1");
		assertEquals(404, bob.status());
		assertEquals("transfer_not_found", bob.error());
	}

	@Test
	void listIsNewestFirstAndPaged() {
		for (int i = 1; i <= 5; i++) {
			assertEquals(201, as(ALICE).post("/v2/funds/withdrawals", "{\"transfer_id\":\"t" + i + "\",\"amount\":\"1.00\"}").status());
		}
		as(BOB).post("/v2/funds/withdrawals", "{\"transfer_id\":\"bob-1\",\"amount\":\"1.00\"}");

		List<String> seen = new ArrayList<>();
		List<Integer> pageSizes = new ArrayList<>();
		String token = null;
		do {
			String path = "/v2/funds/transfers?limit=2" + (token == null ? "" : "&page_token=" + token);
			Http.Response r = as(ALICE).get(path);
			assertEquals(200, r.status(), r.body());
			JsonNode transfers = r.json().get("transfers");
			pageSizes.add(transfers.size());
			transfers.forEach(t -> seen.add(t.get("transfer_id").asString()));
			JsonNode next = r.json().get("next_page_token");
			token = next == null || next.isNull() ? null : next.asString();
		} while (token != null);

		assertEquals(List.of("t5", "t4", "t3", "t2", "t1"), seen, "newest first, only Alice's, no repeats");
		assertEquals(List.of(2, 2, 1), pageSizes);
	}

	@Test
	void listChecksLimitAndToken() {
		assertEquals("limit", as(ALICE).get("/v2/funds/transfers?limit=0").field());
		assertEquals("limit", as(ALICE).get("/v2/funds/transfers?limit=501").field());
		assertEquals("page_token", as(ALICE).get("/v2/funds/transfers?page_token=garbage").field());
	}

	@Test
	void badInputIsRejectedWithTheField() {
		String[][] cases = {
				// transfer_id, amount, expected field
				{"\"has space\"", "\"1.00\"", "transfer_id"},
				{"\"" + "a".repeat(49) + "\"", "\"1.00\"", "transfer_id"},
				{"\"\"", "\"1.00\"", "transfer_id"},
				{"null", "\"1.00\"", "transfer_id"},
				{"\"ok-1\"", "\"0\"", "amount"},
				{"\"ok-1\"", "\"0.00\"", "amount"},
				{"\"ok-1\"", "\"-1.00\"", "amount"},
				{"\"ok-1\"", "\"1.234\"", "amount"},
				{"\"ok-1\"", "\"1e3\"", "amount"},
				{"\"ok-1\"", "\"abc\"", "amount"},
				{"\"ok-1\"", "\"10000000.01\"", "amount"}, // over the Rs 1 crore default limit
				{"\"ok-1\"", "null", "amount"},
		};
		for (String[] c : cases) {
			Http.Response r = as(ALICE).post("/v2/funds/withdrawals", "{\"transfer_id\":" + c[0] + ",\"amount\":" + c[1] + "}");
			assertEquals(422, r.status(), "case " + c[0] + " / " + c[1] + ": " + r.body());
			assertEquals("invalid_field", r.error());
			assertEquals(c[2], r.field(), "case " + c[0] + " / " + c[1]);
		}
		assertEquals(0, rowCount());
	}

	@Test
	void largestAllowedAmountIsAccepted() {
		Http.Response r = as(ALICE).post("/v2/funds/withdrawals", "{\"transfer_id\":\"big\",\"amount\":\"10000000.00\"}");
		assertEquals(201, r.status(), r.body());
		assertEquals("10000000.00", r.json().get("amount").asString());
	}

	@Test
	void brokenRequestsGetTheRightCode() {
		Http.Response notJson = as(ALICE).post("/v2/funds/withdrawals", "{not json");
		assertEquals(400, notJson.status());
		assertEquals("invalid_json", notJson.error());

		Http.Response unknownField = as(ALICE).post("/v2/funds/withdrawals", "{\"transfer_id\":\"a\",\"amount\":\"1.00\",\"extra\":1}");
		assertEquals(400, unknownField.status());

		Http.Response wrongType = as(ALICE).send("POST", "/v2/funds/withdrawals", "text/plain", "hello");
		assertEquals(415, wrongType.status());
		assertEquals("content_type_must_be_json", wrongType.error());
	}
}
