# Coin ledger primitive validation

26 September 2026. 24 focused PostgreSQL/HTTP/runtime-privilege tests passed, including eight new ledger cases and a new authenticated-wallet HTTP case.

Checks cover starter grant idempotency, concurrent spends, retry deduplication, rollback, immutable settlements, bounded refill, profile deletion, opposite-order credits, forged request fields, revoked access and actual SQL permission denials. The first run found an existing migration test hardcoded to three migrations; that assertion now checks that verification preserves the observed count after a removed row. The successful rerun is archived here.

Schema 004 adds wallets and append-only ledger entries. Runtime credentials can insert entries but cannot edit/delete them directly. Guest deletion cascades wallet removal. Balances and wallet revisions derive from entries; negative balance prevention serializes debits with a wallet row lock.

This is not yet the full economy feature: ticket reservation/debit integration, frozen room pools, domain payout integration, matchmaking and wallet UI remain pending. The installed PC host was not upgraded with these changes. The preceding full gameplay checkpoint has separate evidence under `manual-wifi-alpha15-2026-09-26`.
