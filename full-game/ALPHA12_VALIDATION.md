# Alpha12 invitation validation

Date: 26 September 2026. Branch: `shrey/full-tambola-game`. Candidate version **0.12.0-alpha12 / code 12**. This is an internal debug-signed candidate; public service hosting, release signing and physical-device acceptance remain open.

## What changed

Private-room sharing now includes a configured-origin invitation URL and a room code. English/Hindi review cards support pre-registration, existing profiles, current/different rooms, invalid links, failed joins and outstanding commands. Opening a link never joins, registers, leaves or replaces a pending command. Native parsing rejects foreign origins, user info, query/fragment payloads, malformed/encoded paths and invalid codes. Only a bounded code enters saved state; launch extras are removed before saved-state models are created.

The service optionally serves a bilingual responsive invitation page, a browser installation fallback and public signing association JSON. The page has no room lookup, guest registration, cookies, scripts or third-party resources. Canonical configured origins, restrictive CSS-hash CSP, no-referrer, no-store, nosniff and noindex headers protect the landing page. Signing association is absent without configured public fingerprints. [INVITES.md](INVITES.md) records configuration, behavior and hosted acceptance.

Online actions remain pinned at ordinary phone sizes. At font scale 1.3 or higher, or below 480dp usable window height, the same controls scroll with the room content. The invitation and pending-action recovery remain ahead of those controls. This preserves readable text and access to each action without letting the action panel consume the reading area.

## Candidate identity and checks

| Item | Evidence |
| --- | --- |
| Debug APK | **42,740,471 bytes**, SHA-256 `5fcc685d5e253d7b674208a02efbec4726b0f25810df419745ddfd056d9e5fc9` |
| Instrumentation APK | SHA-256 `c9b34f7454a3a37df8f19dd99707321e6f17f1ca66c7724a09b762a5ccd12d1a` |
| Signing | Existing debug certificate, APK v2 verified; certificate SHA-256 `55546680e8d8f41fb68a37c6f3c494ac7c0da7dec62e19f4cc215b8ba1200d6c` |
| Service JAR | SHA-256 `6a7c8dcb35e44a9f2f8b4ed0c96f51ffc01a61c9ea5a6d093719a86fb639c0d0` |
| Service ZIP | SHA-256 `b3c4de927228dd58f4253ef4cbc44b060378f5b761c0dc63c7bde8e39d00f42b`; CRC and bundled application JAR verified |
| Full native suite | **34/34 passed in 354.948 s**, fresh API 30/x86 emulator with the isolated service and response-loss proxy |
| Focused native cases | Three invitation journeys **18.779 s**; cold-launch/external-extras case **3.963 s**; final candidate |
| Large text and process recovery | 360dp / 200% text: **3/3 in 23.950 s**, including full-height visibility of the invitation explanation and controls; 360×450dp / 100% text: **3/3 in 23.465 s**; original density, size, font and animation settings restored; cold/warm BROWSABLE delivery and background process death/relaunch passed (**PID 16982 → 17115**); saved invitation retained and current-room review confirmed with keyboard hidden |
| Debug validation | Responsive APK/test APK, six app JVM tests and lint **1m 59s**; final expanded visibility assertion/test APK and lint **54 s** |
| Release validation | Optimized unsigned APK/AAB and lint **4m 5s**, placeholder `https://ROOMS.example`, two-processor/1.5 GiB JVM and one worker; manifest host normalized to `rooms.example` |
| Unsigned build-check identities | APK `3f98ac9222dce38bf8305877af16ed47cdc0c505dc45f378333888d49a617690`; AAB `73828141b833ccc3fe2118c45110256383a87d7129f070f40e0c2cca6b16a0ac`; placeholder endpoint, not deployable production artifacts |
| Static checks | Both lint reports: **zero errors**, two existing locale/KAPT warnings; **637** English/Hindi resources pass key/plural/placeholder/Unicode checks |
| Packaged media | All **270** voices and **five** uncompressed WAVs match manifests/source; eight native libraries match alpha11; 16 KB ZIP alignment passes |
| Bounded secret scan | **747** APK entries checked for credential/private-key patterns and the isolated fixture password; no matches |

Installed APK/test-APK readbacks match the candidate hashes. Accepted unit reports contain **25 domain, 14 client, 62 service/PostgreSQL and six app cases**, with zero failures/errors/skips. The domain engine is unchanged. The service distribution and unit/HTTP evidence use the recorded JAR; earlier container/load evidence retains its original identities and is not reassigned to this service update.

