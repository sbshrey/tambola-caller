ALTER TABLE guests ADD COLUMN device_key_hash text UNIQUE;
ALTER TABLE guests ADD COLUMN session_revision bigint NOT NULL DEFAULT 0 CHECK (session_revision >= 0);
ALTER TABLE guests ADD CONSTRAINT device_key_hash_format
    CHECK (device_key_hash IS NULL OR device_key_hash ~ '^[0-9a-f]{64}$');
