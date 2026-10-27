package protrader.funds.entity;

/**
 * PENDING   = asked the cash owner (or about to), no final answer yet — also the "still to do" marker.
 * COMPLETED = the cash owner applied it.
 * REJECTED  = the cash owner said a definite no (reason on the row).
 */
public enum TransferStatus {
	PENDING,
	COMPLETED,
	REJECTED;

	/** The public JSON word: "pending" / "completed" / "rejected". */
	public String json() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}
}
