# Installed archive recovery with restricted roles

Passing run `a75f29f5aec3494a`, 27 September 2026, 00:18:19–00:19:05 UTC. The exact installed service distribution remained at source `b6d5eb6f7cc9c6c70006e4d9c422c2abe75187e0`, runtime SHA-256 `f2b39e5bb622b0bd9c368da654a9412fc3e34d617e171497a1f521b7ec80ee10`. No app/server product code or APK changed in this checkpoint.

## Installed archive proof

The drill copied the retained pre-upgrade primary archive, SHA-256 `20a8b61e103ddbf3d9e2bb9f0d69e65114edd84833da57aa64e5964911104e97` (85,485 bytes), and took a newer read-only snapshot of the installed journal (13,108 bytes). It restored both into fresh isolated databases on PostgreSQL port **55432**, while the installed host kept its databases on **55433**. Four new non-administrative login roles separated migration ownership from runtime access. The owner job migrated primary schema **006→007**; serving used restricted app roles with local fixture mode disabled.

Restricted startup replayed the installed journal from primary cursor **58** to head **68**, checked all **68 deletion intents** and preserved the one existing wallet's full ledger and refill state by canonical hash. The installed journal had **zero logout intents**; the next scenario supplies an explicit logout case within isolated copies. This is a small-history restoration proof, not a recovery-time objective or scale test.

## Controlled lifecycle and coin proof

Three fictional players were created only in the isolated service. Each enrolled a device credential, bought tickets and received an exact-retry-safe cancellation refund. After a primary snapshot, one signed out and another deleted its profile, recording two newer journal intents. The process stopped and only primary was restored.

SQL positive controls verified both old bearer hashes, device hashes and unexpired identities really existed in the restored primary before startup. The journal identity/head stayed unchanged by the primary restore. The exact installed runtime then replayed both newer records under restricted credentials:

- Both old bearer tokens and both device renewal credentials returned 401.
- The signed-out wallet survived; access expiry/revocation timestamps matched and its device hash was cleared.
- The deleted guest/wallet disappeared, while the original deletion confirmation remained exactly retryable.
- All remaining wallet/ledger data matched the saved oracle. The surviving player's original purchase and cancellation responses replayed exactly with its balance still 1,500.
- A fresh six-ticket purchase/cancellation refunded once. Another process restart retained wallet data and both exact receipts.

[Final evidence](evidence.json) includes the archive/runtime/driver hashes, stages and four process IDs. Sources are [the PowerShell wrapper](../../tools/rehearse-windows-recovery.ps1) and [the Node driver](../../tools/installed-recovery.mjs). Sensitive archives and raw SQL/API/server data were not exported.

## Cleanup and boundary

[Independent cleanup](cleanup.json) confirmed zero owned databases/roles, all recorded process IDs absent, copied sensitive archives absent, and both retained original upgrade archives unchanged. [Fresh verified HTTPS health](host-health.json) confirmed protocol 4, the same 51-JAR runtime and a current ready supervisor after the drill. No installed accounts were created or altered by the recovery fixture.

The repeatable drill uses a protected host subdirectory, reads live journal data with its restricted account, and never rotates backups, stops the live host or restores over its databases. It closes the copied-installed-backup rehearsal. Live disaster cutover, provider backup independence, actual Windows reboot, physical-phone Wi-Fi and sustained native play/performance remain separate gates in [the current plan](../../../docs/ONLINE_COIN_GAME_PLAN.md).

`artifact-hashes.json` records the retained review files; original archives, credentials and profile data are excluded.
