-- Virtual beta grants and the largest fifty-player pool exceed the old 19,200 cap.
ALTER TABLE coin_ledger DROP CONSTRAINT coin_ledger_amount_check;
ALTER TABLE coin_ledger ADD CONSTRAINT coin_ledger_amount_check
    CHECK (amount <> 0 AND amount BETWEEN -100000 AND 100000);

CREATE INDEX rooms_expanded_coin_lobbies ON rooms (coin_starts_at, id)
    WHERE matchable AND phase = 'LOBBY' AND coin_human_seats < 50;
