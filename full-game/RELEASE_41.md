# Tambola Jalsa beta v41

Current scope, 2026-09-29: the user explicitly instructed "ignore the design and continue the implementation". Figma publication is therefore waived and no longer a completion gate. The implemented round redesign is released in beta v41 with the validation below. Historical Figma quota notes remain as records, not pending work.

Status: v41 APK published to GitHub and the real app updater verified on the owned emulator. The hosted backend is running protocol 9 from source `0f08ad9`.

## Changes

- Landscape round with a persistent left number board, compact recent calls, and two tickets on the right. Right-side arrows navigate up to six tickets.
- Random power preview before five correct manual marks; ready glow and one-tap activation. Shield now explicitly arms on the selected eligible ticket. The round has no bottom power/HP strip.
- Claim panel keeps the board visible and shows remaining winning places, total places, shared prize amounts, and current-call ties.
- Queued marks retain ticket geometry and survive delayed/lost responses and process restart without duplicate power progress.
- New redesigned rounds call every eight seconds. Older clients and existing rounds retain compatible timing.
- Progressive quick-play rosters of 30-50 players, with one to six tickets per player.
- Scrolling end-of-round rankings, equal-total ranks, a highlighted own row, prize/bonus totals, and confirmed replay using the previous ticket quantity.
- The display name is Tambola Jalsa. Package ID, signer, profile data, and the update asset naming contract remain unchanged.

Prize allocation is unchanged: five smaller schemes each receive 10% of the pool, and Full House receives 50%. Published totals are shared pools, with winners' actual settled shares shown in the results screen.

## Release gates

- [x] Full server/domain/client coverage and Android unit tests: 204 of 205 server tests passed initially; all seven tests in the corrected profile-deletion group then passed. Domain 61, client 47, Android unit 34 passed.
- [x] Optimized signed publicBeta APK and matching native acceptance driver build.
- [x] Same-signer v40-to-v41 APK comparison and successful in-place installation of the same package.
- [x] Upgrade retains the owned test profile, wallet, six-ticket preference, and authenticated purchase/refund. Native preparation passed in 8.806s; verification and cleanup passed in 11.734s.
- [x] Committed server distribution, idle-room preflight, database backup, migration and healthy hosted restart. The obsolete LAN listener binding required repair, described below.
- [x] Public HTTPS legacy compatibility and new power-preview/activation checks. The sampled power was PRIZE_BONUS; this run does not independently prove all three powers.
- [x] Real optimized native round through public HTTPS/WSS, correct settlement and new results screen; remaining purchase/refund checks passed separately after fixing a stale driver label assertion. See details below.
- [x] Immutable GitHub APK asset, matching digest, and real v40-to-v41 update feed/download/Android installation verification (31.206s).

Earlier focused evidence is documented in ROUND_SUMMARY.md, MARKING.md, POWER_ROOMS.md, and designs/round-redesign-v1/native-progress.md. Those checks do not substitute for the release gates above. Saved editable design and video artifacts were reviewed before native implementation; Figma publication remains unavailable under the previously observed tool quota, and the user authorized continuing from the saved design.

The public beta still uses its existing development signing identity and temporary hosted endpoint. This release does not establish Play Store readiness or physical-phone performance.

## Candidate validation notes

The broad suite exposed a date-dependent migration fixture in ProfileDeletionTest: its fixed September gameplay clock created sessions that PostgreSQL's real migration clock now considered expired. The fixture now initializes from the database clock before registration. Production migration and credential expiry behavior are unchanged; all seven tests in the affected group passed on rerun. Counts are retained in `reviews/release-v41/server-initial.json` and `server-fixture-recheck.json`.

The acceptance driver now matches the new room capability, eight-second pace, 30-50-player roster, variable ticket funding, highlighted results and six-ticket replay confirmation. The two-stage BetaUpgradeTest passed wallet/preference/session continuity and cleanup. Public power acceptance passed for the sampled PRIZE_BONUS; its code also supports explicit Shield activation, but Shield was not sampled in that run. The native round and targeted remainder are complete, as detailed below.

Hosted rollout: both databases were backed up before migration. The initial overall-health deadline failed because Caddy was bound to the obsolete address 192.168.1.2. A combined configuration command was blocked by tool policy; the successful narrower repair changed only non-secret host/listener configuration to the assigned Wi-Fi address 192.168.1.4 and restarted the owned service. Protected runtime credentials were not changed. The supervisor then reported backendReady/tlsReady/status ready, and the backend reported protocol 9. Local LAN invitation-origin migration was not verified; Internet Beta uses its public directory and friends links. Public legacy validation required its existing configured-ad-intent mode; the candidate app still has live ads disabled.

The first optimized full-round attempt exposed a driver setup mismatch: the app defaults to Power, while the passive QA peer requested Classic. That attempt removed its QA profiles. The driver now explicitly selects Classic for its settlement journey. The next attempt played with 47 players, ticket quantities 1-6, a pool of 18,500, and six owned tickets covering all 90 numbers.

The second run completed the actual round after 71 calls: independently calculated prize shares conserved all 18,500 coins, the player won 2,082, and the wallet reached 51,982. The new summary highlighted rank 3 and showed Early five / Full house. A cold process restart retained 20 marked numbers. Summary confirmation retained six tickets. The driver then failed an obsolete expectation that the Play button itself contains the price. Its report remains marked failed in `native-round-settlement.json`; it is not presented as a wholly passing instrumentation test. The assertion now reads the separate `lobby-ticket-cost` label. The focused purchase/refund test passed in 18.269s on the unchanged release APK, checking six/three-ticket price labels, a three-ticket purchase, exact refund, cold wallet recovery, saved quantity and QA-profile cleanup. Evidence: `native-purchase-refund.json`. No application change was necessary for these driver fixes.

`native-round.mp4` is a 16-second recording of the optimized public round, decoded successfully and inspected through frames at 2/8/14 seconds. It complements the original pre-implementation design videos. Figma access was checked again during release acceptance and still returned the Starter-plan MCP quota error; Figma publication is not complete.

## Published update acceptance

Release: https://github.com/sbshrey/tambola-caller/releases/tag/full-game-alpha41-round-redesign (source tag 973db37). GitHub reports 31,620,074 bytes and the candidate SHA-256. The owned API 30 emulator detected the live release from v40, downloaded and verified it, granted install permission, confirmed Android installation and reopened v41 with its expected wallet. The installed base APK was pulled and hashed: it matches the published asset exactly. The native updater test passed in 31.206s. Two earlier driver attempts exposed Android permission-return recreation and installer completion timing; the driver now handles both, with no release APK changes. Existing-profile, session and six-ticket persistence were proved separately by the earlier in-place upgrade test. See published-updater.json and beta-v41-public-updater.txt.
