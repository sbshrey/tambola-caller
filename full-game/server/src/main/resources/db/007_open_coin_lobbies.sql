-- Derived sales metadata stays atomic with every room write, including restored rows.
-- Index these small values, not the entire payload: calls/presence can keep using HOT updates.
ALTER TABLE rooms
    ADD COLUMN coin_starts_at bigint GENERATED ALWAYS AS
        (CASE WHEN matchable AND phase = 'LOBBY' THEN (payload::jsonb->>'startsAt')::bigint END) STORED,
    ADD COLUMN coin_human_seats integer GENERATED ALWAYS AS
        (CASE WHEN matchable AND phase = 'LOBBY' THEN jsonb_array_length(payload::jsonb->'members') END) STORED;

CREATE INDEX rooms_open_coin_lobbies ON rooms (coin_starts_at, id)
    WHERE matchable AND phase = 'LOBBY' AND coin_human_seats < 8;
