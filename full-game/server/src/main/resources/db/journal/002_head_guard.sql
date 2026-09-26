-- Runtime may update only head. The identity and historical intent rows remain immutable to it.
CREATE FUNCTION guard_deletion_journal_head() RETURNS trigger
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
    IF NEW.singleton IS DISTINCT FROM OLD.singleton OR NEW.journal_id IS DISTINCT FROM OLD.journal_id
       OR NEW.head <> OLD.head + 1 THEN
        RAISE EXCEPTION 'Deletion journal head must advance by one without changing identity' USING ERRCODE = '23514';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM profile_deletions WHERE sequence = NEW.head) THEN
        RAISE EXCEPTION 'Deletion journal head needs its committed intent in this transaction' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION guard_deletion_journal_head() FROM PUBLIC;
CREATE TRIGGER deletion_journal_head_guard BEFORE UPDATE ON deletion_journal_identity
FOR EACH ROW EXECUTE FUNCTION guard_deletion_journal_head();
