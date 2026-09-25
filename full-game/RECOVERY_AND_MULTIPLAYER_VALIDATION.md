# Alpha05 recovery and independent-client acceptance

Date: 26 September 2026. This extends the [alpha05 report](ALPHA05_VALIDATION.md); it does not replace its original evidence or make a production-release claim.

Application candidate: `Tambola-Together-0.5.0-alpha05.apk`, SHA-256 `d244c8fa746cd47fcc863a90bd309081af140bd2acacab27513414b4f60d4c6d`, 39,307,208 bytes. Application/service source is commit `779d8ffea5c945d828781c8984e184cdc36aa1d9`. This milestone changes only instrumentation, local test drivers and documentation. Both drivers compare the installed APK against the candidate before and after acceptance.

## Actual Android process termination

Two cold-process cases passed on Android 11/API 30, using the dedicated `tambola_full_game_api30` emulator and real Netty/PostgreSQL service. This is an external `am force-stop` followed by a new app process, not activity recreation.

| Case | Old → new Android PID | Committed replies dropped | Restart verification |
| --- | --- | --- | --- |
| Draw | 18776 → 18993 | 3 | Passed, 5.012 seconds |
| Profile deletion | 19101 → 19347 | 3 | Passed, 4.894 seconds |

Each seed stage creates a real offline round, calls until a ticket can be marked, marks it through the native UI and pauses it. It then creates an online room with a second fixture participant and starts a manual round. The draw/deletion action uses the native control. The proxy discards successful HTTP replies only after the service transaction commits, including transparent transport retries. The app retains its original encrypted pending action.

The seed writes an atomic, per-run ready marker containing its actual PID only after its pending data and service-side result have been verified. The external driver checks that this PID is live, stops the app, observes its disappearance and launches verification in a different PID. The seed's instrumentation interruption is expected and explicitly labelled; it is not counted as a passing JUnit test. The two restart verifications each report one passing test.

- **Draw:** the new process restores the same bearer credential, player ID, request UUID/body/revision, round ID and owned tickets. A separate service call advances the room to two calls before the UI reconnects. Native retry of the older request clears the pending action while retaining the newer two-call snapshot. Direct replay still returns the original one-call receipt; the authoritative room remains at two calls. No duplicate draw or state rollback occurs.
- **Deletion:** the new process restores the original deletion request and confirms its receipt after the profile no longer exists. The native UI displays confirmation and clears its encrypted online store. The remaining player is host, sees the deleted name redacted and can continue calling.
- **Offline isolation:** both cases restore the entire paused offline round before retry and retain its ID, cards, calls and nonempty manual marks afterward.

Final screenshots assert visible native confirmation/current-call text before capture. An earlier capture ran before Compose advanced its test clock and still showed Checking; the test now waits for the actual rendered result. No application behavior was changed to accommodate the test.

## Two independent native clients

The coordinated journey uses separate Android 11/API 30 AVDs, `tambola_full_game_api30` (`emulator-5582`) and `tambola_full_game_peer_api30` (`emulator-5584`), with separate app stores and Android Keystores. The second AVD was newly created from the already installed system image. Room creation/configuration, registration, joining, readiness, all 90 calls, rematch and cancellation use native UI actions on their respective devices; no API-only peer substitutes for the second UI.

The driver compares both clients' observations before allowing rematch: the same room/round, 90 calls in the same order, matching scores/awards and the same revealed draw audit, with two private owned tickets per device and disjoint ticket IDs. A second native round must retain the rules, show newly dealt number grids, start with no calls, then show cancellation and two saved online results on both devices. Ticket IDs identify player/ordinal slots inside each round; round ID provides the rematch boundary.

The first coordinated run completed its full 90-call comparison, then failed a test assertion that incorrectly required globally new ticket IDs on rematch. Source inspection confirmed the intended player/ordinal identity scheme. The corrected acceptance checks the new round ID and actual number grids; no application logic was changed to satisfy that incorrect assertion.

The final coordinated run passed: host instrumentation **1/1 in 39.465 seconds**, guest instrumentation **1/1 in 31.407 seconds**. Both completed the same 90-call round, agreed on calls, scores, seven prizes and the revealed draw audit, and retained two private tickets each. Screenshot review confirmed identical results (Bina 165 points, Asha 130) with different owned cards and the appropriate host/guest controls. The rematch had a new round ID and newly dealt number grids with retained rules. Both native UIs showed its cancellation and two saved online results. Six screenshots record playing, completed and rematch-result states across the two devices.

The driver restored and verified original animation settings, removed its ADB mappings and stopped its owned Java service. The newly created peer emulator was stopped after evidence capture; the original emulator and PostgreSQL cluster remain available.

## Repeatable commands and boundaries

Use only dedicated `tambola_full_game_*` emulators with fictional data. These tests reset their local online profiles and create/cancel fixture games. Cold-process tests deliberately terminate the app; do not invoke their seed methods as ordinary unattended tests. Their second participant's credentials stay encrypted on-device; tokens and witness files are not included in the evidence package.

From `full-game/`, set the explicit `TAMBOLA_TEST_DATABASE_URL`, `TAMBOLA_TEST_DATABASE_USER` and `TAMBOLA_TEST_DATABASE_PASSWORD` for the loopback `tambola_test` database described in [the service guide](server/README.md). Build the service distribution and both debug APKs, then install both APKs on each required emulator:

```powershell
.\gradlew.bat :server:installDist :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --console=plain
node tools/android-process-recovery.mjs --label cold-process-alpha05-final
node tools/android-native-pair.mjs --label native-pair-alpha05-final
```

The recovery driver owns loopback ports 8080/8081/8082; the native-pair driver owns 8080. They require those ports and corresponding ADB mappings to be free, start only their own fixture processes and stop those handles afterward. They restore and verify each emulator's original animation settings and remove only mappings they created. PostgreSQL and pre-existing emulator processes stay untouched. `--kind draw` or `--kind delete` can narrow a recovery rerun. The ordinary `android-smoke.mjs` suite excludes both externally coordinated classes rather than reporting deliberately skipped/terminated stages as passing gameplay tests.

The final test APK build/lint passed. Lint retains zero errors and one KAPT-to-KSP migration warning. With two emulators running, the instrumentation build used a 1,536 MB Gradle heap to limit local memory pressure. The application's earlier 15-case full regression, 64 passing JVM/service reports, optimized release compilation, signature checks and enlarged-text acceptance remain separately recorded in alpha05; they were not all rerun for test-only additions.

A log-only instrumentation discovery check found the same 15 ordinary tests with both externally coordinated classes excluded. This checks test selection only; its `OK (15 tests)` output is not another executed regression pass. The separate evidence addendum under `releases/0.5.0-alpha05-recovery/` includes only the final runs, screenshots, this report, build/source metadata and checksums. It preserves the original alpha05 package unchanged and contains no session/witness files or database credentials.

These checks establish local process restart and two independent emulated native clients. They do not establish low-memory OS eviction during an unfinished disk write, mobile network switching/loss, different physical OEMs, hosted TLS, service capacity, backup restoration/deletion suppression, or production-signed behavior. Final UI/audio/localization, device/accessibility/performance acceptance, hosted operations and signing remain in the full production plan.
