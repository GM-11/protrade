package protrader.funds.dto;

import protrader.funds.entity.Transfer;

/**
 * What TransferService.create returns.
 *
 * @param transfer the row as it is now (the new one, or the one saved earlier)
 * @param created  true = this call inserted it (HTTP 201); false = it existed already (HTTP 200, same answer)
 */
public record CreateResult(Transfer transfer, boolean created) {
}
