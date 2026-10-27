package protrader.funds.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import protrader.funds.entity.Transfer;
import protrader.funds.entity.TransferKey;

public interface TransferRepository extends Repository<Transfer, TransferKey> {

    default int insertIfAbsent(Transfer t) {
        return insertIfAbsent(t.accountId(), t.transferId(), t.kind().name(), t.amountMinor(), t.status().name(),
                t.reason(), t.shortfallMinor(), t.createdAt(), t.completedAt());
    }

    @Modifying
    @Query("""
            insert into transfers (account_id, transfer_id, kind, amount_minor, status, reason,
                                   shortfall_minor, created_at, completed_at)
            values (:accountId, :transferId, :kind, :amountMinor, :status, :reason,
                    :shortfallMinor, :createdAt, :completedAt)
            on conflict (account_id, transfer_id) do nothing
            """)
    int insertIfAbsent(@Param("accountId") long accountId, @Param("transferId") String transferId,
            @Param("kind") String kind, @Param("amountMinor") long amountMinor, @Param("status") String status,
            @Param("reason") String reason, @Param("shortfallMinor") Long shortfallMinor,
            @Param("createdAt") Instant createdAt, @Param("completedAt") Instant completedAt);

    @Query("select * from transfers where account_id = :accountId and transfer_id = :transferId")
    Optional<Transfer> find(@Param("accountId") long accountId, @Param("transferId") String transferId);

    default List<Transfer> page(long accountId, int limit, Cursor after) {
        return after == null
                ? firstPage(accountId, limit)
                : pageAfter(accountId, after.createdAt(), after.transferId(), limit);
    }

    @Query("""
            select * from transfers where account_id = :accountId
            order by created_at desc, transfer_id desc limit :limit
            """)
    List<Transfer> firstPage(@Param("accountId") long accountId, @Param("limit") int limit);

    @Query("""
            select * from transfers
            where account_id = :accountId and (created_at, transfer_id) < (:createdAt, :transferId)
            order by created_at desc, transfer_id desc limit :limit
            """)
    List<Transfer> pageAfter(@Param("accountId") long accountId, @Param("createdAt") Instant createdAt,
            @Param("transferId") String transferId, @Param("limit") int limit);

    record Cursor(Instant createdAt, String transferId) {
    }
}
