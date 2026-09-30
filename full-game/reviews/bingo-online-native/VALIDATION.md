# Native online Bingo checkpoint

Source progress for Tambola Jalsa on `shrey/tambola-jalsa`; not a public APK or live server deployment.

## Integrated behavior

- The shared home opens a compact online Bingo lobby. One to six cards, public Play, friends-table create/join and a separate Practice action are available.
- Bingo reuses the existing authenticated profile, device-session renewal, wallet and encrypted save. Purchases and commands persist their exact retry identifiers before sending. Card-count preferences remain separate from Tambola.
- A polling connection restores private server snapshots and backs off after network failures. Revision conflicts refresh the table; uncertain commands retain their original request for retry. No client-side prize credit is performed.
- The native 5x5 card uses the existing card renderer, called-number header, bottom paging arrows, claim availability, own-win confetti and ranked results with the current player highlighted and scrolled into view.
- Active online Bingo blocks APK installation prompts and rewarded ads. Background/home navigation stops Bingo audio and win effects. English and Hindi labels are included.

## Evidence and remaining boundaries

The `bingoAndroid` fixture creates a disposable PostgreSQL schema on localhost port 55432, runs a test server on port 8080, maps only the owned emulator, and removes its test profile and schema afterward. It never uses the live database on port 55433. It refuses to overwrite an existing debug profile.

Initial native acceptance passed in 27.275 seconds: six-card purchase, arrow paging, a server-confirmed mark, fresh ViewModel disk restoration, reconnect to the same table and profile deletion. `six-cards.png` shows the portrait card layout without page scrolling.

The final native build passed the same real-server test in 26.949 seconds (`native-test.txt`). All 53 client tests passed, including save/reload of the exact Bingo purchase and command alongside an independent Tambola ticket preference. Existing practice navigation and the two practice claim/results/reset tests passed. The Hindi 200% practice accessibility test passed in 8.024 seconds after correcting a test-only extra lobby tap; that fixture directly renders practice rather than the app destination.

Full native online final-claim/results, deliberate network-loss/retry, Hindi/large-font online layout and physical-phone acceptance still need verification. A fresh ViewModel test is not an OS process-kill test. Deployment and same-signer APK upgrade acceptance remain outstanding.
