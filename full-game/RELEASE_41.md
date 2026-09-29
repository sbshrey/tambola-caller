# Tambola Jalsa beta v41 candidate

Status: release acceptance in progress. No v41 APK has been published. The hosted backend is running protocol 9 from source `0f08ad9`.

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
- [ ] Real optimized native round through public HTTPS/WSS, correct settlement, new results screen, and replay/refund checks.
- [ ] Immutable GitHub APK asset, matching digest, and update feed verification.

Earlier focused evidence is documented in ROUND_SUMMARY.md, MARKING.md, POWER_ROOMS.md, and designs/round-redesign-v1/native-progress.md. Those checks do not substitute for the release gates above. Saved editable design and video artifacts were reviewed before native implementation; Figma publication remains unavailable under the previously observed tool quota, and the user authorized continuing from the saved design.

The public beta still uses its existing development signing identity and temporary hosted endpoint. This release does not establish Play Store readiness or physical-phone performance.

## Candidate validation notes

The broad suite exposed a date-dependent migration fixture in ProfileDeletionTest: its fixed September gameplay clock created sessions that PostgreSQL's real migration clock now considered expired. The fixture now initializes from the database clock before registration. Production migration and credential expiry behavior are unchanged; all seven tests in the affected group passed on rerun. Counts are retained in `reviews/release-v41/server-initial.json` and `server-fixture-recheck.json`.

The acceptance driver now matches the new room capability, eight-second pace, 30-50-player roster, variable ticket funding, highlighted results and six-ticket replay confirmation. The two-stage BetaUpgradeTest passed wallet/preference/session continuity and cleanup. Public power acceptance passed for the sampled PRIZE_BONUS; its code also supports explicit Shield activation, but Shield was not sampled in that run. The complete native round is still running.

Hosted rollout: both databases were backed up before migration. The initial overall-health deadline failed because Caddy was bound to the obsolete address 192.168.1.2. A combined configuration command was blocked by tool policy; the successful narrower repair changed only non-secret host/listener configuration to the assigned Wi-Fi address 192.168.1.4 and restarted the owned service. Protected runtime credentials were not changed. The supervisor then reported backendReady/tlsReady/status ready, and the backend reported protocol 9. Local LAN invitation-origin migration was not verified; Internet Beta uses its public directory and friends links. Public legacy validation required its existing configured-ad-intent mode; the candidate app still has live ads disabled.

The first optimized full-round attempt exposed a driver setup mismatch: the app defaults to Power, while the passive QA peer requested Classic. That attempt removed its QA profiles. The driver now explicitly selects Classic for its settlement journey. The next attempt reached active play with 47 players, ticket quantities 1-6, a pool of 18,500, and six owned tickets covering all 90 numbers. Full-round completion remains pending.
