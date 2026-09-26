-- Restored with the primary database; the journal itself lives outside its restore boundary.
CREATE TABLE deletion_recovery (
    singleton boolean PRIMARY KEY DEFAULT true CHECK (singleton),
    journal_id text,
    applied_sequence bigint NOT NULL DEFAULT 0 CHECK (applied_sequence >= 0)
);
INSERT INTO deletion_recovery(singleton) VALUES (true);
