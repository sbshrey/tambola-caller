# Release content — internal draft

Prepared against alpha14 and the current room-service source on 26 September 2026. These pages have not been published, submitted to a store or approved as a production privacy policy. Private online play still needs hosted acceptance. Replace the clearly marked operator fields and resolve the listed service decisions before publishing.

## Store listing copy

**App name:** Tambola Together

**Short description:** Quick Tambola games, all your tickets together. Play solo or with friends.

**Full description draft:**

Tap Quick play and get straight to Tambola: three tickets, two clearly labelled computer opponents, familiar prizes and a new number every five seconds. Assisted marking helps you follow the action. No registration or internet is needed for solo play.

Keep your whole hand in view. One to six tickets fit together on the game table without a ticket carousel or scrolling. Newly dealt tickets in your hand have no repeated numbers; a full six-ticket strip covers 1–90 once, like a physical strip. Other players' tickets stay private.

Practise offline, pass a shared device around for family play, or invite friends to a private online room. A paused handoff hides the previous family player's tickets. Online rooms support up to 32 players. Digital tickets, number calling, verified wins, results and rematches all stay inside the app.

Quick play includes Early Five, Corners, the three lines and Full House. For a custom game, choose your tickets, pace and rules before dealing. Try manual dabbing or assisted marking, and preview supported custom patterns. The game checks wins and records points and badges. There are no purchases, cash stakes or payouts.

Make the table your own with player avatars, light and dark themes, celebrations and reduced-motion controls. Choose an English or Hindi interface and English, Hindi or Hinglish number voices. Optional music and effects have separate volume controls. The number voices are AI-generated recordings packaged with the app; game data is not sent to OpenAI during play.

Solo and same-device family games work offline, including number voices. Private online rooms require an internet connection. Saved games, history, a guided practice round and rematches help you settle into the next game.

Use a nickname in online rooms. Room members can see player names, avatars, wins and results; ticket numbers are private to their owners. Read “Your game data” in Settings for retention and deletion controls.

**Publishing checks:** Only publish the online claims after the production endpoint and physical-client journeys pass. Do not describe the app as real-money gaming, universally accessible, independently audited or guaranteed uninterrupted. Target audience, store category, content-rating answers, country availability and final screenshots remain operator decisions. No store listing exists yet.

## Privacy page copy to complete before publication

**Tambola Together — Your game data**

**Operator:** [OPERATOR NAME — REQUIRED]

**Contact:** [MONITORED SUPPORT/PRIVACY EMAIL — REQUIRED]

**Effective date:** [PUBLICATION DATE — REQUIRED]

Tambola Together is a free social game with points and badges. It includes offline solo and same-device family games and optional private online rooms. The app has no advertising or purchases.

### Data on your device

Offline player names, rounds, ticket marks, results, badges and settings stay on your device. They have no automatic expiry. Use the app's Settings controls to remove local game data. Online credentials and cached rooms are stored separately using Android Keystore-backed encryption. The app is configured to exclude its data from Android cloud backup and device transfer.

### Data used for private online play

Creating an online guest profile sends your chosen display name and avatar to the room service. The service assigns a random profile identifier and an access credential; it stores a one-way hash of that credential. It also stores room membership, settings, tickets, calls, prize text, scores, results and timestamps to operate and resume the shared game.

Other members of your room see display names, avatars, readiness, wins and results. Each player receives their own ticket numbers. Use a nickname, and avoid putting personal information in custom prize titles. Invitation sharing uses the destination you choose; recipients can see the shared invitation.

The service processes network addresses to handle connections and applies short-lived request limits using hashed network-address/profile keys. It records aggregate service counts and timings. Those aggregate metrics do not contain names, profile IDs, room codes or ticket contents.

