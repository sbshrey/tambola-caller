ALTER TABLE guests ADD COLUMN revoked_at bigint;
-- Old logout records are indistinguishable from expired sessions. Fail closed for those tokens.
UPDATE guests SET revoked_at = expires_at WHERE expires_at <= (extract(epoch FROM clock_timestamp()) * 1000)::bigint;

CREATE TABLE room_participants (
    room_id text NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    player_id text NOT NULL,
    PRIMARY KEY (room_id, player_id)
);
CREATE INDEX room_participants_player ON room_participants (player_id, room_id);
-- Includes former members retained only in an audit or another player's command receipt.
WITH records AS (
    SELECT id AS room_id, payload::jsonb AS doc FROM rooms
    UNION ALL SELECT room_id, payload::jsonb FROM finished_rounds
    UNION ALL SELECT room_id, response::jsonb->'snapshot' FROM command_receipts
), people AS (
    SELECT room_id, coalesce(person->>'id', person->>'playerId') AS player_id
    FROM records CROSS JOIN LATERAL jsonb_array_elements(coalesce(doc->'members', '[]'::jsonb)) person
    UNION SELECT room_id, person->>'id'
    FROM records CROSS JOIN LATERAL jsonb_array_elements(coalesce(doc->'round'->'players', '[]'::jsonb)) person
    UNION SELECT room_id, actor FROM create_receipts
    UNION SELECT room_id, actor FROM command_receipts
)
INSERT INTO room_participants
SELECT DISTINCT people.room_id, people.player_id FROM people JOIN guests ON guests.id = people.player_id;

-- A secret-derived confirmation key, with no profile ID, name or raw token.
CREATE TABLE deletion_receipts (
    confirmation_hash text PRIMARY KEY,
    deleted_at bigint NOT NULL,
    expires_at bigint NOT NULL
);
CREATE INDEX deletion_receipts_expiry ON deletion_receipts (expires_at);
