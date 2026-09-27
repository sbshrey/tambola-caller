# Release content — internal draft

Updated 27 September 2026 against the optimized alpha22 coin-game flow and the installed Wi-Fi service. These pages have not been published, submitted to a store or approved as a production privacy policy. The current build connects to this computer on its private Wi-Fi address; public multiplayer distribution still needs a public endpoint and physical-device acceptance. Replace the clearly marked operator fields and resolve the listed service decisions before publishing. The [current scope and acceptance plan](../docs/ONLINE_COIN_GAME_PLAN.md) supersedes the earlier solo/family release copy.

## Store listing copy

**App name:** Tambola Together

**Short description:** Fast online Tambola. Pick tickets, dab numbers and claim free-coin prizes.

**Full description draft:**

Pick one to six tickets and tap Play. Join an online Tambola table with a short countdown, a new number every five seconds and familiar prizes. Guest play starts from your ticket choice, with no email or password form.

Play with readable ticket pages: one or two tickets at a time, with simple up/down buttons for the rest. Dab your numbers yourself, then tap Claim beside the ticket and choose your prize. Numbers never repeat across your own tickets; a complete six-ticket strip covers 1–90 once, like a physical strip. Other players' ticket numbers stay private.

Start with 1,500 free coins. Each ticket costs 100 coins, and the round's ticket total determines its prize pool. See the pool and prize amounts before calling begins. Play for Early Five, Corners, Top, Middle and Bottom lines, plus one to three house prizes. Claims on the same call share a prize. Results show your winnings and a quick way into the next round.

Tables accept up to eight people. If fewer than four people have joined when the round starts, clearly labelled computer players fill the remaining places to four. All use the same revealed numbers and prize rules. An online connection is required, including when you play alongside computers.

Running low? When you cannot afford one ticket, collect 500 free coins when your refill is ready. Coins have no cash value and cannot be bought, transferred or redeemed. There are no cash prizes, advertising or paid ticket purchases.

Follow recent calls, animated balls, dab stamps and win celebrations. Choose an English or Hindi interface, English/Hindi/Hinglish number voices, light or dark appearance and reduced motion. Voice, music and effects have separate controls. Number voices are AI-generated recordings packaged with the app; gameplay sends no game data to OpenAI.

Reconnect to your purchased tickets and saved marks on the same installation. Your last ticket quantity is remembered for the next purchase. Keep the app's data if you want to keep access to your guest wallet: recovery after resetting, uninstalling or losing the device is not currently available. Tap your coin balance to open your profile and read “Your game data.”

**Publishing checks:** Only publish the online claims after the production endpoint and physical-client journeys pass. Do not describe the app as real-money gaming, universally accessible, independently audited or guaranteed uninterrupted. Target audience, store category, content-rating answers, country availability and final screenshots remain operator decisions. No store listing exists yet.

## Privacy page copy to complete before publication

**Tambola Together — Privacy policy**

**Operator:** [OPERATOR NAME — REQUIRED]

**Contact:** [MONITORED SUPPORT/PRIVACY EMAIL — REQUIRED]

**Effective date:** [PUBLICATION DATE — REQUIRED]

Tambola Together is an online social Tambola game with free virtual coins. Coins buy tickets and are awarded for verified claims; they have no cash value and cannot be purchased for money, transferred or redeemed. The app has no advertising or cash purchases. Computer players are identified in the game.

### Data on your device

Guest session and device credentials, cached online games, marks, pending requests and the preferred ticket quantity are stored on this installation using Android Keystore-backed encryption. Settings are stored locally. The app is configured to exclude its data from Android cloud backup and device transfer. Older offline saves from previous versions remain on the device without automatic expiry and can be removed in Settings; they are not a playing mode in this release.

### Data used for online play

Starting online play creates or resumes a guest profile. The service receives its display name and avatar; a new coin-game profile starts with a generated player name. The service assigns a random profile identifier and session credential, and accepts a device credential for renewal on the same installation. It stores only one-way hashes of session and device credentials. It also stores room membership, game settings, tickets, calls, verified claims, results, coin balances, transaction entries and request receipts to operate the game and handle retries without duplicate charges or credits. Older shared games may also contain custom prize text and scores.

Other members of your table see display names, avatars, wins and results. Each player receives only their own ticket numbers. Shared records from earlier private-room versions remain subject to the same retention and deletion rules. Avoid personal information in player names or any previously saved custom prize titles.

The service processes network addresses to handle connections and applies short-lived request limits using hashed network-address/profile keys. It records aggregate service counts and timings. Those aggregate metrics do not contain names, profile IDs, room codes or ticket contents.

