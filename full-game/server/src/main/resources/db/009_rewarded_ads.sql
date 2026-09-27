CREATE TABLE reward_ad_intents (
    id text PRIMARY KEY,
    player_id text NOT NULL REFERENCES guests(id) ON DELETE CASCADE,
    reward_day bigint NOT NULL,
    slot integer NOT NULL CHECK (slot BETWEEN 1 AND 5),
    ad_unit text NOT NULL,
    created_at bigint NOT NULL,
    expires_at bigint NOT NULL CHECK (expires_at > created_at),
    UNIQUE (player_id, reward_day, slot)
);
CREATE TABLE reward_ad_receipts (
    transaction_id text PRIMARY KEY,
    intent_id text NOT NULL UNIQUE REFERENCES reward_ad_intents(id) ON DELETE CASCADE,
    credited_at bigint NOT NULL
);
