CREATE TABLE coin_wallets (
    player_id text PRIMARY KEY REFERENCES guests(id) ON DELETE CASCADE,
    refill_after bigint NOT NULL DEFAULT 0 CHECK (refill_after >= 0)
);
-- Append-only at runtime. One durable key per effect prevents duplicate charges
-- or credits; deleting a profile removes its entire private wallet by cascade.
CREATE TABLE coin_ledger (
    player_id text NOT NULL REFERENCES coin_wallets(player_id) ON DELETE CASCADE,
    entry_key text NOT NULL CHECK (length(entry_key) BETWEEN 1 AND 200),
    amount bigint NOT NULL CHECK (amount <> 0 AND amount BETWEEN -19200 AND 19200),
    created_at bigint NOT NULL CHECK (created_at >= 0),
    PRIMARY KEY (player_id, entry_key)
);
