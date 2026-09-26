-- Shares the existing commit-ordered head and primary replay cursor. No tokens or names.
CREATE TABLE session_revocations (
    sequence bigint PRIMARY KEY CHECK (sequence > 0),
    player_id text NOT NULL UNIQUE,
    revoked_at bigint NOT NULL
);

CREATE OR REPLACE FUNCTION guard_deletion_journal_head() RETURNS trigger
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
    IF NEW.singleton IS DISTINCT FROM OLD.singleton OR NEW.journal_id IS DISTINCT FROM OLD.journal_id
       OR NEW.head <> OLD.head + 1 THEN
        RAISE EXCEPTION 'Recovery journal head must advance by one without changing identity' USING ERRCODE = '23514';
    END IF;
    IF (SELECT count(*) FROM (
        SELECT sequence FROM profile_deletions WHERE sequence = NEW.head
        UNION ALL SELECT sequence FROM session_revocations WHERE sequence = NEW.head
    ) intents) <> 1 THEN
        RAISE EXCEPTION 'Recovery journal head needs exactly one intent in this transaction' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION guard_deletion_journal_head() FROM PUBLIC;
