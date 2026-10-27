package protrader.funds.entity;

/** Money in (DEPOSIT) or money out (WITHDRAW). Stored in the DB by name. */
public enum TransferKind {
	DEPOSIT,
	WITHDRAW;

	/** The public JSON word: "deposit" / "withdraw". */
	public String json() {
		return this == DEPOSIT ? "deposit" : "withdraw";
	}
}
