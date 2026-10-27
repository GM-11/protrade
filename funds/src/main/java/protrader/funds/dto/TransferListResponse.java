package protrader.funds.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/** GET /v2/funds/transfers. next_page_token is null on the last page. */
public record TransferListResponse(
		@JsonProperty("transfers") List<TransferResponse> transfers,
		@JsonProperty("next_page_token") String nextPageToken) {
}
