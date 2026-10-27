package protrader.funds.services;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import protrader.funds.dto.CreateResult;
import protrader.funds.entity.Transfer;
import protrader.funds.entity.TransferKind;
import protrader.funds.repositories.TransferRepository;

@Service
public class TransferService {

    private final TransferRepository transfers;

    public TransferService(TransferRepository transfers) {
        this.transfers = transfers;
    }

    @Transactional
    public CreateResult create(long accountId, String transferId, TransferKind kind, long amountMinor, Instant now) {
        Transfer transfer = Transfer.newPending(accountId, transferId, kind, amountMinor, now);

        int inserted = transfers.insertIfAbsent(transfer);
        if (inserted == 1) {
            return new CreateResult(transfer, true);
        }
        Transfer existing = transfers.find(accountId, transferId)
                .orElseThrow(() -> new IllegalStateException(
                        "No existing transfer found for accountId=" + accountId + " transferId=" + transferId));

        if (kind != existing.kind() || amountMinor != existing.amountMinor()) {
            throw new TransferIdReusedException(accountId, transferId);
        }
        return new CreateResult(existing, false);
    }

    public Optional<Transfer> find(long accountId, String transferId) {
        return transfers.find(accountId, transferId);
    }

    public List<Transfer> page(long accountId, int limit, TransferRepository.Cursor after) {
        return transfers.page(accountId, limit, after);
    }
}
