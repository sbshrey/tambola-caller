# Tambola Caller · V1

A small, mobile-first caller for a group playing with **physical Tambola tickets**. One host opens the app and taps **Next number**. No account, backend, paid service, third-party fonts, analytics, or runtime dependencies.

Built from the [shared V1 discussion and design reference](https://chatgpt.com/share/6ab450f5-c28c-83e8-ba0d-e1a38e598305).

**[Play Tambola on GitHub Pages](https://sbshrey.github.io/tambola-caller/)**

## Run locally

Install Node.js 22 or later, then run:

```sh
npm start
```

Open **http://127.0.0.1:4173**. No `npm install` step is necessary. Serve the app over HTTP rather than opening `index.html` as a file: JavaScript modules and service workers need a web origin.

```sh
npm test       # game, storage, speech adapter and offline-worker regression tests
npm run build # copies only the static app to dist/
```

## Play

- Tap **Next number** for a random 1–90 number, without repeats. A short double-tap guard prevents accidental rapid calls.
- **Voice on** announces each number using the device's speech engine, preferring installed Indian English, then installed English voices. **Say it again** repeats the latest number even if automatic voice is off.
- The board distinguishes the latest number from earlier calls. The last ten calls appear latest first; **View all** shows complete chronological history.
- **Share number** prepares the latest number and its spelling, the call count, and the five most recent calls. Choose **WhatsApp → your group → Send** in your phone's sharing menu. If that menu is unavailable, the app offers **Open WhatsApp** with the message prefilled, or **Copy message**. The **Copy** button beside Share number copies the same text directly. Sharing never draws another number and cancelling it keeps the game unchanged. You confirm sending in WhatsApp; the app cannot confirm delivery or send automatically.
- **Undo last** returns the latest number to the pool. Claims recorded on that call are also removed, with confirmation.
- **New game** asks for confirmation before clearing numbers and claims. It keeps the voice preference.
- After checking a paper ticket, record **Early 5**, **Top line**, **Middle line**, **Bottom line**, or **Full house**. A winner name is optional and can contain several names for a tie. Tap a recorded claim to edit or remove it. Claims unlock at the earliest possible call (5 or 15); the app does not verify tickets.
- Games and voice settings are saved in this browser. Refreshing resumes the game without speaking unexpectedly. Other tabs on the same origin pick up saved changes; use one host tab to avoid simultaneous draws.
- Once **Ready for offline play** appears, the cached app can reopen offline. Actual offline speech depends on the voices installed on the device. Browsers may evict site data; clearing it removes the saved game and offline cache.
- **Share app** copies the app URL for you to send. Each device runs an independent game; it does not share the host's live board. Keep everyone together, or use your usual group call to hear the host.

For remote play, only the host draws numbers. Other players can stay in WhatsApp, read the shared messages, and mark their paper tickets. Sharing a number sends only the call information, without winner names or the app link. Undo and New game do not change messages already sent to WhatsApp; tell your group about corrections or a new round. The app and message preparation work offline after caching, but WhatsApp needs connectivity to deliver messages.

Example number message:

```text
🎱 *47 — Forty seven*
Call 12 of 90
Recent: 47, 82, 13, 66, 5
```

After an update, close all open Tambola tabs and reopen the app so the new offline version can activate. If needed, reopen online once, close it, then open it again. Saved progress stays on the same browser and device.

## Deploy

GitHub Pages is the primary host. GitHub's built-in Pages deployment publishes the app from `main` after each push. The source is already a complete static site; no custom workflow or build service is required. `.nojekyll` tells Pages to serve the files directly.

The repository's **Settings → Pages → Build and deployment** uses **Deploy from a branch**, branch **main**, folder **/ (root)**. Run `npm test` and `npm run build` before pushing changes; branch deployment does not run the regression suite for you. No deployment secrets or paid services are needed.

Run `npm run build` and publish the **dist/** directory to an HTTPS static host. All URLs are relative, so the app also works below a path such as `/tambola-caller/`.

For **Vercel**, import this repository, use “Other” as the framework, `npm run build` as the build command, and `dist` as the output directory. These settings are also in `vercel.json`. No environment variables are required. For GitHub Pages or Cloudflare Pages, publish the same directory.

Service workers require HTTPS, except on localhost. A phone visiting a plain HTTP LAN address can use the caller but cannot cache it for offline reopening. Use an HTTPS deployment for phone testing and sharing. A localhost URL cannot be opened by friends on their devices.

When releasing changed app assets, increment the cache version in **sw.js**. An installed update waits until all old app tabs close; reopening then uses the complete new asset set without replacing an active game midway.

## Structure

- `index.html`, `styles.css`: accessible interface and responsive layout.
- `src/game.js`: immutable game transitions, unbiased random selection, save validation.
- `src/app.js`: DOM interactions, confirmation dialogs, and state coordination.
- `src/storage.js`, `src/voice.js`: browser adapters with graceful failure.
- `sw.js`, `manifest.webmanifest`, `icons/`: offline app and home-screen metadata.
- `scripts/`: dependency-free local server and static build.
- `tests/`: Node's built-in test runner; speech tests use a fake device adapter, not real audio.

## Scope

V1 is a caller, not a WhatsApp bot or an online multiplayer room. Digital tickets, automatic claim verification, remote board syncing, accounts, payments, and player registration are outside this version.

Before sharing with the group, check one real Android/iPhone for volume, voice quality, home-screen installation, and offline reopening. Automated adapter tests do not establish that a particular phone can play audio.
