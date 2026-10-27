package protrader.funds.services;

/**
 * The (account, transfer_id) pair already exists with a different kind or amount.
 * Answered as 422 transfer_id_reused: a silent "here's the old record" could make a client
 * believe the new amount went through.
 */
public class TransferIdReusedException extends RuntimeException {

	public TransferIdReusedException(long accountId, String transferId) {
		super("transfer_id " + transferId + " was already used by account " + accountId + " for a different transfer");
	}
}
