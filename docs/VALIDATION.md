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

## Not established by these checks

- Actual sound output, installed voices, and offline voice availability on a physical Android phone or iPhone.
- Home-screen installation and mobile Safari behavior on real devices.
- Public hosting, GitHub push, or Vercel deployment.
- Remote multiplayer and automatic ticket verification, which are outside V1.
