# Tambola Caller · V1

A small, mobile-first caller for a group playing with **physical Tambola tickets**. One host opens the app and taps **Next number**. No account, backend, runtime API calls, third-party fonts, analytics, or runtime dependencies. The voice clips were generated once using the paid OpenAI API; playing and sharing them needs no key.

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
- **Voice on** plays a prerecorded AI voice clip: Indian English, familiar short Tambola calls where appropriate, individual digits and the full number. All 90 MP3s are included. **Say it again** repeats the latest number even if automatic voice is off. If a clip fails, the device speech engine is the fallback. Dynamic winner names and voice-toggle messages still use device speech.
- **Share board image** prepares a PNG containing the current number and spelling, last ten calls (latest first), call count, remaining count, and full 1–90 board. Called numbers are marked and the latest number is highlighted. Tap it, then choose **WhatsApp → your group → Send**. The PNG is prepared after each draw so the share menu can open directly from your tap. **Preview** lets you inspect it or **Download PNG** to attach in WhatsApp. If native image sharing is unavailable, the preview/download opens automatically. Images contain no winner names and are generated entirely on the device, including offline.
- The board distinguishes the latest number from earlier calls. The last ten calls appear latest first; **View all** shows complete chronological history.
- **Share number** prepares the latest number and its spelling, the call count, and the five most recent calls. Choose **WhatsApp → your group → Send** in your phone's sharing menu. If that menu is unavailable, the app offers **Open WhatsApp** with the message prefilled, or **Copy message**. The **Copy** button beside Share number copies the same text directly. Sharing never draws another number and cancelling it keeps the game unchanged. You confirm sending in WhatsApp; the app cannot confirm delivery or send automatically.
- **Share audio clip** opens the phone’s file sharing menu. Choose WhatsApp, the group, then Send. If file sharing is unavailable, use **Download MP3** and attach the saved file in WhatsApp. The clip is an audio attachment; it is not guaranteed to appear as a WhatsApp voice note. Text sharing remains available.
- **Undo last** returns the latest number to the pool. Claims recorded on that call are also removed, with confirmation.
- **Manage** optionally adds player names and configures prize schemes before or during play. Add names one per line or separated by commas; rename or remove players, change scheme names/prizes, untick unused schemes, or add custom ones. There is no setup requirement to call numbers.
- After checking a paper ticket, tap **Early 5**, **Top line**, **Middle line**, **Bottom line**, **Full house**, or a custom scheme. Choose up to **two winners** from the player dropdowns; **Add & select** adds a missing player on the spot. The default total prize is **₹10**: one winner gets ₹10, two receive ₹5 each. Enter any whole-rupee prize (₹0 for no cash prize); odd amounts split to paise, e.g. ₹11 gives ₹5.50 each. Winners can be left blank and assigned later. Tap a recorded claim to edit or remove it. Standard claims unlock after 5 or 15 calls; custom schemes after the first call. The host verifies tickets.
- **Winners & share results** opens a summary of scheme winners, individual payouts, and each player's combined total. At the end of your round, tap **Share results → WhatsApp → your group → Send**, or use **Open WhatsApp** / **Copy**. You can also share an interim update without waiting for all 90 calls. Unclaimed schemes and unassigned winners are labelled clearly; only explicitly assigned players count towards totals. This tracks prizes; it does not transfer money.
- Editing a scheme's default prize affects future awards. To revise a recorded payout, edit that claim directly. Renaming a player updates their results; assigned players and claimed schemes cannot be removed until their winner assignments or claims are removed.
- **New game** asks for confirmation before clearing numbers and claims. It keeps players, schemes, prize defaults and the voice preference for the next round. Share the results before starting a new round.
- Games, setup and voice settings are saved in this browser. Refreshing resumes the game without speaking unexpectedly. Existing saves upgrade automatically: calls and free-text winner notes are preserved, and the original save remains as a backup. Choose players on an old claim to include it in payout totals. Other tabs on the same origin pick up saved changes; use one host tab to avoid simultaneous draws.
- Once **App + 90 voice clips ready offline** appears, the app and roughly 6.6 MB voice pack can reopen and play offline. Initial caching needs connectivity; keep the page open until ready. Fallback speech and winner names depend on installed device voices. Browsers may evict site data; clearing it removes the saved game and offline cache.
- **Share app** copies the app URL for you to send. Each device runs an independent game; it does not share the host's live board. Keep everyone together, or use your usual group call to hear the host.

