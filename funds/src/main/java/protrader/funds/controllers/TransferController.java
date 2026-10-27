package protrader.funds.controllers;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import protrader.funds.config.AuthFilter;
import protrader.funds.config.FundsProperties;
import protrader.funds.dto.CreateResult;
import protrader.funds.dto.TransferListResponse;
import protrader.funds.dto.TransferRequest;
import protrader.funds.dto.TransferResponse;
import protrader.funds.entity.Transfer;
import protrader.funds.entity.TransferKind;
import protrader.funds.repositories.TransferRepository.Cursor;
import protrader.funds.services.TransferService;
import protrader.funds.utils.ApiException;
import protrader.funds.utils.Inputs;
import protrader.funds.utils.PageTokens;

/** Public Funds API, base /v2/funds (contract §3.4). The account always comes from the credentials. */
@RestController
@RequestMapping("/v2/funds")
public class TransferController {

	private final TransferService transfers;
	private final Clock clock;
	private final long maxTransferMinor;

	public TransferController(TransferService transfers, Clock clock, FundsProperties props) {
		this.transfers = transfers;
		this.clock = clock;
		this.maxTransferMinor = props.maxTransferMinor();
	}

	/** Decided 2026-10-25: players can't add money themselves (fair seasons); deposits come from the system only. */
	@PostMapping(path = "/deposits", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<TransferResponse> deposit(@RequestAttribute(AuthFilter.ACCOUNT_ID) long accountId) {
		throw ApiException.notSupportedYet(null, "deposits are added by the system only");
	}

	@PostMapping(path = "/withdrawals", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<TransferResponse> withdraw(@RequestAttribute(AuthFilter.ACCOUNT_ID) long accountId,
			@RequestBody TransferRequest body) {
		String transferId = Inputs.transferId(body.transferId());
		long amountMinor = Inputs.amountMinor(body.amount(), maxTransferMinor);
		CreateResult result = transfers.create(accountId, transferId, TransferKind.WITHDRAW, amountMinor, now());
		return answer(result);
	}

	@GetMapping("/transfers/{transferId}")
	TransferResponse one(@RequestAttribute(AuthFilter.ACCOUNT_ID) long accountId, @PathVariable String transferId) {
		Inputs.transferId(transferId);
		return transfers.find(accountId, transferId)
				.map(TransferResponse::of)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "transfer_not_found", "no such transfer", null));
	}

	@GetMapping("/transfers")
	TransferListResponse list(@RequestAttribute(AuthFilter.ACCOUNT_ID) long accountId,
			@RequestParam(name = "limit", required = false) Integer limit,
			@RequestParam(name = "page_token", required = false) String pageToken) {
		int size = Inputs.limit(limit);
		Cursor after = PageTokens.decode(pageToken);
		// ask for one extra row to know whether another page exists
		List<Transfer> rows = transfers.page(accountId, size + 1, after);
		boolean more = rows.size() > size;
		List<Transfer> page = more ? rows.subList(0, size) : rows;
		String next = null;
		if (more) {
			Transfer last = page.get(page.size() - 1);
			next = PageTokens.encode(new Cursor(last.createdAt(), last.transferId()));
		}
		return new TransferListResponse(page.stream().map(TransferResponse::of).toList(), next);
	}

	/** 201 when this request created it, 200 when it existed (same answer as the first time). */
	static ResponseEntity<TransferResponse> answer(CreateResult result) {
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
				.body(TransferResponse.of(result.transfer()));
	}

	/** Cut to milliseconds: what we answer now must equal what a retry reads back from the DB. */
	private Instant now() {
		return clock.instant().truncatedTo(ChronoUnit.MILLIS);
	}
}
