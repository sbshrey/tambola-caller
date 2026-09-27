# Expanded multiplayer candidate

Candidate: alpha36, version code 36. The published release is still alpha35 while native acceptance and deployment are being completed.

The implementation adds ten-second online calls, two tickets per page, manual marking without called-number hints, a claim picker that survives incoming calls, fifty-player tables and six fixed prize pools with multiple distinct winners. Daily coins preserve existing spending while raising the total starting grant to 50,000. Ticket insurance and a 25% prize boost use additional beta coins without reducing other winners' shares.

Firebase Crashlytics and custom Performance traces are integrated behind an optional sharing control. Firebase account sign-in/configuration and dashboard delivery remain pending. Rewarded ads use UMP consent and signed server verification; live ads are disabled pending AdMob setup. See [Firebase setup](../../FIREBASE_SETUP.md) and [AdMob setup](../../ADMOB_SETUP.md).

## Recorded checks

- Domain: 54 tests passed. Client: 39 tests passed.
- Full server run: 182 tests, 181 passed. The sole failure identified an expression-dependent lobby index that prevented PostgreSQL HOT updates. Migration 008 was corrected before deployment; all four index/backfill/restore tests then passed.
- A further PostgreSQL-backed test connected fifty private WebSocket clients. Every player received the same next call, six own tickets and no unrevealed draw order. This is fixture transport evidence, not a physical-network latency benchmark.
- All four rewarded-ad tests passed with generated ECDSA signatures verified by Google's Tink library: duplicate/concurrent/restarted delivery, invalid signatures and reward fields, transaction replay, five-per-day limits, failed-load slot reuse and deletion. All ten restricted-role tests passed, including real ad-credit writes and cascade deletion. Real Google-signed callbacks still require account activation.
- Native arena checks: 16 normal-size checks passed; the first narrow/double-text run found a Hindi caption clipping issue. Removing the decorative sparkle at larger text sizes fixed it; all nine rerun checks passed. The first attempt remains explicitly labelled as failed evidence.
- Gateway tests pass, including preservation of signed query bytes, fifty connection attempts from one network, bounded registration bursts, new reward routes and rejection of internal paths.

`regression-attempt1.json` records suite counts without copying raw server logs. `native-normal`, `native-narrow-large-attempt1` and `native-narrow-large-fixed` contain the corresponding screenshots and instrumentation evidence.

Twenty native reward/lobby/disclosure/arena checks passed after fixing results alignment, disclosure recreation and the pre-registration disclosure entry. Both English and Hindi reward screens passed, including larger text. Three additional narrow/200% checks passed for the fifty-player roster and the two-ticket claim flow. The official Google test rewarded ad loaded into AdActivity and dismissed back to the app; this check never granted coins. Android unit tests (18) and lint passed. Three final offline cadence/settings checks passed after refreshing older fixtures: quick play has ten-second calls, manual marking and two tickets per page. Lobby copy now says ten-second calls and six shared prizes; the latest captures are in `native-cadence`. Earlier native captures preserve the previous label as historical evidence.

The optimized APK, committed-source packaging, live PC migration, public full-round acceptance and release publication are still being completed. No Firebase console, live ad revenue, physical phone or cellular-network success is claimed by this record.