The shared protocol remains **2**, offline round format **3**, local Room database **1**. No runtime permissions or dependencies were added. No OpenAI request or paid generation was made for this slice. The app continues to package the existing 270 voices and five original sound files; it contains no runtime AI key.

## Evidence boundaries and investigations

- The Windows host rebooted at **08:52:44** while the test-package rebuild was incomplete. That APK was not a valid ZIP and its build log was truncated. The Gradle cache access index was quarantined, the project resource-hash cache was regenerated, and the source was rebuilt. PostgreSQL completed WAL recovery and returned ready. The former API 30 emulator could not boot because its Android runtime-permissions XML was corrupt; its disk files were preserved and a fresh dedicated `tambola_full_game_alpha12_api30` device was created. Final native results use the rebuilt APK on that fresh device, not the interrupted environment. The earlier pre-reboot screenshots/checks are investigation evidence only.

- Service regression: **62 tests**, including six new configuration/HTTP cases, pass in a **3m 41s** Gradle run using isolated PostgreSQL. Client: **14 tests**, including three new parser cases, pass. Domain/application unit counts and final native results are recorded above. Main/journal schemas are unchanged.
- Four local Chromium renders (360px phone, desktop, dark phone and installation fallback) passed DOM/header checks and visual review. No external requests, CSP errors or horizontal overflow were observed. Reduced-motion CSS was checked. This does not prove Chrome-to-Android handoff or a public domain association.
- The first instrumentation compilation used two incorrect test-only type names (`Connection` package and `GameMode.SOLO`); those were corrected. The next native run exposed removal of the launcher category when sanitizing launch intents. Android's ActivityScenario rejected lifecycle events because the activity intent no longer matched its launcher intent; two cases failed before the run was intentionally stopped. Normal launcher filter metadata is now retained with extras removed. Consumed VIEW launches are observed through the real activity lifecycle in the dedicated cold test, since their filter identity intentionally changes after consumption. Final checks use the corrected candidate; the earlier incomplete logs and initial APK identity are kept separately.
- Initial cold/warm/process-restoration checks used an intermediate APK and are retained as investigation evidence. Final candidate cold-launch and process-death evidence is recorded separately. No earlier alpha's device matrix, load, Docker image or physical-device claim is assigned to this APK/JAR.
- The first focused bilingual invite run passed, but its cleanup closed the activity before restoring the persisted English fixture baseline. A subsequent separately launched ordinary suite started in saved Hindi and failed English-label selectors; that run was stopped. The invite fixture now restores English while the activity is alive, then closes it and resets the override. This changes test isolation, not the APK's locale behavior. The incomplete run remains separate from final acceptance.
- On the fresh emulator, the first large-text run passed all three cases, but Android materialized its previously implicit default font setting as explicit `1.0` during restoration. The original harness rejected that representation change. Raw settings and that report are retained; the helper now recognizes only this equivalent default and continues to require density/animation equality. Final large-text acceptance uses a separate successful run.

- Visual review of the earlier `5fb925…` candidate exposed a real large-text layout issue despite its 34 functional cases passing: fixed online controls occupied most of the page and clipped the invitation. That candidate's reports/screens are retained under investigation. The final responsive candidate has its own hashes, full-suite results, normal/200%/short-window views and explicit clipped-versus-unclipped text-height assertions. An initial test assertion used a nonexistent DpRect height property; that compilation failed and was corrected to compare pixel bounds. No failing run is counted as acceptance.

The exact owned Java service/proxy processes (12092/14512) and both ADB reverse mappings were removed after validation; ports 8080–8082 were confirmed closed. The dedicated emulator and isolated PostgreSQL remain available for the continuing goal. Earlier release packages and the damaged old AVD disk were preserved.

## Remaining production acceptance

Choose and deploy the actual service domain, TLS ingress, provider budget, production secrets/roles, independent deletion-journal backups, monitoring and alert delivery. Match a production-signed APK/Play signing certificate to public `assetlinks.json`, and prove verified App Links plus browser installation fallback on physical phones. Finish independent-network multiplayer, broader automatic-call/fault/soak and large-history deletion, bounded journal retirement, dependency/image advisory review, ARM64/16KB devices, TalkBack, Hindi editorial/listening/audio routing/performance, public privacy/support and store distribution checks. See the [full plan](../docs/FULL_GAME_PLAN.md) and [progress ledger](../docs/FULL_GAME_PROGRESS.md). No remote CI, public deployment or store release occurred in this slice.
