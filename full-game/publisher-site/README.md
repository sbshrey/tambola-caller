# Tambola publisher website

This Hosting configuration serves one public ownership file from the existing Firebase project. The homepage redirects to the existing Tambola Internet Beta download/support page. The game service remains on the PC; this configuration contains no backend, database, functions or billing change.

- Developer website for the future store listing: `https://tambola-together-beta-sbshrey.web.app`
- Ownership file: `https://tambola-together-beta-sbshrey.web.app/app-ads.txt`
- Privacy policy: `https://sbshrey.github.io/tambola-caller/privacy/`
- Publisher/support: `sbshrey`, `sbshrey@gmail.com`

The public publisher ID is taken from the supplied AdMob app/unit IDs. The line uses [Google's documented authorized-seller format](https://developers.google.com/admob/android/next-gen/app-ads). No app, unit, device or session secret belongs in this directory.

The existing GitHub project page lives below `/tambola-caller/`. Placing an ownership file only below that path would not provide the hostname-root `/app-ads.txt` that AdMob looks for. Firebase supplies a dedicated root without needing a custom domain. [AdMob hosting and crawl guidance](https://support.google.com/admob/answer/9363762).

## Publishing

Run from this directory, with the existing Firebase login:

```powershell
firebase hosting:channel:list --site tambola-together-beta-sbshrey --project tambola-together-beta-sbshrey --json
firebase deploy --only hosting --project tambola-together-beta-sbshrey --non-interactive
```

Inspect the existing site's live release before replacing it. The initial preparation found the default site present and its homepage returning HTTP 404. Only `public/app-ads.txt` is deployable; the README, configuration and CLI logs remain outside the public directory. After deployment, verify the ownership file returns HTTP 200, plain text and the exact local content; verify `/` redirects to the existing friends page and unknown files return 404.

## Store and advertising handoff — 28 September 2026

The user confirmed **Requires review** in AdMob and **no Play Console account yet**. Consent form delivery now passes the Android UMP check, and the signed console callback was verified earlier. The current alpha39 APK still has live ads disabled. A hosted ownership file is not an AdMob verification result or approval.

1. The owner creates a [Play Console account](https://play.google.com/console/signup), completes the registration payment and identity/device checks. Google currently lists a US$25 one-time fee. Choose Personal or Organization according to the actual publisher. [Registration requirements](https://support.google.com/googleplay/android-developer/answer/6112435).
2. Prepare the store release with durable production signing and a signed Android App Bundle, the final store listing/screenshots, audience/content rating, Data safety and deletion support. The existing `publicBeta` variant uses development signing; it is not a Play upload candidate. Decide package/signing continuity before the first upload, since existing beta wallets have no cross-install recovery.
3. For a new personal account, run a closed test with at least 12 testers continuously opted in for 14 days, collect real feedback and apply for production access. Sideloaded APKs and emulator tests do not satisfy this requirement. No testers have been recruited or contacted. [Testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465).
4. Once publicly available in a supported store, link the listing to the matching package in AdMob. For the existing AdMob registration that package is `io.github.sbshrey.tambola.game.beta`; do not link an unrelated app or silently change its identity. Set the store's developer website to the Firebase root above and retain the privacy URL separately. [Link a supported store](https://support.google.com/admob/answer/10037806).
5. Use AdMob's **Verify app / Check for updates** when requested. `app-ads.txt` verification and the app readiness review are separate from consent and reward-callback verification. A file being reachable does not prove AdMob has crawled or accepted it. [App verification](https://support.google.com/admob/answer/14538460).
6. Complete a rewarded-ad integration check with a signed callback and exactly 1,000 credited coins, then enable and verify the release placement. Testing must use Google's test-ad/test-device settings. Do not claim the console verification awarded a real reward.

Google Play is one supported route; AdMob also supports selected third-party Android stores. The current GitHub APK distribution is useful for beta play but is not a supported-store listing for AdMob review. [Readiness requirements](https://support.google.com/admob/answer/10564477).