For remote play, only the host draws numbers. Other players can stay in WhatsApp, read the shared messages, and mark their paper tickets. Sharing a number sends only the call information, without winner names or the app link. Undo and New game do not change messages already sent to WhatsApp; tell your group about corrections or a new round. The app and message preparation work offline after caching, but WhatsApp needs connectivity to deliver messages.

A shared image is a snapshot of that call, not a live board. Undo rebuilds the image and New game clears it until the first new number. Older image encodes cannot replace the latest board, and obsolete in-memory image URLs are released. PNGs are 1080 × 1540 pixels (typically around 200 KB), independent of screen size. They use the browser’s Canvas API without external image/font downloads or a server. The app cannot preselect a WhatsApp group or confirm delivery; the sender chooses the group and taps Send.

Example number message:

```text
🎱 *47 — Forty seven*
Call 12 of 90
Recent: 47, 82, 13, 66, 5
```

After an update, close all open Tambola tabs and reopen the app so the new offline version can activate. If needed, reopen online once, close it, then open it again. Saved progress stays on the same browser and device.

## Generate or change voice clips

The website never calls OpenAI. The local generator reads `OPENAI_API_KEY` from the process environment; no key is written to metadata, copied by the build, or needed in GitHub Pages settings. Keep keys out of source and chat. Local `.env*` files are ignored but are not automatically loaded.

Edit `scripts/calls.mjs` for wording, voice and instructions. The default is `gpt-4o-mini-tts`, voice `coral`, prompted as a warm female host with natural Indian English. Accent and perceived voice character should be auditioned; they are not guaranteed by a preset name. Nicknames vary across groups, so many calls use only a clear number instead of a forced rhyme.

```sh
node scripts/generate-audio.mjs --samples       # 7, 22, 90
node scripts/generate-audio.mjs --all           # complete/resume 1–90
node scripts/generate-audio.mjs --numbers 22    # regenerate if script/settings changed
```

Generation and retakes incur API usage. Unchanged, intact clips are skipped using request fingerprints and file hashes in `audio/manifest.json`. Failures stop without automatic billable retries; rerunning resumes. To intentionally retake an unchanged clip, delete that clip locally, then select its number. The manifest records the script, voice/model and generation time without credentials. The app discloses that number voices are AI-generated.

After changing clips, run tests/build, bump the service-worker cache version and publish. The build requires all 90 clips. See the official [OpenAI text-to-speech documentation](https://developers.openai.com/api/docs/guides/text-to-speech).

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
- `src/prizes.js`, `src/prize-ui.js`: optional player/scheme setup, prize splitting, winner dropdowns and shareable results.
- `src/storage.js`, `src/voice.js`: browser adapters with graceful failure.
- `sw.js`, `manifest.webmanifest`, `icons/`: offline app and home-screen metadata.
- `src/audio.js`: clip URLs and audio file preparation.
- `src/board-image.js`: PNG rendering and preparation tied to the current called-number sequence.
- `src/sharing.js`: reusable text/file sharing and copy fallbacks for PNGs and audio.
- `audio/numbers/`, `audio/manifest.json`: 90 reusable MP3s and generation provenance.
- `scripts/`: dependency-free local server, static build and local audio generator.
- `tests/`: Node's built-in test runner; speech tests use a fake device adapter, not real audio.

## Scope

V1 is a caller with optional local player and prize tracking, not a WhatsApp bot or an online multiplayer room. Digital tickets, automatic claim verification, remote board syncing, accounts, payments, and online player registration are outside this version.

Before sharing with the group, check one real Android/iPhone for volume, voice quality, home-screen installation, and offline reopening. Automated adapter tests do not establish that a particular phone can play audio.