**[HOSTING DETAILS REQUIRED: name the selected operator's infrastructure/service providers, processing locations, access controls, transport protection and actual infrastructure-log/backup retention. Verify these in the deployed configuration before replacing this paragraph. Current local tests do not establish provider behavior.]**

### Retention and deletion

Rooms close 24 hours after creation. Their stored game records are scheduled for removal 30 days after that expiry. Guest access expires after seven days; expired guest profiles are scheduled for removal after another 30 days. These removals depend on the service's retention worker running successfully. Cached online results on this device remain until sign-out, reset, confirmed profile deletion or replacement by newer results.

To request deletion in the app, open Play online → Delete online profile and read the confirmation. Deletion removes the guest profile and access, leaves its rooms and replaces the profile name and avatar in service game records with “Deleted player” and the default avatar. Other players can finish an active game. Shared ticket numbers, calls, scores, custom prize text and results remain until room retention expires. Deletion does not remove text embedded in custom prize titles or copies already saved/shared by other people. After confirmation, this device clears its online data and badges. Offline games remain separate.

Deletion does not rewrite older backups immediately. A separate recovery record prevents a restored primary backup from restoring access to the deleted profile. That record contains a random profile identifier, a one-way confirmation proof and deletion timestamps; it contains no display name, avatar or sign-in secret. It currently has no automatic expiry. The original deletion request can confirm its result for 30 days. **[Before release, finalize and verify the bounded backup/recovery-record retirement policy, then update this paragraph and the matching in-app English/Hindi text together.]**

Signing out revokes online access and clears online data on this device; it does not delete service game history. Reset online data only clears this device. If a deletion request is awaiting confirmation, retain it and use Retry pending action; resetting discards the saved confirmation details.

**Outside-app deletion requests:** [PUBLIC HTTPS REQUEST PAGE AND VERIFIED SUPPORT PROCESS — REQUIRED]. Guest profiles do not have email/password recovery. Define how support can verify authority over a request when the app/session is unavailable. A display name, room code or knowledge of scores alone must not authorize deletion of someone else's profile. Do not claim that this external process exists until it is implemented and exercised.

### Sound, permissions and AI disclosure

The app uses internet access for private online play and vibration for game feedback. It does not request microphone, camera, contacts or location permissions. Number voices were generated with OpenAI and are played from recordings packaged with the app. Music and effects are original synthesized assets. Gameplay does not call OpenAI or send game data to OpenAI.

### Questions and updates

Contact [MONITORED SUPPORT/PRIVACY EMAIL — REQUIRED] with privacy questions. Publish updates here with a new effective date when the app or its data practices change.

## Support page draft

**Tambola Together support — [MONITORED SUPPORT EMAIL REQUIRED]**

Include your app version, Android version, playing mode and a short description of what happened. For a connection problem, include approximately when it happened and whether other players were still connected. Do not send sign-in secrets or recovery proofs.

- **Offline game:** return to the saved game and resume. Avoid clearing local game data if you want to retain that round.
- **Room connection interrupted:** wait for usable internet and reconnect to the current room. Calls may continue while your device is disconnected.
- **Pending action:** use Retry pending action so the app can confirm the original request. Resetting online data discards its local recovery details.
- **No sound:** check Android media volume and the app's separate voice/music/effects controls. After an interruption or route change, use Resume sound or Hear again.
- **Data controls:** “Your game data” explains the difference between local reset, sign-out and confirmed profile deletion. The external deletion-request link must appear prominently on this page once its process is ready.

## Submission handoff

### Dated eligibility checks — 26 September 2026

- **Target API:** Google's current new-app requirement is Android 16/API 36 or higher from 31 August 2026. The packaged alpha14 APK was inspected with SDK `aapt2`: package `io.github.sbshrey.tambola.game`, version code 14, target SDK **36**, SHA-256 `49344972eaf57381bfbd02a445882b71f3104259bed0ea4f638cc21c1a0cc891`. It meets this target-number requirement; that does not establish all store eligibility or physical-device behavior. [Target API policy](https://support.google.com/googleplay/android-developer/answer/11926878).
- **Developer-account testing:** if the publishing account is personal and was created after 13 November 2023, Google requires at least **12 continuously opted-in testers for 14 days** in a closed test before applying for production access. This app's publishing account type/date and production-access status are not known. Local emulator tests and APK sideloads do not prove that Play Console condition. Confirm it when choosing the publishing account; no testers were contacted and no closed track was created. [Personal-account testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465).
- **Deletion scope:** Google's guidance excludes identities created and operated wholly offline, but an in-scope online account needs a functional external deletion-request resource even if in-app deletion exists. A prominent support email or form can be a request route; users must not need to reinstall the app to initiate it. Our server-backed guest profile's classification still needs confirmation. Do not infer exemption solely from the word “guest.” Operator contact, ownership verification and the real external pathway remain unresolved. [Account-deletion guidance](https://support.google.com/googleplay/android-developer/answer/13327111).

Google requires a public privacy-policy URL and accurate Data safety disclosures. Where account-deletion requirements apply, the external resource must let a user request deletion without requiring reinstallation. Treat the server-backed guest profile as in scope for release planning until the store's applicable classification is confirmed; a temporary guest label is not a demonstrated exemption. [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311), [account-deletion guidance](https://support.google.com/googleplay/android-developer/answer/13327111).

Prepare the final Data safety answers from the deployed data flows: display names, random user identifiers, gameplay/custom text, diagnostics/network processing and selected providers. Do not declare “no data collected” for the online version. Any sharing exemptions and collection-purpose choices need review against the actual provider contracts and implementation. Reconcile the final form with this page and the in-app disclosure before submission.

Source checks: `app/src/main/res/values/privacy.xml`, `AndroidManifest.xml`, `online/OnlineStore.kt`, `client/OnlineState.kt`, service registration/cleanup/redaction, [profile deletion](server/PROFILE_DELETION.md), [audio provenance](AUDIO.md) and [invitations](INVITES.md). Store/support text is a draft artifact; it adds no endpoint, account flow or app behavior.
