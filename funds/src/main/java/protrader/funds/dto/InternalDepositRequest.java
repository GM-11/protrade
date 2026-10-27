package protrader.funds.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Body of POST /internal/v1/funds/deposits — system deposits such as the season grant. */
public record InternalDepositRequest(
		@JsonProperty("account_id") String accountId,
		@JsonProperty("transfer_id") String transferId,
		@JsonProperty("amount") String amount) {
}
