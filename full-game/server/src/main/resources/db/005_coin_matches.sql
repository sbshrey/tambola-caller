ALTER TABLE rooms ADD COLUMN matchable boolean NOT NULL DEFAULT false;
CREATE INDEX rooms_matchmaking ON rooms (phase, expires_at, id) WHERE matchable;
CREATE TABLE match_receipts (
    actor text NOT NULL REFERENCES guests(id) ON DELETE CASCADE,
    command_id text NOT NULL,
    request_hash text NOT NULL,
    room_id text NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    response text NOT NULL,
    PRIMARY KEY (actor, command_id)
);
CREATE INDEX match_receipts_room ON match_receipts (room_id);
