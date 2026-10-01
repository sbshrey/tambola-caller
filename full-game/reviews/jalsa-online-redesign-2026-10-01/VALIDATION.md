# Tambola Jalsa v47 implementation review

## Scope

- Reviewed the v44 home, Bingo entry, waiting room, live card and Tambola arena screenshots. The old home used two plain light cards; Bingo exposed a local Practice button and online quick rooms could start with one human plus simulated players.
- Reworked the home and Bingo presentation around live play. Tambola retains its established ticket, number board, claim and power layouts, while its waiting room now distinguishes a real opponent from a countdown.
- New app purchases opt into `realPlayersOnly`. The server isolates these queues from older simulated-seat queues, waits for two human members, and refunds unmatched entries after two minutes. Existing saved and older-client rooms retain their historical behavior to avoid changing an ongoing paid round.
- Local offline gameplay source and saved history remain for data compatibility, but the current app navigation does not enter local games.

## Validation completed before release

- Kotlin/Android builds and test compilation passed.
- Domain and client unit tests passed.
- The complete 219-test PostgreSQL server suite passed on a disposable local PostgreSQL 16 cluster with its backup/restore tools configured. An earlier test-setup attempt lacked `TAMBOLA_PG_BIN`; the complete rerun passed.
- Four added integration cases prove the two-human start and timeout/refund behavior for Tambola and Bingo. Both changed server test classes passed after those cases were added.
- Three native emulator tests passed on the final layout: home and Bingo navigation, Hindi portrait card selection at 200% text, and Hindi landscape live card and claim choices at 200% text. A landscape screenshot also confirmed the Friends label after the entry panel was made scrollable.
- English/Hindi resource parity passed for 969 resources. All 34 debug app unit tests, debug lint and publicBeta lint passed. The optimized v47 publicBeta APK built successfully.
- The v47 APK retains the beta application ID and the previous APK's signing certificate (`55546680e8d8f41fb68a37c6f3c494ac7c0da7dec62e19f4cc215b8ba1200d6c`). Its 31,672,842 bytes hash to `d9175727cce385d3f649865b9adecd6af8e7565fb5663f3fa3d849d568682827`.

## Remaining release checks

Record the exact publicBeta APK, signer/version comparison, deployed-host readiness, public two-player purchases, published asset digest and older-app updater installation after they are completed. Emulator screenshots and source tests do not establish physical-phone performance.
