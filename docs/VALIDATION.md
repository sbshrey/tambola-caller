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

## Not established by these checks

- Actual sound output, installed voices, and offline voice availability on a physical Android phone or iPhone.
- Home-screen installation and mobile Safari behavior on real devices.
- Vercel deployment (GitHub Pages is the selected host).
- Remote multiplayer and automatic ticket verification, which are outside V1.
