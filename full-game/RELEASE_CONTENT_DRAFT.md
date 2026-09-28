# Release content — internal draft

Updated 28 September 2026 against the published alpha39 Internet Beta and the current PC service. This store/support document remains an internal draft and has not been submitted to a store. The current public privacy policy is maintained separately at [privacy/index.html](../privacy/index.html) and [published here](https://sbshrey.github.io/tambola-caller/privacy/), using the publisher's supplied contact address. Internet multiplayer uses an HTTPS Cloudflare tunnel to the operator's PC; the APK is distributed through GitHub Releases. Firebase diagnostics are available with player opt-in. The AdMob console callback is verified, but live ads remain disabled in the published APK. The publisher consent check reports that no consent forms are configured for the supplied app ID. Older dated store-eligibility checks below are historical evidence, not a new submission approval.

## Store listing copy

**App name:** Tambola Together

**Short description:** Fast online Tambola. Pick tickets, dab numbers and claim free-coin prizes.

**Full description draft:**

Pick one to six tickets and tap Play. Join an online Tambola table with a short countdown, a new number every ten seconds and familiar prizes. Guest play starts from your ticket choice, with no email or password form.

Play with readable ticket pages: one or two tickets at a time, with simple up/down buttons for the rest. Dab your numbers yourself, then tap Claim beside the ticket and choose your prize. Numbers never repeat across your own tickets; a complete six-ticket strip covers 1–90 once, like a physical strip. Other players' ticket numbers stay private.

Start with 50,000 free beta coins. Each ticket costs 100 coins, and the round's ticket total determines its prize pool. See the pool and prize amounts before calling begins. Play for Early Five, Corners, Top, Middle and Bottom lines, and Full House. Each category can have several winners who share its fixed pool. Results show your winnings and a quick way into the next round. Existing eligible beta wallets receive a one-time starter upgrade that preserves their previous transactions.

Tables support up to 50 players. Quick play includes staggered arrivals of simulated practice personas, shown as Player plus a random ID and a Practice label. Friends tables contain invited players. An online connection is required for these modes, including quick play with practice personas.

Collect increasing daily login rewards across a seven-day streak: 500, 750, 1,000, 1,500, 2,000, 3,000 and 5,000 coins. When you cannot afford one ticket, collect 500 free coins when your refill is ready. Coins have no cash value and cannot be bought, transferred or redeemed. There are no cash prizes or paid ticket purchases. Rewarded video is being configured and is not enabled in the current published beta; do not advertise it as available until activation and completed-ad reward checks pass.

Choose Classic for traditional play or Power rooms for free random powers after every five unique correct manual marks. Hold up to two powers: Shield, 15-second Auto-Dab, or a 25% bonus on a successful prize share. Incorrect marks are rejected. In Power rooms, a false claim uses an available Shield or discards the selected ticket. The full number board opens from the recent-calls arrow for a quick look.

Follow recent calls, animated balls, dab stamps and win celebrations. Choose an English or Hindi interface, English/Hindi/Hinglish number voices, light or dark appearance and reduced motion. Voice, music and effects have separate controls. Number voices are AI-generated recordings packaged with the app; gameplay sends no game data to OpenAI.

Reconnect to your purchased tickets and saved marks on the same installation. Your last ticket quantity is remembered for the next purchase. Keep the app's data if you want to keep access to your guest wallet: recovery after resetting, uninstalling or losing the device is not currently available. Tap your coin balance to open your profile and read “Your game data.”

**Publishing checks:** Only publish the online claims after the production endpoint and physical-client journeys pass. Do not describe the app as real-money gaming, universally accessible, independently audited or guaranteed uninterrupted. Target audience, store category, content-rating answers, country availability and final screenshots remain operator decisions. No store listing exists yet.

## Privacy page copy to complete before publication

**Tambola Together — Privacy policy**

**Operator:** sbshrey

**Contact:** sbshrey@gmail.com

**Effective date:** [PUBLICATION DATE — REQUIRED]

Tambola Together is an online social Tambola game with free virtual coins. Coins buy tickets and are awarded for verified claims and daily rewards; they have no cash value and cannot be purchased for money, transferred or redeemed. Practice personas are simulated and identified in the game. The current published beta has no live advertising. Optional rewarded-video integration is described below for builds in which it is enabled; it does not block ordinary play or free refills.

### Data on your device

Guest session and device credentials, cached online games, marks, pending requests and the preferred ticket quantity are stored on this installation using Android Keystore-backed encryption. Settings are stored locally. The app is configured to exclude its data from Android cloud backup and device transfer. Older offline saves from previous versions remain on the device without automatic expiry and can be removed in Settings; they are not a playing mode in this release.

### Data used for online play

Starting online play creates or resumes a guest profile. The service receives its display name and avatar; a new coin-game profile starts with a generated player name. The service assigns a random profile identifier and session credential, and accepts a device credential for renewal on the same installation. It stores only one-way hashes of session and device credentials. It also stores room membership, game settings, tickets, calls, verified claims, results, coin balances, transaction entries and request receipts to operate the game and handle retries without duplicate charges or credits. Older shared games may also contain custom prize text and scores.

Other members of your table see display names, avatars, wins and results. Each player receives only their own ticket numbers. Shared records from earlier private-room versions remain subject to the same retention and deletion rules. Avoid personal information in player names or any previously saved custom prize titles.

The service processes network addresses to handle connections and applies short-lived request limits using hashed network-address/profile keys. It records aggregate service counts and timings. Those aggregate metrics do not contain names, profile IDs, room codes or ticket contents.

The beta game service and its PostgreSQL databases run on the operator's PC. Cloudflare Tunnel carries HTTPS connections from internet players to that service. GitHub hosts the source, APK downloads, invitation page and server-location directory. These providers process connection information to deliver their services and may process it in different countries. Their practices are described in [Cloudflare's privacy policy](https://www.cloudflare.com/privacypolicy/) and [GitHub's privacy statement](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement). The PC service uses separate restricted database roles and local protected runtime credentials. Operator-side diagnostic logs currently have no automatic expiry and are designed to exclude credentials and gameplay request bodies.

### Optional diagnostics and rewarded videos

Firebase Crashlytics and Performance Monitoring are included to understand beta crashes and performance. Sharing is off by default and can be changed in the app's diagnostics setting. When enabled, Google may receive crash stack traces, app/device and operating-system information, installation identifiers, and performance measurements. Our custom traces report operation names, duration and outcome; they do not attach nicknames, profile IDs, tickets, room codes, URLs or credentials. Turning sharing off stops future collection in the app and deletes unsent crash reports; it does not retract reports already delivered to Google.

Google states that Crashlytics retains crash traces and associated identifiers for 90 days before beginning deletion. Performance Monitoring retains IP-associated events for 30 days and installation-associated/de-identified performance data for 60 days before beginning deletion. See [Firebase privacy information](https://firebase.google.com/support/privacy) for the provider's current details and processing practices.

In builds where optional rewarded videos are enabled, Google AdMob handles ad delivery and its User Messaging Platform handles relevant ad privacy choices. The placement offers 1,000 virtual coins after a completed video and server verification, up to five rewards per UTC day. You can dismiss a video and continue playing without its reward. Google's SDK may process IP addresses, device/app identifiers, ad interactions and diagnostic information for advertising, fraud prevention and service operation; removing Android Advertising ID permission does not mean that the SDK processes no identifiers. See [Google's partner-service privacy information](https://policies.google.com/technologies/partner-sites) and [Mobile Ads SDK data disclosures](https://developers.google.com/admob/android/privacy/play-data-disclosure).

We supply AdMob with a random, single-reward reference rather than a nickname, game profile ID, room code or ticket contents. Google sends a signed reward callback; the service matches the reference to the requested reward and prevents duplicate credits. The service stores reward intents and receipts with the game profile, and deletes them with profile deletion. AdMob's console verification probes never award coins. Ad privacy options are available in the app when Google's consent system requires them. The current published alpha39 APK does not request live rewarded ads.

### Retention and deletion

Rooms close 24 hours after creation. Their stored game records are scheduled for removal 30 days after that expiry. Game sessions expire after seven days and renew automatically on this installation without replacing the wallet. Profiles with an enabled device credential, their wallets and transaction histories currently have no automatic expiry. Unenrolled or revoked profiles are scheduled for removal 30 days after session expiry. These removals depend on the service's retention worker running successfully. Cached online results on this device remain until sign-out, reset, confirmed profile deletion or replacement by newer results.

To request deletion in the app, tap your coin balance → Delete online profile and read the confirmation. Deletion removes the guest profile, credentials, wallet and its ledger/owned request receipts, leaves its rooms and replaces the profile name and avatar in shared service game records with “Deleted player” and the default avatar. Other players can finish an active game. Shared ticket numbers, calls, claims, scores, custom prize text and results remain until room retention expires. Deletion does not remove text embedded in older custom prize titles or copies already saved/shared by other people. After confirmation, this device clears its online data and badges. Older offline saves remain separate. **[Before publication, review and justify the retention of shared records against the final deletion policy; in-app disclosure is not store approval.]**

Signing out or deleting a profile does not rewrite older backups immediately. Routine backups retain the latest seven complete primary/recovery-database pairs. Additional archives retained for service upgrades currently have no automatic expiry. Separate recovery records prevent an older primary backup from restoring signed-out or deleted access. They contain a random profile identifier and sign-out/deletion times; deletion also stores a one-way confirmation proof and its expiry. They contain no display name, avatar or sign-in secret and currently have no automatic expiry. The original deletion request can confirm its result for 30 days, while recovery protection remains. Any future change to these retention periods needs corresponding service and in-app disclosure changes.

Signing out revokes both the session and device credential and clears online data on this device; it does not immediately delete service history. Reset online data only clears this device. Resetting, uninstalling or losing the device can lose access to the guest wallet: there is no recovery on another installation. If a request is awaiting confirmation, keep its saved details and use Reconnect & check; resetting discards local recovery information. Use Delete online profile for service-side deletion.

**Outside-app deletion requests:** [PUBLIC HTTPS REQUEST PAGE AND VERIFIED SUPPORT PROCESS — REQUIRED]. Guest profiles do not have email/password recovery. Define how support can verify authority over a request when the app/session is unavailable. A display name, room code or knowledge of scores alone must not authorize deletion of someone else's profile. Do not claim that this external process exists until it is implemented and exercised.

### Sound, permissions and AI disclosure

The app uses network access for online play and vibration for game feedback. It does not request microphone, camera, contacts or location permissions. Number voices were generated with OpenAI and are played from recordings packaged with the app. Music and effects are original synthesized assets. Gameplay does not call OpenAI or send game data to OpenAI.

### Questions and updates

Contact sbshrey@gmail.com with privacy questions. Publish updates here with a new effective date when the app or its data practices change.

## Support page draft

**Tambola Together support — sbshrey@gmail.com**

Include your app version, Android version and a short description of what happened. For a connection or coin problem, include approximately when it happened, the ticket quantity and the visible amount. Do not send sign-in secrets, device credentials or recovery proofs.

- **Connection interrupted:** reconnect to the current table. Calls may continue while your device is disconnected. The Internet Beta can connect beyond Wi-Fi, but its temporary PC server must remain awake and online. The older Wi-Fi build still requires the host's local network.
- **Pending purchase, cancellation or claim:** use Reconnect & check so the app can confirm the original request. Avoid resetting app data; that discards the saved request and may lose wallet access.
- **Low balance:** collect your daily reward or choose fewer tickets. Below 100 coins, collect the free refill when its countdown is complete. There is no way to buy coins for money. The published alpha39 APK has rewarded videos disabled while AdMob activation is completed.
- **Finding another ticket:** use the up/down arrows. A page change keeps existing marks. Claim belongs to the ticket beside the button, then to the prize you select.
- **No sound:** check Android media volume and the app's separate voice/music/effects controls. After an interruption or route change, use Resume sound or Hear again.
- **Data controls:** tap the coin balance for the profile and deletion controls. “Your game data” explains local reset, sign-out and confirmed profile deletion. The external deletion-request link must appear prominently on this page once its process is ready.

## Submission handoff

### Dated eligibility checks — 27 September 2026

- **Listing fields:** this draft's app name, short description and full description contain 16, 74 and 2,174 characters respectively, within the current 30/80/4,000 limits. Recheck after editing or translating. [Store-listing fields](https://support.google.com/googleplay/android-developer/answer/9859152).
- **Target API:** Google's current new-app requirement is Android 16/API 36 or higher from 31 August 2026. The [archived alpha22 package metadata](reviews/computer-round-alpha22-2026-09-27/manifest-badging.txt) records package `io.github.sbshrey.tambola.game`, version code 22 and target SDK **36**. Its APK SHA-256 is `05dfaa405629fec0a5ab57234941eca1229b9f2cb13034a496e3549759bdb4b4`. This target-number check does not establish all store eligibility or physical-device behavior. The development-signed private-IP build is not the public release package. [Target API policy](https://support.google.com/googleplay/android-developer/answer/11926878).
- **Developer-account testing:** if the publishing account is personal and was created after 13 November 2023, Google requires at least **12 continuously opted-in testers for 14 days** in a closed test before applying for production access. This app's publishing account type/date and production-access status are not known. Local emulator tests and APK sideloads do not prove that Play Console condition. Confirm it when choosing the publishing account; no testers were contacted and no closed track was created. [Personal-account testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465).
- **Deletion scope:** Google's guidance excludes identities created and operated wholly offline, but an in-scope online account needs a functional external deletion-request resource even if in-app deletion exists. A prominent support email or form can be a request route; users must not need to reinstall the app to initiate it. Our server-backed guest profile's classification still needs confirmation. Do not infer exemption solely from the word “guest.” Operator contact, ownership verification and the real external pathway remain unresolved. [Account-deletion guidance](https://support.google.com/googleplay/android-developer/answer/13327111).

Google requires a public privacy-policy URL and accurate Data safety disclosures. Where account-deletion requirements apply, the external resource must let a user request deletion without requiring reinstallation. Treat the server-backed guest profile as in scope for release planning until the store's applicable classification is confirmed; a temporary guest label is not a demonstrated exemption. [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311), [account-deletion guidance](https://support.google.com/googleplay/android-developer/answer/13327111).

Prepare the final Data safety answers from the deployed data flows: display names, random user identifiers, device/session credential processing, gameplay and legacy custom text, virtual-coin balances and ledger entries, diagnostics/network processing and selected providers. Do not declare “no data collected” for the online version. Any sharing exemptions and collection-purpose choices need review against the actual provider contracts and implementation. Reconcile the final form with this page and the in-app disclosure before submission.

Current data-flow checks: `app/src/main/res/values/privacy.xml`, `telemetry/BetaTelemetry.kt`, `ads/RewardedAdsController.kt`, `server/RewardedAds.kt`, `CoinLedger.kt`, `tools/windows-backup.ps1`, the alpha39 release evidence and the 28 September callback/consent checks. Firebase and AdMob data-disclosure pages were checked on 28 September 2026. The earlier store-eligibility section retains its original date and APK evidence. Store/support text is a draft artifact; it adds no endpoint, account flow or app behavior.
