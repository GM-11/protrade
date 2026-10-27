package protrader.funds.controllers;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import protrader.funds.config.FundsProperties;
import protrader.funds.dto.InternalDepositRequest;
import protrader.funds.dto.TransferResponse;
import protrader.funds.entity.TransferKind;
import protrader.funds.services.TransferService;
import protrader.funds.utils.Inputs;

/**
 * System deposits (e.g. the season grant, transfer_id "grant-s1-1000"). Internal port only — PortRoutingFilter
 * hides it from the public port. Same create rules as a public request: retry-safe, reused id → 422.
 */
@RestController
public class InternalTransferController {

	private final TransferService transfers;
	private final Clock clock;
	private final long maxTransferMinor;

	public InternalTransferController(TransferService transfers, Clock clock, FundsProperties props) {
		this.transfers = transfers;
		this.clock = clock;
		this.maxTransferMinor = props.maxTransferMinor();
	}

	@PostMapping(path = "/internal/v1/funds/deposits", consumes = MediaType.APPLICATION_JSON_VALUE)
	ResponseEntity<TransferResponse> deposit(@RequestBody InternalDepositRequest body) {
		long accountId = Inputs.accountId(body.accountId());
		String transferId = Inputs.transferId(body.transferId());
		long amountMinor = Inputs.amountMinor(body.amount(), maxTransferMinor);
		return TransferController.answer(transfers.create(accountId, transferId, TransferKind.DEPOSIT, amountMinor,
				clock.instant().truncatedTo(ChronoUnit.MILLIS)));
	}
}
