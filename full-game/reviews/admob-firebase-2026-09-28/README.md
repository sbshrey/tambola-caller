# AdMob and Firebase activation — 28 September 2026

The user supplied real public AdMob identifiers and reauthenticated Firebase. The internet-beta package now defaults to app ID `ca-app-pub-1312548197553464~7883782841` and rewarded unit `ca-app-pub-1312548197553464/9960291000`. The `tambolaLiveAds=true` build flag enables requests only in `publicBeta`. Debug previews retain Google's official sample IDs and never grant coins.

Consent is shared per activity, refreshed at launch and required before initialization/loading. Required ad privacy choices are available in the game-data dialog even without an online profile. Both server and loaded-ad reward amounts must match the advertised 1,000 coins before showing a real video. Both Advertising ID manifest permissions are removed; the disclosure accurately describes SDK/device identifier processing instead of promising no identifiers on every Android version. The policy review is documented in [AdMob setup](../../ADMOB_SETUP.md).

## Verified

- The configured optimized alpha37 build compiled, lint passed and the Crashlytics release mapping uploaded. Final candidate identity is recorded separately after the final build.
- App unit tests: 18 passed. Native API 30 emulator: two privacy/disclosure tests plus Google's official test-video load/display/dismiss flow passed, [3 tests](native-tests.txt). No live ad was clicked. This test does not assert a full rewarded completion or real SSV credit.
- Firebase project `tambola-together-beta-sbshrey` and its beta Android registration were created through the refreshed CLI session. Configuration stays in ignored `app/src/publicBeta/google-services.json`; no billing account or paid backend was enabled.
- The [Crashlytics reporting API](crashlytics-api.json) confirmed exactly one intentional fatal event from a separate `alpha37-firebase-probe` build.
- The [Performance check](performance-delivery.json) logged `online_request`, `join_table`, `claim_prize`, and `login_rewards`, observed HTTP 200 from Firebase's logging endpoint, and verified no new custom traces after opting out. These were synthetic actions through the app wrapper, not a completed multiplayer session. Individual Performance dashboard trace visibility remains uninspected.
- The PC reward configuration helper initially refused to restart an unfinished table. After it closed, the helper backed up encrypted settings, configured the supplied unit, restarted the idle game service and passed health checks. The quick tunnel stayed running at `https://farmer-outlets-routines-story.trycloudflare.com`.
- [Public HTTPS economy checks](public-economy.json) passed after configuration, including reward-intent retries without credit, expanded prize pools, purchases/refunds and deletion of those two QA profiles.

## Remaining external checks

The dedicated AdMob callback-test profile remains available for the user's console test. Its credential record is encrypted under ignored `.test-workspace/admob-ssv-probe.secrets`. Use `tools/verify-admob-callback.ps1 -Action Check`, then `-Action Delete` after recording verification. At preparation the wallet gained zero ad coins, as intended. Real signed callback delivery and deduplication have not yet been confirmed. The reward amount/item, published UMP message, app-store/readiness review and any app-ads.txt requirements need account-console verification. Browser control returned `unsupported Codex auth method: apikey`; no console state was inferred.

Alpha36 remains the public APK. Do not publish the alpha37 live-ad candidate until its reward path and AdMob readiness are verified. A quick-tunnel restart changes the callback hostname and requires updating Google's saved URL.

## Probe history and limits

The first disposable Firebase probe declared its activity but did not include the added Kotlin source directory, causing an activity-not-found crash before opting in. A subsequent init-script attempt used the wrong Groovy overload. The corrected Android variant Kotlin-source registration built successfully and produced the verified intentional crash. These were probe-build failures; the normal application source has no test-crash entry point. Raw diagnostic logs remain ignored locally. No physical-phone/mobile-data test or real ad impression is claimed.
