package protrader.funds.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("transfers")
public record Transfer(
        @Id TransferKey id,
        TransferKind kind,
        long amountMinor,
        TransferStatus status,
        String reason,
        Long shortfallMinor,
        Instant createdAt,
        Instant completedAt) {

    public static Transfer newPending(long accountId, String transferId, TransferKind kind, long amountMinor,
            Instant now) {
        return new Transfer(new TransferKey(accountId, transferId), kind, amountMinor, TransferStatus.PENDING, null,
                null, now, null);
    }

    public long accountId() {
        return id.accountId();
    }

    public String transferId() {
        return id.transferId();
    }
}
