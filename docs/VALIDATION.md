# V1 validation — 24 September 2026

## Automated checks

`npm test`: **18 passed, 0 failed** on Node.js 24.21.0.

- Forty complete random games each contained all 90 numbers exactly once.
- Empty/finished game boundaries, undo/redraw, claim eligibility, and claim rollback.
- Save round trips, malformed/incompatible saves, and denied/quota-limited storage.
- Number spelling and speech selection/cancellation/error handling through a fake speech adapter.
- Service-worker asset coverage, cache isolation, offline query navigation, and unrelated-request handling through a worker fixture.

`npm run build`: **passed**, producing the static site in `dist/`.

`node --check` passed for the app entry point and development server.

## Browser checks

Tested against the local HTTP app in the Chromium-based Codex browser:

- Fresh game, correct disabled controls, voice enabled by default.
- A double-click drew one number.
- Calls updated the current number, spelling, counter, latest highlight, board, and last-ten list.
- Complete 90-call game: 90 unique history entries, 90 marked cells, one latest cell, zero remaining, and drawing disabled.
- Undo from a complete game and redraw restored completion correctly.
- Recorded a named claim; refreshed and confirmed the claim, numbers, and muted voice preference persisted.
- Undo with a claim opened a confirmation. Cancellation kept the game; confirmation removed that call and its claim.
- Recorded and removed a Full house claim.
- Complete history appeared in call order.
- New-game cancellation preserved the game. Confirmation reset numbers and claims.
- Offline network emulation: reloaded the cached app, restored its game, and drew another number. Verified the final stylesheet also reopens offline.
- Inspected desktop, 390px, and 320px layouts. Fixed narrow-screen button wrapping; no horizontal overflow at 320px.
- Share dialog displayed the app link and explained that a localhost preview is not a public sharing link.
- No browser console errors in the final verification.

The preview was left with a fresh game, voice enabled, and normal network/viewport settings restored.

## GitHub Pages deployment

