# Tambola Caller · V1

A small, mobile-first caller for a group playing with **physical Tambola tickets**. One host opens the app and taps **Next number**. No account, backend, runtime API calls, third-party fonts, analytics, or runtime dependencies. The voice clips were generated once using the paid OpenAI API; playing and sharing them needs no key.

Built from the [shared V1 discussion and design reference](https://chatgpt.com/share/6ab450f5-c28c-83e8-ba0d-e1a38e598305).

**[Play Tambola on GitHub Pages](https://sbshrey.github.io/tambola-caller/)**

## Android keyboard for WhatsApp hosts

**[Tambola Keyboard v1.1.0: installation and usage](android-keyboard/README.md)** (Android 8+). Enable it once, open your WhatsApp group, then tap **Next number → WhatsApp Send**. Large **Call · Board · Winners** tabs provide emoji numbers, saved progress, offline English/Hindi/Hinglish voices, number/recent/board pictures, and optional players/prizes with two-winner splits. Players keep using paper tickets. Sending stays under the host's control.

The keyboard runs its own game, separately from this website; use one caller for the whole round. It includes a separate practice area. See the [installation guide](android-keyboard/README.md) and [validation notes](android-keyboard/VALIDATION.md).

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
- Choose **Call language → English (India), हिन्दी, or Hinglish** before or during play. Each has all 90 sayings and prerecorded AI voice clips. Hindi uses Hindi sayings and numbers; Hinglish combines Hindi sayings with English digits and number names. **Try voice** previews 22 without drawing a number. The choice is saved and kept for the next round. Sayings, number messages, shared MP3s, and number words on PNGs follow the selection; interface and winner-management controls stay in English. See the [90-number catalog and references](docs/CALLS.md); Hindi includes familiar Indian references and labelled adaptations, since groups use different calling traditions.
- **Voice on** plays the selected language's clip with the saying, individual digits and full number. **Say it again** repeats the latest number even if automatic voice is off. If a clip fails, the device speech engine is the fallback; Hindi/Hinglish fallback depends on available Hindi voices. Dynamic winner names and voice-toggle messages still use device speech.
- Tap the **share icon at the top-right** of the current-number card, Recently called, or the board to share just that section as a PNG. The number image includes its spelling and saying in the selected language; the recent image shows the last ten calls, latest first; the board image shows all 90 cells with called/latest marks. Choose **WhatsApp → your group → Send**. No extra selection menu is needed. Unsupported file sharing opens the matching preview and **Download PNG** option. Icons become ready after the first number is called.
- **Share full summary** keeps the combined PNG with the current number, last ten calls, counts, and full board. **Preview** lets you inspect it or **Download PNG** to attach in WhatsApp. All four images are prepared after each draw so the share menu can open directly from your tap. They contain no player/winner names and are generated entirely on the device, including offline.
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
- Once **v1.6 · All 3 languages ready offline** appears, the app and roughly 24 MB of voice clips (270 MP3s) can reopen and play offline. Initial caching needs connectivity; keep the page open until ready. Fallback speech and winner names depend on installed device voices. Browsers may evict site data; clearing it removes the saved game and offline cache.
- **Share app** copies the app URL for you to send. Each device runs an independent game; it does not share the host's live board. Keep everyone together, or use your usual group call to hear the host.

For remote play, only the host draws numbers. Other players can stay in WhatsApp, read the shared messages, and mark their paper tickets. Sharing a number sends only the call information, without winner names or the app link. Undo and New game do not change messages already sent to WhatsApp; tell your group about corrections or a new round. The app and message preparation work offline after caching, but WhatsApp needs connectivity to deliver messages.

A shared image is a snapshot of that call, not a live board. Undo and language changes rebuild all images; New game clears them until the first new number. Older image encodes cannot replace the current snapshots, and preview URLs are released on close or invalidation. PNGs are 1080 pixels wide: current number 1080 high, recent numbers 720, board 1140, and full summary 1540. They use the browser’s Canvas API and installed fonts without external image/font downloads or a server. The app cannot preselect a WhatsApp group or confirm delivery; the sender chooses the group and taps Send.

Example number message:

```text
🎱 *47 — Forty seven*
Call 12 of 90
Recent: 47, 82, 13, 66, 5
```

After an update, close all open Tambola tabs and reopen the app so the new offline version can activate. If needed, reopen online once, close it, then open it again. Saved progress stays on the same browser and device.

## Generate or change voice clips

The website never calls OpenAI. The local generator reads `OPENAI_API_KEY` from the process environment; no key is written to metadata, copied by the build, or needed in GitHub Pages settings. Keep keys out of source and chat. Local `.env*` files are ignored but are not automatically loaded.

Edit `src/call-phrases.js` for sayings, `src/calls.js` for spoken script assembly, and `scripts/calls.mjs` for voice instructions. The default is `gpt-4o-mini-tts`, voice `coral`, prompted as a warm female Indian host with language-specific instructions. Accent and perceived voice character should be auditioned; they are not guaranteed by a preset name. English uses traditional Tambola/bingo calls, Hindi uses familiar references and adaptations, and Hinglish uses the Hindi saying followed by English numbers.

```sh
node scripts/generate-audio.mjs --samples --language hi     # 7, 22, 90
node scripts/generate-audio.mjs --all --language hinglish   # complete/resume 1–90
node scripts/generate-audio.mjs --numbers 22 --language en  # only if changed/missing
```

Generation and retakes incur API usage. Language defaults to `en`. English lives under `audio/`, Hindi under `audio/hi/`, and Hinglish under `audio/hinglish/`; each has its own `numbers/` and `manifest.json`. Unchanged, intact clips are skipped using request fingerprints and file hashes. Failures stop without automatic billable retries; rerunning resumes. To intentionally retake an unchanged clip, delete that clip locally, then select its number and language. Manifests record the script, voice/model and generation time without credentials. The app discloses that number voices are AI-generated.

The optional `scripts/validate-audio.mjs` uses the same number/language flags for a paid transcription audit without the expected script. Reports record hashes and number detections; they do not replace human listening. After changing clips, run tests/build, bump the service-worker cache version and publish. The build requires all 270 clips. See the official [OpenAI text-to-speech documentation](https://developers.openai.com/api/docs/guides/text-to-speech).

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
- `src/board-image.js`, `src/image-ui.js`: four PNG layouts, background preparation, section share icons and a shared preview/download flow.
- `src/sharing.js`: reusable text/file sharing and copy fallbacks for PNGs and audio.
- `src/languages.js`, `src/call-phrases.js`, `src/calls.js`: shared language choices, number names, sayings and speech scripts.
- `audio/`, `audio/hi/`, `audio/hinglish/`: 90 reusable MP3s and a provenance manifest per language.
- `scripts/`: dependency-free local server, static build and local audio generator.
- `tests/`: Node's built-in test runner; speech tests use a fake device adapter, not real audio.

## Scope

V1 is a caller with optional local player and prize tracking, not a WhatsApp bot or an online multiplayer room. Digital tickets, automatic claim verification, remote board syncing, accounts, payments, and online player registration are outside this version.

Before sharing with the group, check one real Android/iPhone for volume, voice quality, home-screen installation, and offline reopening. Automated adapter tests do not establish that a particular phone can play audio.
