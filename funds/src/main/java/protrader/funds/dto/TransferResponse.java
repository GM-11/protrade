package protrader.funds.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import protrader.funds.entity.Transfer;
import protrader.funds.utils.Money;
import protrader.funds.utils.Timestamps;

/** The contract's Transfer object: {transfer_id, kind, amount, status, reason, created_at, completed_at}. */
public record TransferResponse(
		@JsonProperty("transfer_id") String transferId,
		@JsonProperty("kind") String kind,
		@JsonProperty("amount") String amount,
		@JsonProperty("status") String status,
		@JsonProperty("reason") String reason,
		@JsonProperty("created_at") String createdAt,
		@JsonProperty("completed_at") String completedAt) {

	public static TransferResponse of(Transfer t) {
		return new TransferResponse(t.transferId(), t.kind().json(), Money.format(t.amountMinor()), t.status().json(),
				t.reason(), Timestamps.format(t.createdAt()), Timestamps.format(t.completedAt()));
	}
}
