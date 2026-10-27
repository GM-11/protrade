package protrader.funds.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Body of POST /v2/funds/deposits and /v2/funds/withdrawals. Money is a rupee string ("500.50"). */
public record TransferRequest(
		@JsonProperty("transfer_id") String transferId,
		@JsonProperty("amount") String amount) {
}
