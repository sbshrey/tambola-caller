# Rewarded video activation

The integration is implemented but live ads are disabled by default. There is no AdMob account or publisher configuration yet. Beta games, daily coins and free refills work without advertising.

## Prepare the account

1. Sign in at [AdMob](https://admob.google.com/) and create your account. Complete the account's identity/payment details yourself.
2. Add the Android app, package `io.github.sbshrey.tambola.game.beta`, initially as unpublished. Obtain the **app ID** (`ca-app-pub-…~…`) and create a **rewarded ad unit** (`ca-app-pub-…/…`). Set reward amount **1000**, reward item **coins**.
3. Configure privacy messages in AdMob's Privacy & messaging section. The app uses UMP before requesting live ads and offers the privacy-options entry when required. Advertising ID permission is removed. Review Google's current SDK data disclosures when preparing the store's Data safety form.
4. Link a supported app-store listing and complete app readiness/ownership verification. A GitHub APK download by itself does not satisfy app readiness. For applicable stores, publish the account's exact app-ads.txt line on the developer website used by the store listing. Do not invent a publisher ID or use Google's sample line.

See [app readiness](https://support.google.com/admob/answer/10564477?hl=en-GB) and [app-ads.txt verification](https://support.google.com/admob/answer/14538460?hl=en).

## Connect the server before enabling real ads

- Configure `TAMBOLA_ADMOB_REWARD_UNIT` in the PC host's protected runtime environment. Use the same rewarded unit in the APK. No OAuth token belongs in the APK or source control.
- In the rewarded unit's server-side verification settings, use `https://<current-public-game-host>/admob/reward`.
- The current free quick tunnel changes its hostname when restarted. Update the AdMob callback whenever it changes; a stable named tunnel/domain is preferable before activating ads for users. The normal APK directory discovery cannot change Google's saved callback URL.
- Use AdMob's verification test and an issued reward intent to prove that the real signed callback arrives and credits exactly once. Verify the real callback's ad-unit suffix and epoch-millisecond timestamp before activation. This cannot be proved using locally generated signatures alone.
- Build with `-PtambolaAdMobAppId=<app-id> -PtambolaAdMobRewardUnit=<rewarded-unit>`. Both values must belong to the same publisher. Build the publicBeta variant only after account readiness and callback verification.

The server uses Google's Tink rewarded-ads verifier, bounded HTTPS key downloads, six-hour key refreshes and a one-minute retry throttle. An authenticated opaque intent links the video to a wallet; the client's completion callback never grants coins. Failed loads reuse an uncredited slot. Five slots per UTC date, transaction/intent uniqueness and atomic ledger credits prevent duplicate rewards. Verification may arrive after app closure: reopening the profile refreshes the wallet. Deleted profiles and their reward records are removed together. Callbacks more than one day after the intent's UTC-date boundary are rejected.

## Development preview

`./gradlew.bat :app:assembleDebug -PtambolaAdTest=true` enables Google's official test app/unit in debug only. Test previews never request a reward intent or award server coins. Release/publicBeta remain disabled unless real IDs are explicitly supplied. Never click live ads while testing.

References: [Android SDK setup](https://developers.google.com/admob/android/quick-start), [rewarded ads](https://developers.google.com/admob/android/rewarded), [UMP consent](https://developers.google.com/admob/android/privacy), [server verification](https://developers.google.com/admob/android/ssv).
