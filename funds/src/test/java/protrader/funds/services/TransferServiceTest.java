package protrader.funds.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import protrader.funds.dto.CreateResult;
import protrader.funds.entity.Transfer;
import protrader.funds.entity.TransferKind;
import protrader.funds.entity.TransferStatus;
import protrader.funds.support.IntegrationTest;

/**
 * Piece 1 — TransferService.create, against real Postgres.
 *
 * Given/when/then:
 *  1. new pair                          → row saved PENDING, created = true, every field as given
 *  2. same pair, same kind + amount     → created = false, the FIRST record back (its created_at), still one row
 *  3. same pair, different amount       → TransferIdReusedException, saved row unchanged
 *  4. same pair, different kind         → TransferIdReusedException
 *  5. same transfer_id, other account   → a separate transfer (the pair is the key, not the id alone)
 *  6. 20 threads, same request at once  → exactly one created = true, all 20 answers equal, one row
 *  7. 20 threads, same id, 20 amounts   → exactly one wins, 19 get TransferIdReusedException, one row with the winner's amount
 */
class TransferServiceTest extends IntegrationTest {

	private static final Instant T0 = Instant.parse("2026-10-25T10:00:00.123Z");

	@Autowired
	TransferService service;

	@Test
	void newTransferIsSavedAsPending() {
		CreateResult r = service.create(1000, "wd-1", TransferKind.WITHDRAW, 50_000, T0);

		assertTrue(r.created());
		Transfer t = r.transfer();
		assertEquals(1000, t.accountId());
		assertEquals("wd-1", t.transferId());
		assertEquals(TransferKind.WITHDRAW, t.kind());
		assertEquals(50_000, t.amountMinor());
		assertEquals(TransferStatus.PENDING, t.status());
		assertNull(t.reason());
		assertNull(t.shortfallMinor());
		assertEquals(T0, t.createdAt());
		assertNull(t.completedAt());

		assertEquals(t, service.find(1000, "wd-1").orElseThrow(), "what we answered is what is stored");
		assertEquals(1, rowCount());
	}

	@Test
	void sameRequestTwiceReturnsTheFirstRecord() {
		CreateResult first = service.create(1000, "wd-1", TransferKind.WITHDRAW, 50_000, T0);
		CreateResult again = service.create(1000, "wd-1", TransferKind.WITHDRAW, 50_000, T0.plusSeconds(5));

		assertTrue(first.created());
		assertFalse(again.created());
		assertEquals(first.transfer(), again.transfer(), "the retry sees the first record, including its created_at");
		assertEquals(1, rowCount());
	}

	@Test
	void sameIdDifferentAmountIsReused() {
		service.create(1000, "wd-1", TransferKind.WITHDRAW, 50_000, T0);

		assertThrows(TransferIdReusedException.class,
				() -> service.create(1000, "wd-1", TransferKind.WITHDRAW, 500_000, T0.plusSeconds(5)));

		Transfer stored = service.find(1000, "wd-1").orElseThrow();
		assertEquals(50_000, stored.amountMinor(), "the saved row is untouched");
		assertEquals(1, rowCount());
	}

	@Test
	void sameIdDifferentKindIsReused() {
		service.create(1000, "t-1", TransferKind.DEPOSIT, 50_000, T0);

		assertThrows(TransferIdReusedException.class,
				() -> service.create(1000, "t-1", TransferKind.WITHDRAW, 50_000, T0.plusSeconds(5)));

		assertEquals(TransferKind.DEPOSIT, service.find(1000, "t-1").orElseThrow().kind());
	}

	@Test
	void sameIdOnAnotherAccountIsASeparateTransfer() {
		CreateResult alice = service.create(1000, "wd-1", TransferKind.WITHDRAW, 50_000, T0);
		CreateResult bob = service.create(1001, "wd-1", TransferKind.WITHDRAW, 70_000, T0);

		assertTrue(alice.created());
		assertTrue(bob.created());
		assertEquals(2, rowCount());
		assertEquals(70_000, service.find(1001, "wd-1").orElseThrow().amountMinor());
	}

	@Test
	void twentyThreadsSendingTheSameRequestMakeExactlyOneRow() throws Exception {
		List<CreateResult> results = race(20, i -> service.create(1000, "wd-race", TransferKind.WITHDRAW, 50_000, T0.plusMillis(i)));

		long created = results.stream().filter(CreateResult::created).count();
		assertEquals(1, created, "exactly one request inserts");
		Transfer winner = results.stream().filter(CreateResult::created).findFirst().orElseThrow().transfer();
		for (CreateResult r : results) {
			assertEquals(winner, r.transfer(), "every answer is the winner's record");
		}
		assertEquals(1, rowCount());
	}

	@Test
	void twentyThreadsWithDifferentAmountsLeaveOnlyTheWinner() throws Exception {
		List<Object> outcomes = race(20, i -> {
			try {
				return service.create(1000, "wd-race", TransferKind.WITHDRAW, 1_000 + i, T0);
			} catch (TransferIdReusedException e) {
				return e;
			}
		});

		List<CreateResult> wins = new ArrayList<>();
		int reused = 0;
		for (Object o : outcomes) {
			if (o instanceof CreateResult r && r.created()) {
				wins.add(r);
			} else if (o instanceof TransferIdReusedException) {
				reused++;
			}
		}
		assertEquals(1, wins.size(), "one winner");
		assertEquals(19, reused, "everyone else is told the id was reused");
		assertEquals(wins.get(0).transfer().amountMinor(), service.find(1000, "wd-race").orElseThrow().amountMinor());
		assertEquals(1, rowCount());
	}

	/** Runs n calls at the same moment (all threads wait at a gate first) and returns their results. */
	private static <T> List<T> race(int n, java.util.function.IntFunction<T> call) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(n);
		try {
			CountDownLatch ready = new CountDownLatch(n);
			CountDownLatch go = new CountDownLatch(1);
			List<Future<T>> futures = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				int index = i;
				futures.add(pool.submit(() -> {
					ready.countDown();
					go.await();
					return call.apply(index);
				}));
			}
			assertTrue(ready.await(10, TimeUnit.SECONDS));
			go.countDown();
			List<T> results = new ArrayList<>();
			for (Future<T> f : futures) {
				results.add(f.get(30, TimeUnit.SECONDS));
			}
			return results;
		} finally {
			pool.shutdownNow();
		}
	}
}
