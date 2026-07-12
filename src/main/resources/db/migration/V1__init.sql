create table accounts (
    id              uuid primary key,
    code            varchar(64) not null unique,
    name            varchar(255) not null,
    type            varchar(32) not null,
    currency        char(3) not null,
    balance         numeric(19, 4) not null default 0,
    version         bigint not null default 0,
    created_at      timestamptz not null default now(),
    constraint chk_accounts_balance_non_negative check (balance >= 0)
);

create table ledger_transactions (
    id                          uuid primary key,
    description                 text not null,
    occurred_at                  timestamptz not null default now(),
    idempotency_key              varchar(128) unique,
    reversal_of_transaction_id   uuid references ledger_transactions (id)
);

create table ledger_entries (
    id              uuid primary key,
    transaction_id  uuid not null references ledger_transactions (id),
    account_id      uuid not null references accounts (id),
    type            varchar(16) not null,
    amount          numeric(19, 4) not null,
    created_at      timestamptz not null default now(),
    constraint chk_ledger_entries_amount_positive check (amount > 0)
);

create index idx_ledger_entries_account_id on ledger_entries (account_id);
create index idx_ledger_entries_account_created on ledger_entries (account_id, created_at);
create index idx_ledger_entries_transaction_id on ledger_entries (transaction_id);

create table app_users (
    id              uuid primary key,
    username        varchar(128) not null unique,
    password_hash   varchar(255) not null,
    role            varchar(32) not null,
    created_at      timestamptz not null default now()
);
