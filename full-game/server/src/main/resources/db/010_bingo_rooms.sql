CREATE TABLE bingo_rooms (
    id text PRIMARY KEY, code text NOT NULL UNIQUE CHECK (code ~ '^B-[A-Z2-9]{8}$'),
    phase text NOT NULL CHECK (phase IN ('LOBBY','ACTIVE','FINISHED','CLOSED')),
    expires_at bigint NOT NULL, starts_at bigint, friend_table boolean NOT NULL,
    checked_at bigint NOT NULL DEFAULT 0, payload text NOT NULL
);
CREATE INDEX bingo_open_queue ON bingo_rooms (starts_at, id) WHERE phase = 'LOBBY' AND NOT friend_table;
CREATE INDEX bingo_worker_queue ON bingo_rooms (checked_at, id) WHERE phase <> 'CLOSED';
CREATE TABLE bingo_participants (
    room_id text NOT NULL REFERENCES bingo_rooms(id) ON DELETE CASCADE,
    player_id text NOT NULL REFERENCES guests(id) ON DELETE CASCADE,
    PRIMARY KEY (room_id, player_id)
);
CREATE INDEX bingo_player_rooms ON bingo_participants(player_id, room_id);
CREATE TABLE bingo_receipts (
    actor text NOT NULL REFERENCES guests(id) ON DELETE CASCADE,
    command_id text NOT NULL, request_hash text NOT NULL,
    room_id text NOT NULL REFERENCES bingo_rooms(id) ON DELETE CASCADE,
    response text NOT NULL, PRIMARY KEY (actor, command_id)
);
CREATE INDEX bingo_room_receipts ON bingo_receipts(room_id);
