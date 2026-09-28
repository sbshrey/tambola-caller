# Rewarded video activation

The Android integration has the publisher IDs supplied on 28 September 2026. They belong to the internet-beta package only:

- Package: `io.github.sbshrey.tambola.game.beta`
- App ID: `ca-app-pub-1312548197553464~7883782841`
- Rewarded unit: `ca-app-pub-1312548197553464/9960291000`

Live requests require `-PtambolaLiveAds=true`. Account readiness, the published consent message and a real signed server callback must be verified before distributing that build. Beta games, daily coins and free refills work without advertising. The current published [alpha39 APK](https://github.com/sbshrey/tambola-caller/releases/tag/full-game-alpha39-power-rooms) has live advertising disabled.

## Prepare the account

1. The AdMob account and app/unit IDs are already supplied. Complete any identity/payment details requested by [AdMob](https://admob.google.com/) yourself.
2. Open rewarded unit `9960291000`. Set reward amount **1000**, reward item **coins**. The server validates both values; a differently configured reward cannot be credited.
3. Configure privacy messages in AdMob's Privacy & messaging section. The app uses UMP before requesting live ads and offers the privacy-options entry when required. Advertising ID permission is removed. Review Google's current SDK data disclosures when preparing the store's Data safety form.
4. Link a supported app-store listing and complete app readiness/ownership verification. A GitHub APK download by itself does not satisfy app readiness. For applicable stores, publish the account's exact app-ads.txt line on the developer website used by the store listing. Do not invent a publisher ID or use Google's sample line.

See [app readiness](https://support.google.com/admob/answer/10564477?hl=en-GB) and [app-ads.txt verification](https://support.google.com/admob/answer/14538460?hl=en).

## Connect the server before enabling real ads

- Run `./tools/configure-rewarded-host.ps1` for a preflight, then add `-Apply` to configure `TAMBOLA_ADMOB_REWARD_UNIT` in the PC host's encrypted runtime environment. The default is the supplied unit. The helper refuses to restart while an unfinished table exists, preserves the encrypted previous settings, and checks service health. No OAuth token belongs in the APK or source control.
- In the rewarded unit's server-side verification settings, use `https://<current-public-game-host>/admob/reward`.
- The current free quick tunnel changes its hostname when restarted. Update the AdMob callback whenever it changes; a stable named tunnel/domain is preferable before activating ads for users. The normal APK directory discovery cannot change Google's saved callback URL.
- Use AdMob's verification test and an issued reward intent to prove that the real signed callback arrives and credits exactly once. Verify the real callback's ad-unit suffix and epoch-millisecond timestamp before activation. This cannot be proved using locally generated signatures alone.
- `./tools/verify-admob-callback.ps1 -Action Prepare` creates an isolated QA profile and prints only the callback URL and opaque custom-data reference. Run AdMob's test twice using that reference, then `-Action Check` must show `confirmed=True` and `coinsAdded=1000`. Finally use `-Action Delete` to remove the owned test profile. Credentials stay encrypted locally. A saved probe must be checked/deleted before creating another. All three actions resolve the publisher's current HTTPS directory; Check reports the current callback and intent expiry. When the tunnel changes, the helper can still inspect or delete the same probe without creating another profile or reward slot. Updating this helper's saved origin does not change AdMob's console settings.
- Build `:app:assemblePublicBeta -PtambolaLiveAds=true -PtambolaFirebase=true` after account readiness and callback verification. The supplied IDs are defaults; optional `tambolaAdMobAppId` and `tambolaAdMobRewardUnit` overrides must belong to the same publisher. Debug and Wi-Fi variants never use these live IDs.

The server uses Google's Tink rewarded-ads verifier, bounded HTTPS key downloads, six-hour key refreshes and a one-minute retry throttle. An authenticated opaque intent links the video to a wallet; the client's completion callback never grants coins. Failed loads reuse an uncredited slot. Five slots per UTC date, transaction/intent uniqueness and atomic ledger credits prevent duplicate rewards. Verification may arrive after app closure: reopening the profile refreshes the wallet. Deleted profiles and their reward records are removed together. Callbacks more than one day after the intent's UTC-date boundary are rejected.

For a rejected console test, the server response and running server log include fixed reason labels such as `reward_amount`, `custom_data`, or `timestamp`. Signed callbacks report all mismatches together; unsigned callbacks never reach these field checks. No callback values, signatures, user IDs or transaction IDs are logged. Log entries are limited to one per five seconds. The Windows host allows its logs to be read while running, without restarting the public tunnel.

## Development preview

`./gradlew.bat :app:assembleDebug -PtambolaAdTest=true` enables Google's official test app/unit in debug only. Test previews never request a reward intent or award server coins. Public-beta ad requests remain disabled unless `tambolaLiveAds=true`; other release variants stay disabled. Never click live ads while testing.

## Rewarded policy review (28 September 2026)

The placement explicitly offers 1,000 virtual coins, starts only after a player taps it between games, and permits dismissal without blocking normal play. Coins have no cash-out or transfer feature. The server, not a client callback, grants the reward exactly once. The app requests current UMP consent information at launch, waits for consent before SDK initialization/ad loading, and exposes required privacy choices both beside the placement and in the game-data dialog, including before an online profile exists. The manifest removes Advertising ID permission. No text asks players to click advertisements or support the developer.

These are implementation checks against [Google's rewarded policies](https://support.google.com/admob/answer/7313578?hl=en), not an AdMob approval. Correct console rewards and working callbacks are necessary to deliver the advertised reward. The quick-tunnel callback changes after a tunnel restart; do not leave a stale callback configured.

References: [Android SDK setup](https://developers.google.com/admob/android/quick-start), [rewarded ads](https://developers.google.com/admob/android/rewarded), [UMP consent](https://developers.google.com/admob/android/privacy), [server verification](https://developers.google.com/admob/android/ssv).
