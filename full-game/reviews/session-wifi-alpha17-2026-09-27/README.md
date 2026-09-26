# Alpha17 Wi-Fi acceptance

App and installed service source: `9fe335d47b5b76688c0a148f64e074af4630fbdf`, building on the verified [device-session](../device-sessions-2026-09-27/README.md) and [durable-logout](../session-revocations-2026-09-27/README.md) changes. The service JAR exactly matches the revocation candidate that passed 125 server tests and the restricted-role process checks.

## Installed candidate

Android version **17 / 0.17.0-alpha17-wifi**, existing development signing certificate, Android 8+. The APK is at `full-game/releases/0.17.0-alpha17-wifi/Tambola-Together-0.17.0-alpha17-wifi.apk`. SHA-256: `575bcd865231a3e5fe96c972c22ac6c657d95e4973beb10a8edcdb73c56dc2e4` (41,917,463 bytes). The installed emulator APK was read back and matched these exact bytes. The package contains the host's public CA, never a private key. No adb reverse or certificate-verification bypass was used.

The host runs primary schema **006**, journal schema **003**, room protocol **4**, at `https://192.168.1.4:8443`. A clean-commit preflight found no unfinished game. The guarded upgrade retained a fresh verified primary/journal backup pair and old service files, ran owner migrations/grants, and required a new healthy restricted runtime process. `deployment.json` records that initial readiness checkpoint; the separate transaction/native/final-health evidence completes its then-pending external checks. Raw backups, protected configuration and private keys remain outside the repository.

## Checks

- LAN APK and instrumentation builds, nine Android unit tests, LAN lint, signature verification and same-certificate comparison with alpha16 passed.
- Real HTTPS transactions verified purchases, exact receipt retries, device enrollment, exact session rotation replay, refusal of old access, the unchanged wallet/purchase receipt, and exact cancellation refunds. Deleted device credentials could not renew. Temporary HTTP QA profiles were deleted. Request timings are PC diagnostics, not a phone-network guarantee.
- `LanTransportTest` passed on the dedicated Android 11/API30 emulator in 0.841 seconds. Android's actual trust manager and app HTTP/WebSocket client used HTTPS/WSS, rejected cleartext, retained own-ticket privacy and displayed computer identities.
- `LanCoinGameTest` passed in **460.311 seconds** with animations enabled. The app automatically enrolled its device credential, bought six disjoint tickets, paged and marked manually, selected all eight prizes and completed **84 calls**. Four authenticated QA identities funded a **2,400-coin** pool; this full-round scenario used three passive HTTP peers and no computer opponents. Separate checks cover computer participation.
- All eight prizes settled to the native player: **3,300** final coins from 1,500 minus 600 ticket cost plus 2,400 winnings. The four wallets conserved **6,000** coins. Play Again bought three tickets (balance 3,000), then cancellation refunded exactly 300 (balance 3,300). All four profiles were deleted in cleanup; app preferences and system animation settings were restored.
- Post-test supervisor state was fresh and ready; a verified private-CA HTTPS health request returned 200. Native screenshots show the paged table/per-ticket Claim controls and the results/replay flow.

The report's final current-room call/claim counts are zero after leaving the new lobby; `finishedCalls`, settlement fields, `acceptance.json` and the successful native assertions preserve the completed-round result.

## Performance and release boundaries

FrameMetrics observed **5,284** non-first-draw frames, excluding six first draws, with no dropped/unavailable reports. Diagnostic p95 upper bucket: **50 ms**; maximum **299.12 ms**; **3,470** exceeded 16.67 ms. This debug emulator result does not meet the 60 fps target or establish physical-device performance. No restart fault was injected during this alpha17 round; the earlier alpha16 host-restart evidence remains a separate checkpoint.

The Windows firewall rule is absent and this shell is not elevated; the [administrator setup step](../../../docs/WIFI_HOST.md#one-administrator-action-for-phone-testing) is still required before physical-phone testing. Router isolation and physical Wi-Fi reachability remain unverified. Lost-device/reinstall recovery, native refill acceptance, broader layout/language checks, optimized frames, actual Windows reboot, installed-backup restore, public hosting and release signing remain open. See [the active plan](../../../docs/ONLINE_COIN_GAME_PLAN.md).

`candidate.json` identifies exact binaries/source; `acceptance.json`, raw native reports, screenshots, transaction checks and deployment metadata record the observations. `SHA256SUMS` covers evidence bytes. No access/device tokens, raw databases, private keys or protected host configuration are included.
