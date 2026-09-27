# Beta expansion — alpha36

[Alpha36 is published](https://github.com/sbshrey/tambola-caller/releases/tag/full-game-alpha36-expanded-multiplayer). The anonymous APK download matches the tested optimized build. Firebase and AdMob account activation remain pending.

Confirmed requirements: PC-hosted internet multiplayer, ten-second calls, two tickets per page, no called-number hints on tickets, claim choices stay open across calls, 50,000 beta coins including an existing-user upgrade, seven increasing daily rewards, rewarded ads, power-ups, 30–50-player tables, and several winners sharing each of six fixed prize pools.

Rules version 2 uses Early five, Corners, Top line, Middle line, Bottom line (10% each), and Full house (50%). Each category targets one distinct winner per ten players, rounded up, with a minimum of two in small tables. Everyone tying on the call that reaches the target shares equally. A player can win each category once, using any owned ticket. Integer remainders follow stable ticket ID order. Claims can arrive across calls until the category closes. Payouts wait for the final tie window; unawarded coins are returned at completion. Rules version 1 stays available for installed alpha35 clients and saved rounds.

The implemented power-ups are ticket insurance (no prize: recover the entry cost still outstanding after pool refunds) and prize boost (25% of prize winnings, rounded down). One can be selected before joining, free during the beta. Both are extra promotional credits, separate from the fixed prize pool, and require a completed round. Cancelled rounds receive normal pool refunds only.

The beta upgrade adds 48,500 coins once to the existing 1,500 starter grant, preserving all spending and winnings. Daily rewards add 500, 750, 1,000, 1,500, 2,000, 3,000 and 5,000 coins on consecutive UTC dates. The cycle repeats after seven days and resets after a missed date. Ledger keys and wallet locking deduplicate simultaneous/retried logins.

Implementation and verification checklist:

- [x] Ten-second calls and timer; two-ticket layouts and native accessibility QA.
- [x] No ticket hint styling or called/uncalled tap distinction; server still verifies claims.
- [x] Picker survives new calls and closes when offline, pending, finished, or dismissed.
- [x] Fifty-player server round, multi-winner settlement and legacy-client compatibility.
- [x] One-time beta wallet upgrade and increasing seven-day login rewards with retry/concurrency checks.
- [x] Meaningful power-ups with bounded server-owned coin effects.
- [x] Rewarded ad test integration, signed server-side reward verification, activation instructions (live account activation pending).
- [x] Firebase Crashlytics/Performance SDK integration and collection controls compile.
- [ ] Firebase project/account configuration and real console verification (user chose to sign in later).
- [x] Regression checks, native playtest, backup/migration validation, PC-host deployment, APK publication and anonymous download verification. See the review for the resolved index/runtime issues and the test-driver cleanup failure, recovered and covered by a focused passing check.

Firebase account creation is waiting for the user to run `firebase login --reauth`; they chose to sign in later. AdMob has no account yet: prepare test integration, then guide activation. Do not claim telemetry or live ad rewards are active before their external setup and verification succeed. Power-ups are open to our recommendation and must not reveal called ticket numbers or reduce another winner's prize share.
