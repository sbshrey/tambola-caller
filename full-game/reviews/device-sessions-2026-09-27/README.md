# Device-session continuity checkpoint

Source is the change committed with this archive, based on `d4f0399e6667ce682342e2beacf4c684d6b80dbb`. This candidate adds schema 006; room protocol 4 and the guest-registration response remain unchanged. It is not deployed to the installed Wi-Fi host and does not replace the previously shared alpha16 Wi-Fi APK.

## Behavior verified

- A separate random device credential is persisted in the encrypted Android store before enrollment. Only its hash is stored in PostgreSQL. The same guest wallet survives expiration of its seven-day access token and cleanup. A simulated 90-day return retained 2,350 coins, including an earned-credit fixture, with no second starter grant.
- Renewal persists a new random access token and expected revision before HTTP. Identical retries return the same session; concurrent competing replacements conflict; old requests cannot roll a newer session back. The old access token stops working. Wallet, marks, owned tickets and purchase request identities stay attached to the same profile.
- SQL rollback preserves the old session. Storage failures prevent unsaved enrollment/rotation, and a failed response save retains the original intent for recovery. Incorrect player/token/revision responses cannot overwrite the local identity.
- Deletion settles any interrupted rotation, then retains the same token and request ID for confirmation. Logout normally revokes the device proof. A real disposable primary-database `pg_dump`/`pg_restore` with the independent journal retained could not reactivate a deleted device profile or wallet.
- The native fault test purchased six tickets (900 coins remaining), marked a called number, received a fixture-injected wallet 401, lost a genuinely committed renewal response, recreated its Activity, and rejoined the same hand with the mark/balance intact. It then lost and retried deletion confirmation with the same proof. Test profiles were deleted; owned fixture processes and adb mappings were removed.

## Checks

The full JVM run passed 207 tests: domain 50, client 31, server 117, Android app unit tests 9. The archive includes class-level counts with zero failures, errors or skips. Server checks include real restricted login roles and real backup/restore, using isolated test databases/schemas. The final Android debug/test builds, app unit checks and lint passed.

Native acceptance includes `DeviceSessionGameTest` and both `CoinGameTest` cases. The latter verify purchase/cancellation/refund, independently authenticated peer tickets, manual marking, the locked House Two picker, and purchase-response-loss recovery. The final native logs identify the runs after the account-exit lifecycle guards were tightened.

`candidate.json` records the exact internal debug APK checksum and endpoint. It is a loopback test candidate, not a phone installer for the running LAN server. `device-session-native.json` contains only sanitized result flags/counts. No credentials, tokens, database archives or private keys are included. `SHA256SUMS` preserves raw evidence bytes.

## Release boundaries

The 90-day test advances a controlled service clock. The native test injects its first 401 and covers Activity recreation; app process-death, physical phones and calendar-time waiting are not established by it. This change has not undergone the installed-host schema upgrade or LAN acceptance yet.

Continuity works on the same installation. Lost-device/reinstall recovery is still required. Logout-only revocation currently lives in the primary database; its protection across an old primary restore remains a release gate, separate from the demonstrated independent-journal protection for profile deletion. The installed alpha16 host remains at schema 005 from `0b4c8de948d4925975d4e772e4b8ac1b458d65ec` and was healthy over verified TLS during these checks.

The earlier frame-time result (debuggable emulator p95 51 ms) remains unresolved. Native refill, broader layout/language checks, physical-phone Wi-Fi, installed-host backup restoration, reboot behavior, public hosting/signing and other release gates remain tracked in the main plan.
