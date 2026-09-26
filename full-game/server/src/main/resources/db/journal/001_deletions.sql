CREATE TABLE deletion_journal_identity (
    singleton boolean PRIMARY KEY DEFAULT true CHECK (singleton),
    journal_id text NOT NULL,
    head bigint NOT NULL DEFAULT 0 CHECK (head >= 0)
);
CREATE TABLE profile_deletions (
    sequence bigint PRIMARY KEY CHECK (sequence > 0),
    player_id text NOT NULL UNIQUE,
    confirmation_hash text NOT NULL UNIQUE CHECK (confirmation_hash ~ '^[0-9a-f]{64}$'),
    deleted_at bigint NOT NULL,
    confirm_until bigint NOT NULL CHECK (confirm_until > deleted_at)
);
