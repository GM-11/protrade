-- One row per money-in / money-out request. The row is also the "still to do" note:
-- status = PENDING means the cash owner has not given a final answer yet (piece 3 retries those).
create table transfers (
    account_id      bigint      not null,
    transfer_id     varchar(48) not null,
    kind            varchar(8)  not null check (kind in ('DEPOSIT', 'WITHDRAW')),
    amount_minor    bigint      not null check (amount_minor > 0),
    status          varchar(10) not null check (status in ('PENDING', 'COMPLETED', 'REJECTED')),
    reason          varchar(32),                        -- REJECTED only
    shortfall_minor bigint,                             -- insufficient_cash only, so a retry gets the same answer
    created_at      timestamptz not null,
    completed_at    timestamptz,
    -- the "only once" rule: Postgres itself refuses a second row with the same pair
    primary key (account_id, transfer_id)
);

-- GET /v2/funds/transfers: newest first, per account
create index transfers_by_account_newest on transfers (account_id, created_at desc, transfer_id desc);
