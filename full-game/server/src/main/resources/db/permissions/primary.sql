-- Run as owner, on a dedicated database, with psql variables database and runtime_role.
REVOKE ALL ON DATABASE :"database" FROM PUBLIC;
REVOKE ALL ON DATABASE :"database" FROM :"runtime_role";
GRANT CONNECT ON DATABASE :"database" TO :"runtime_role";
REVOKE ALL ON SCHEMA public FROM :"runtime_role";
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO :"runtime_role";
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC, :"runtime_role";
GRANT SELECT ON schema_migrations, guests, rooms, create_receipts, command_receipts,
    room_events, rate_limits, finished_rounds, room_participants, deletion_receipts, deletion_recovery TO :"runtime_role";
GRANT INSERT ON guests, rooms, create_receipts, command_receipts, room_events,
    rate_limits, finished_rounds, room_participants, deletion_receipts TO :"runtime_role";
GRANT UPDATE ON guests, rooms TO :"runtime_role";
GRANT UPDATE (response) ON command_receipts TO :"runtime_role";
GRANT UPDATE (payload) ON finished_rounds TO :"runtime_role";
GRANT UPDATE (requests) ON rate_limits TO :"runtime_role";
GRANT UPDATE (journal_id, applied_sequence) ON deletion_recovery TO :"runtime_role";
GRANT DELETE ON guests, rooms, command_receipts, room_events, rate_limits,
    room_participants, deletion_receipts TO :"runtime_role";
GRANT SELECT, INSERT ON coin_wallets, coin_ledger TO :"runtime_role";
GRANT UPDATE (refill_after) ON coin_wallets TO :"runtime_role";
GRANT SELECT, INSERT ON match_receipts TO :"runtime_role";
GRANT UPDATE (response) ON match_receipts TO :"runtime_role";

GRANT SELECT, INSERT ON reward_ad_intents, reward_ad_receipts TO :"runtime_role";
