# 0.2.0-alpha02 validation

Date: 25 September 2026. Package `io.github.sbshrey.tambola.game`, version code 2, minimum API 26, target API 36. This is an internal offline alpha signed with an Android debug certificate. Production readiness remains unproven.

## Delivered behavior

- Android setup exposes one/two/three-house games and an explicit 90-call option. Ranked houses replace standalone full-house points. Rules explain same-call ties, previously winning tickets and how the round ends.
- The custom-prize editor covers row, column, number range, populated positions, called counts, AND/OR groups, owned-ticket ordinals and multiple-ticket requirements. It rejects impossible counts and explains incompatible ticket allowances without silently discarding a rule.
- A sample-ticket playground constructs a valid positive example when the illustrative tickets permit one; clearing calls provides an incomplete example. Tapping sample numbers explores the rule. These tickets never become the real deal.
- Setup/editor drafts survive activity recreation. Rematch retains names, computer count, ticket allowance, marking settings, terminal options and custom rules; dealing creates a fresh round.
- Claim inspection shows the locked rule, selected numbers, missing calls, matching owned tickets, exact winning tickets and the award call. Later matching tickets cannot claim an earlier award.
- Results include custom points/awards and highlight equal top scores equally. Sharing starts with a preview and excludes names unless enabled; no message is automatically sent.
- The palette now consistently uses ink/saffron/jade surfaces and controls. Preview actions and the keyboard Done action dismiss input focus so feedback is visible.

## Build and automated evidence

- **20 domain tests pass**, including 100,000 generated-ticket cases, custom-rule migration/undo/ties, and 250 varied positive-example deals. **Four Android JVM setup tests pass**, covering rematch fidelity, incompatible ticket changes, impossible selections and house/name constraints.
- **16 PostgreSQL/HTTP/WebSocket regression tests passed** after standard prize matching was moved to the same selectors used by inspection. Later domain edits only shorten displayed rule descriptions. This does not imply a hosted endpoint or Android online flow exists.
- The seven-journey Android suite passed on the dedicated Android 11/API 30 emulator after the editor/focus changes, in **181.989 seconds**. It includes a real 90-call game with a compound custom prize requiring two tickets, saved awards, and retained rematch settings; recreation in the editor; invalid-rule correction/discard; share-preview privacy; and the four original offline journeys.
- After adding the focused preview journey and rebuilding the final APK, direct instrumentation against the **exact candidate hash below** passed **8/8 tests in 182.416 seconds**, with Wi-Fi and mobile data disabled. This includes all seven journeys above plus the column-rule preview/inspection journey at normal text size.
- A separate column-rule preview/inspection/cancellation journey passed against the final APK at **360dp / 200% system font**, in **13.829 seconds**. Screenshot review confirmed readable wrapping, accessible Save/Back controls and scrollable explanations. The first direct invocation found the target package absent because Gradle had removed its test installation; installing the candidate before retrying resolved that harness setup issue.
- Build, JVM tests and `lintDebug` pass. Lint reports **zero errors and one KAPT-to-KSP migration warning**, with no new suppressions. All builds used normal dependency SHA-256 verification. The source whitespace check passes.
- Visual review found that the numeric keyboard could obscure preview feedback. Preview/selection/Done actions now clear focus, and the focused test asserts the feedback is displayed. Stable screenshot capture waits for native dialog/ripple transitions.

## Real APK upgrade evidence

The dedicated test emulator's fictional app data was reset before installing the packaged alpha01 APK. That older app created a genuine version-1 active round with **five calls and two computer-ticket marks**. The candidate alpha02 APK then updated the same installation **without clearing data**.

The new app restored the same round in a paused state. Continuing made a sixth unique call, then paused. Host-side SQLite/WAL snapshots verified the same round ID, unchanged ticket fingerprint and rules, all five original calls in order, and both old marks retained. The next save used round format **version 2**. This checks one real alpha01→alpha02 installation/update sample on API 30; it is not proof of every upgrade path, power loss, low storage or physical-device behavior.

`tools/snapshot-emulator-round.mjs` provides repeatable snapshot evidence for this purpose. It requires a named `tambola_full_game_*` emulator, stops only this app, writes to ignored test workspace storage and reports selected round metadata without printing future draw order. It refuses physical devices. The app/database copies contain fictional test data and are not included in the sharing package.

## Candidate and remaining work

Candidate file: `Tambola-Together-0.2.0-alpha02.apk`; **37,690,400 bytes**. SHA-256: `801f9655556dfa572af1618ff657bfcca240d5db24bbf0ac256cec8403d0a34d`.

The candidate uses the same debug certificate as alpha01. APK Signature Scheme v2 verifies. Manifest inspection confirms API 26/36, version code 2 and only vibration/the non-exported receiver permission; there is no Internet or microphone permission. The locally packaged APK, install guide, reports, screenshots, checksum and source-commit record live under ignored `releases/0.2.0-alpha02/`.

Native online lobby/game/reconnect flows, hosting/TLS/operations, explicit online data deletion, badges, final art/music/celebrations, Hindi UI, light theme, broader device/TalkBack/audio/performance testing, production signing and APK/AAB release validation remain unfinished. Setup/editor saved-instance-state checks do not establish draft recovery after force-stop. Text sharing was previewed and cancelled; third-party message delivery was not tested. No paid generation, cloud provisioning, public release or store submission occurred in this milestone.