**[HOSTING DETAILS REQUIRED: name the selected operator's infrastructure/service providers, processing locations, access controls, transport protection and actual infrastructure-log/backup retention. Verify these in the deployed configuration before replacing this paragraph. Current local tests do not establish provider behavior.]**

### Retention and deletion

Rooms close 24 hours after creation. Their stored game records are scheduled for removal 30 days after that expiry. Game sessions expire after seven days and renew automatically on this installation without replacing the wallet. Profiles with an enabled device credential, their wallets and transaction histories currently have no automatic expiry. Unenrolled or revoked profiles are scheduled for removal 30 days after session expiry. These removals depend on the service's retention worker running successfully. Cached online results on this device remain until sign-out, reset, confirmed profile deletion or replacement by newer results.

To request deletion in the app, tap your coin balance → Delete online profile and read the confirmation. Deletion removes the guest profile, credentials, wallet and its ledger/owned request receipts, leaves its rooms and replaces the profile name and avatar in shared service game records with “Deleted player” and the default avatar. Other players can finish an active game. Shared ticket numbers, calls, claims, scores, custom prize text and results remain until room retention expires. Deletion does not remove text embedded in older custom prize titles or copies already saved/shared by other people. After confirmation, this device clears its online data and badges. Older offline saves remain separate. **[Before publication, review and justify the retention of shared records against the final deletion policy; in-app disclosure is not store approval.]**

Signing out or deleting a profile does not rewrite older backups immediately. Separate recovery records prevent an older primary backup from restoring signed-out or deleted access. They contain a random profile identifier and sign-out/deletion times; deletion also stores a one-way confirmation proof and its expiry. They contain no display name, avatar or sign-in secret and currently have no automatic expiry. The original deletion request can confirm its result for 30 days, while recovery protection remains. **[Before release, finalize and verify the bounded backup/recovery-record retirement policy, then update this paragraph and the matching in-app English/Hindi text together.]**

Signing out revokes both the session and device credential and clears online data on this device; it does not immediately delete service history. Reset online data only clears this device. Resetting, uninstalling or losing the device can lose access to the guest wallet: there is no recovery on another installation. If a request is awaiting confirmation, keep its saved details and use Reconnect & check; resetting discards local recovery information. Use Delete online profile for service-side deletion.

**Outside-app deletion requests:** [PUBLIC HTTPS REQUEST PAGE AND VERIFIED SUPPORT PROCESS — REQUIRED]. Guest profiles do not have email/password recovery. Define how support can verify authority over a request when the app/session is unavailable. A display name, room code or knowledge of scores alone must not authorize deletion of someone else's profile. Do not claim that this external process exists until it is implemented and exercised.

### Sound, permissions and AI disclosure

The app uses network access for online play and vibration for game feedback. It does not request microphone, camera, contacts or location permissions. Number voices were generated with OpenAI and are played from recordings packaged with the app. Music and effects are original synthesized assets. Gameplay does not call OpenAI or send game data to OpenAI.

### Questions and updates

Contact [MONITORED SUPPORT/PRIVACY EMAIL — REQUIRED] with privacy questions. Publish updates here with a new effective date when the app or its data practices change.

## Support page draft

**Tambola Together support — [MONITORED SUPPORT EMAIL REQUIRED]**

Include your app version, Android version and a short description of what happened. For a connection or coin problem, include approximately when it happened, the ticket quantity and the visible amount. Do not send sign-in secrets, device credentials or recovery proofs.

- **Connection interrupted:** reconnect to the current table. Calls may continue while your device is disconnected. For the Wi-Fi build, the phone must be on the host's network and the computer must be awake with the server running.
- **Pending purchase, cancellation or claim:** use Reconnect & check so the app can confirm the original request. Avoid resetting app data; that discards the saved request and may lose wallet access.
- **Low balance:** choose fewer tickets. Below 100 coins, collect the free refill when its countdown is complete. There is no way to buy coins for money.
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

Source checks: `app/src/main/res/values/privacy.xml`, `coins.xml`, the packaged alpha22 metadata, `ui/CoinLobby.kt`, `online/OnlineViewModel.kt`, `online/OnlineStore.kt`, `client/OnlineState.kt`, service registration/session renewal/cleanup/redaction, `RoomEconomy.kt`, `CoinLedger.kt`, [profile deletion](server/PROFILE_DELETION.md), [audio provenance](AUDIO.md) and [the coin plan](../docs/ONLINE_COIN_GAME_PLAN.md). Official policy pages above were checked on 27 September 2026. Store/support text is a draft artifact; it adds no endpoint, account flow or app behavior.
