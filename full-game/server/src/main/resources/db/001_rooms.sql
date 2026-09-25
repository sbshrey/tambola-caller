CREATE TABLE guests (
    id text PRIMARY KEY,
    name text NOT NULL,
    avatar integer NOT NULL,
    token_hash text UNIQUE NOT NULL,
    expires_at bigint NOT NULL
);
CREATE TABLE rooms (
    id text PRIMARY KEY,
    code text UNIQUE NOT NULL,
    phase text NOT NULL,
    expires_at bigint NOT NULL,
    checked_at bigint NOT NULL DEFAULT 0,
    payload text NOT NULL
);
CREATE INDEX rooms_live ON rooms (expires_at) WHERE phase IN ('LOBBY', 'ACTIVE');
CREATE TABLE create_receipts (
    actor text NOT NULL REFERENCES guests(id) ON DELETE CASCADE,
    command_id text NOT NULL,
    request_hash text NOT NULL,
    room_id text NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    PRIMARY KEY (actor, command_id)
);
CREATE TABLE command_receipts (
    room_id text NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    actor text NOT NULL,
    command_id text NOT NULL,
    request_hash text NOT NULL,
    response text NOT NULL,
    PRIMARY KEY (room_id, actor, command_id)
);
CREATE TABLE room_events (
    room_id text NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    revision bigint NOT NULL,
    payload text NOT NULL,
    PRIMARY KEY (room_id, revision)
);
CREATE TABLE rate_limits (
    bucket text NOT NULL,
    window_start bigint NOT NULL,
    requests integer NOT NULL,
    PRIMARY KEY (bucket, window_start)
);
CREATE TABLE finished_rounds (
    room_id text NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    round_id text NOT NULL,
    payload text NOT NULL,
    PRIMARY KEY (room_id, round_id)
);
