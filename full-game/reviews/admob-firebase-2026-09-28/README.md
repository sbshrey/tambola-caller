# AdMob and Firebase activation — 28 September 2026

The user supplied real public AdMob identifiers and reauthenticated Firebase. The internet-beta package now defaults to app ID `ca-app-pub-1312548197553464~7883782841` and rewarded unit `ca-app-pub-1312548197553464/9960291000`. The `tambolaLiveAds=true` build flag enables requests only in `publicBeta`. Debug previews retain Google's official sample IDs and never grant coins.

Consent is shared per activity, refreshed at launch and required before initialization/loading. Required ad privacy choices are available in the game-data dialog even without an online profile. Both server and loaded-ad reward amounts must match the advertised 1,000 coins before showing a real video. Both Advertising ID manifest permissions are removed; the disclosure accurately describes SDK/device identifier processing instead of promising no identifiers on every Android version. The policy review is documented in [AdMob setup](../../ADMOB_SETUP.md).

## Verified

- The final optimized alpha37 candidate compiled, lint passed and the Crashlytics release mapping uploaded. [Candidate identity](candidate.json): source `5a193a194a83adf3933092e9df41f5077e9edc93`, SHA-256 `7d79f8b290a4a4f015f9343997f7fbff0103ce02a7c70804cb3429f57b7e8a4b`, 31,611,442 bytes. APK inspection confirmed both supplied ad IDs, the real Firebase app resource, non-debuggable packaging, the existing upgrade-compatible signing certificate, collection disabled by default, both Advertising ID permissions absent and no probe activity/code/logging flag.
- App unit tests: 18 passed. Native API 30 emulator: two privacy/disclosure tests plus Google's official test-video load/display/dismiss flow passed, [3 tests](native-tests.txt). No live ad was clicked. This test does not assert a full rewarded completion or real SSV credit.
- Firebase project `tambola-together-beta-sbshrey` and its beta Android registration were created through the refreshed CLI session. Configuration stays in ignored `app/src/publicBeta/google-services.json`; no billing account or paid backend was enabled.
- The [Crashlytics reporting API](crashlytics-api.json) confirmed exactly one intentional fatal event from a separate `alpha37-firebase-probe` build.
- The [Performance check](performance-delivery.json) logged `online_request`, `join_table`, `claim_prize`, and `login_rewards`, observed HTTP 200 from Firebase's logging endpoint, and verified no new custom traces after opting out. These were synthetic actions through the app wrapper, not a completed multiplayer session. Individual Performance dashboard trace visibility remains uninspected.
- The PC reward configuration helper initially refused to restart an unfinished table. After it closed, the helper backed up encrypted settings, configured the supplied unit, restarted the idle game service and passed health checks. The quick tunnel stayed running at `https://farmer-outlets-routines-story.trycloudflare.com`.
- [Public HTTPS economy checks](public-economy.json) passed after configuration, including reward-intent retries without credit, expanded prize pools, purchases/refunds and deletion of those two QA profiles.
- The exact optimized candidate passed the [public Internet purchase/refund smoke test](public-candidate-smoke/validation.json) in 24.301 seconds, including cold restart and deletion of its owned profile. This used the stable app directory and real public HTTPS without adb reverse. The emulator and build daemons were stopped after verification.

## Remaining external checks

The dedicated AdMob callback-test profile remains available for the user's console test. Its credential record is encrypted under ignored `.test-workspace/admob-ssv-probe.secrets`. Use `tools/verify-admob-callback.ps1 -Action Check`, then `-Action Delete` after recording verification. At preparation the wallet gained zero ad coins, as intended. Real signed callback delivery and deduplication have not yet been confirmed. The reward amount/item, published UMP message, app-store/readiness review and any app-ads.txt requirements need account-console verification. Browser control returned `unsupported Codex auth method: apikey`; no console state was inferred.

Alpha36 was public at the time of this candidate review; [alpha39](../power-rooms-2026-09-28/README.md) was subsequently published with live ads disabled. Do not publish an ad-enabled candidate until its reward path and AdMob readiness are verified. A quick-tunnel restart changes the callback hostname and requires updating Google's saved URL.

## Probe history and limits

The first disposable Firebase probe declared its activity but did not include the added Kotlin source directory, causing an activity-not-found crash before opting in. A subsequent init-script attempt used the wrong Groovy overload. The corrected Android variant Kotlin-source registration built successfully and produced the verified intentional crash. These were probe-build failures; the normal application source has no test-crash entry point. Raw diagnostic logs remain ignored locally. No physical-phone/mobile-data test or real ad impression is claimed.

## Current callback follow-up

After alpha39 publication, browser inspection still reports `unsupported Codex auth method: apikey`. The callback helper now resolves the same validated publisher directory as the APK for Prepare, Check and Delete, so a tunnel rotation no longer strands the encrypted QA probe. The existing probe was recovered through the new origin without creating a new profile or intent. A second Check confirms the saved origin is current; no Google callback or ad credit is present. See `callback-recheck.json`. The user has been given the exact console test fields and asked for app readiness/consent status. Live ads remain incomplete and disabled.