- Repository: [sbshrey/tambola-caller](https://github.com/sbshrey/tambola-caller).
- Public app: [https://sbshrey.github.io/tambola-caller/](https://sbshrey.github.io/tambola-caller/).
- Application commit: `c862c08382e06c938adb887670abc874a22b6bf3`.
- [Initial Pages build and deployment](https://github.com/sbshrey/tambola-caller/actions/runs/35946586055): **successful**.
- Pages publishes `main`, `/ (root)`, with HTTPS enforced. The built-in branch deployment does not require uploading a custom workflow.
- All 11 deployed app assets returned HTTP 200 and matched the committed files by SHA-256, including scripts, stylesheet, manifest, service worker, and icons.
- Live-browser checks passed: five calls marked five board cells; a named Early 5 claim and game state survived reload; offline reload restored the game and allowed another call.
- The Share app dialog provided the public HTTPS URL. The live test game was reset and voice was re-enabled afterward.
- The 18 regression tests and the static build passed locally before publication. GitHub's branch deployment publishes the site but does not run this test suite.

## V1.1 number sharing — 24 September 2026

- `npm test`: **26 passed**, including eight sharing tests. `npm run build` and JavaScript syntax checks passed.
- Sharing tests cover exact message content, recent-call order, first/last calls, undo, reset, correctly encoded WhatsApp links, immediate native sharing invocation, cancellation, unavailable/denied sharing, and clipboard failures.
- Browser checks on a separate local test game confirmed disabled sharing before the first call, enabled controls after drawing, the copy-success UI, sharing controls at 320px without horizontal overflow, and offline reopening with the new sharing module.
- An isolated browser fixture with native sharing disabled and clipboard writes rejected exercised the fallback dialog and manual selection. The preview and decoded WhatsApp link matched exactly; undo updated both, and reset disabled sharing.
- The WhatsApp link was inspected without sending a message. Real phone share-sheet selection and actual WhatsApp delivery remain unverified; adapter tests simulate native results and do not establish delivery.
- The service-worker cache version was advanced to `v1.1.0` and includes `src/sharing.js`. Existing games retain the same storage schema.

## Not established by these checks

- Actual sound output, installed voices, and offline voice availability on a physical Android phone or iPhone.
- Home-screen installation and mobile Safari behavior on real devices.
- Vercel deployment (GitHub Pages is the selected host).
- Remote multiplayer and automatic ticket verification, which are outside V1.

## V1.2 prerecorded voices — 2026-09-24

- Generated all 90 MP3 files locally with OpenAI `gpt-4o-mini-tts`, `coral`, and a female Indian English host prompt. `audio/manifest.json` binds each clip to its script, generation settings fingerprint and SHA-256. The API key was read from the environment and is absent from the source/build.
- 39 Node tests pass, including stale playback cancellation, audio-to-device-speech fallback, file sharing/cancellation, generation resume without repeat billing, all 90 assets and hashes, and offline byte-range responses. Static build succeeds and includes the full voice pack.
- Automated transcription of every actual MP3 checked the final number without providing expected wording to the transcription model. `docs/audio-validation.json` records results and hashes. A second transcription resolved a formatting ambiguity for 8. Initial 60 and 66 clips lacked a clear full-number ending; both were regenerated with explicit wording and passed a fresh transcription. All 90 final files identify the intended number. Automated transcription is not a human accent or voice-quality assessment.
- MP3 frame parsing checked all initial files for valid frame boundaries, complete frames and plausible durations (3.12–6.77 seconds). The two replacement clips were decoded by the transcription service; final pack hashes are covered by tests.
- In-app Chromium: a drawn 16 loaded the matching MP3 and decoded a 6.552-second duration; Say it again restarted playback. After network emulation went offline and the app reloaded, a fresh 37 played its cached clip (5.256 seconds) and became shareable without a network connection.
- Responsive screenshots at 390px and 320px verified the audio/text controls and fallback guidance fit. An isolated local fixture with file sharing unavailable showed the Download MP3/attach-in-WhatsApp guidance, preserved the called number, stopped playback, and cleared audio sharing/download on Undo. No console errors were observed. Unit fixtures cover native file-sharing payload and user-activation ordering.
- No WhatsApp message was sent. Actual Android/iPhone WhatsApp handoff, delivered attachment playback, and preferred accent/voice still need a phone check. The app shares an MP3 file, not a guaranteed WhatsApp voice-note bubble. The phone user chooses the recipient/group and confirms Send.
- The service worker caches the complete app and all 90 clips under v1.2.0, supports media byte ranges offline, and only reports the new audio pack ready when the active worker confirms its version. Old open tabs keep their previous version until closed.

## V1.3 PNG sharing — 2026-09-24

- Added Share board image and Preview controls. A 1080 × 1540 PNG contains the latest number and words, last 10 calls latest first, current call count, remaining count and the complete 1–90 board. Latest/called/waiting styles and an underline on called cells distinguish the marks. No player/winner names are included.
- PNG generation uses a local canvas with system fonts, without a backend, third-party screenshot library or network assets. Files are prepared before the sharing tap to preserve mobile user activation. PNG and MP3 share the same native-file helper. Unsupported/failed sharing offers preview, Download PNG and WhatsApp attachment guidance; cancellation is quiet.
- 44 Node tests pass. Added regression coverage for image snapshot privacy, first/final calls, undo/reset, stale asynchronous results, unchanged-board reuse, failed generation, PNG handoff/cancellation/fallback. Existing voice and audio tests still pass. Offline worker includes the renderer under cache v1.3.0. Static build succeeds.
- Actual browser Canvas output decoded as 1080 × 1540. A controlled native-share fixture captured the actual generated File for call 12 / number 47: `image/png`, PNG magic bytes, correct IHDR dimensions, 205,144 bytes, and filename `Tambola-call-12-number-47.png`. The fixture does not send data externally. Sharing and cancelling both kept the number/count unchanged.
- Visually checked first-call, 12-call and complete 90-call exports. The completed board marked all 90 cells with 90 latest and recent calls 90 through 81. Mobile preview/fallback screenshots at 390px and 320px showed readable controls and reachable Share/Download buttons. The production UI at 320px had equal document client/scroll widths (305px excluding the scrollbar).
- After browser network emulation went offline and the app reloaded, Undo generated a new PNG for call 11 / number 82 with the corrected recent list and board. Reset removed the old preview/download URLs and disabled image sharing. No browser console errors observed. All temporary emulation was restored.
- Real Android/iPhone WhatsApp handoff and delivered-image appearance still need a phone check. No WhatsApp message was sent. Native sharing capability and target availability depend on the browser/device; the user selects the group and confirms Send. Download PNG remains available through Preview.

## V1.4 players, prize schemes and results — 2026-09-24

- Added optional local player setup and standard/custom prize schemes, editable before and during play. Each scheme defaults to ₹10 total. Up to two distinct players can be selected through native dropdowns; amounts split exactly in paise. Prize tracking does not transfer money or verify paper tickets.
- `npm test`: **55 passed**, including eleven new tests for optional tracking, one/two-winner splits, odd-rupee splits, invalid awards, midgame edits, recorded-prize preservation, undo positions, protected assignments, custom/disabled schemes, new-round retention, combined player totals and saved-game migration. `npm run build` and `git diff --check` pass.
- Existing v1 saves migrate to the separate v2 key without losing calls, voice preferences or free-text winner notes. The v1 key stays untouched as a backup; older tabs cannot overwrite the upgraded save. Legacy notes are displayed without guessing individual identities; only explicitly selected players contribute to payout totals.
- In-app Chromium at 390px: added three players and a custom scheme, drew five calls, assigned a two-person Early 5 tie and confirmed ₹5 each. Duplicate selections were disabled. Renaming Asha to Asha S updated the results; changing the scheme default from ₹10 to ₹20 left the recorded award at ₹10. Explicitly editing that claim to ₹20 produced ₹10 per winner. Assigned-player removal and disabling a claimed scheme were blocked.
- An isolated local browser fixture seeded a real v1 save with five calls and the note “Asha & Meera”. The upgraded UI retained both. Add & select created the two players directly from the claim dialog; adding a third winner was disabled. The results message and decoded WhatsApp link matched exactly. With native sharing unavailable, Share results showed the Open WhatsApp / Copy fallback without changing the game.
- Removed an unused scheme, added Four corners, saved, and reloaded: setup and the ₹5/₹5 award persisted. With browser networking offline, a further reload restored the players, schemes, call count and shareable results. New game cleared calls and awards, disabled result sharing, and retained the two-player/five-scheme setup.
- Inspected 390px and 320px views. Narrow-screen scheme names and quick-add controls were given separate rows after the initial screenshot showed cramped fields. Final 320px document client/scroll widths both measured 305px, with the Four corners name field 172px wide. The final browser console had no errors or warnings; network, cache and viewport emulation were restored.
- Offline cache v1.4.0 includes both new prize modules and the existing 90 voice clips. Results can be prepared offline. Actual Android/iPhone share-sheet handoff and WhatsApp delivery remain unverified; no message was sent. Sharing still requires choosing the group and confirming Send in WhatsApp.
