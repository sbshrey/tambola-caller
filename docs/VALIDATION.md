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
