# Tambola Together 0.4.0-alpha04

This is an internal **development alpha**, signed with an Android debug certificate. Solo and family play work offline. Native private rooms require a separately configured room service; this package targets a local test service, not a public hosted service. It is not the production release. Use fictional player names while testing. A future release signed with the production certificate may require uninstalling this alpha, which clears its local history.

Android 8 or later is required. The app installs separately from Tambola Keyboard; enabling a keyboard is not needed.

1. Copy `Tambola-Together-0.4.0-alpha04.apk` to your Android phone and open it. This version uses the same debug signing identity as the earlier alphas, so Android can update that installation without clearing its data.
2. If Android asks, allow APK installation for the app you opened the file from.
3. Open **Tambola Together**, select **Play solo** or **Play on one device**, then configure your round and tap **Deal the tickets**.
4. Use **Call next number** or **Auto**. Tap **Mark ticket** for large buttons, or select assisted marking during setup.
5. Open **Check claims** for automatically verified prizes. **Inspect** shows the required numbers, missing calls and winning tickets. Finish at the chosen house, or continue through all 90 calls when that option is enabled.
6. **Play another round** keeps your players, ticket allowance and rules in setup; dealing starts a new round with fresh tickets. **Share these results** first shows a message preview. Player names are excluded unless you opt in.

Settings include English/Hindi/Hinglish calling, voice on/off, haptics, reduced motion, and calling pace. Leaving the app pauses offline calling. Reopen and choose **Resume round** to continue. Number recordings are AI-generated and bundled. Internet permission supports private rooms; no OpenAI key is included or needed to play.

Included now: digital tickets, solo/computer/family play, standard prizes, one/two/three house settings, 90-call play, custom pattern prizes, points and ties, local round history, pause/resume, practice undo, number board, inspected claims, rematches and controlled text result sharing.

## Learn and collect badges

Choose **Learn with a sample ticket** on the welcome card or **How to play** from Home. The five-part interactive lesson explains the ticket, calls sample number 7, lets you mark it, and demonstrates an automatically verified top-line win. Voice/language controls are available at the start. You can skip or replay it. The sample cannot replace your current game, save a round, or earn points/badges in real play.

Open **Your badges** on Home to see **First round**, **First full house**, and **Five together**. Solo practice, games against computers, the family table and your current online profile have separate milestones. Cancelled rounds never count. A computer's house does not earn your badge; a family's house badge belongs to the shared table. Full house and all ranked houses qualify, including same-call ties. Reopening a result does not count it again.

Offline badges come from saved completed rounds; deleting those rounds clears their badges. Online badges remain in encrypted local profile data even after old results leave the 50-result cache. Signing out or resetting online data removes the profile's local badges. These are social milestones on this device, not a global leaderboard or a promise of account recovery.

## Make a custom prize

In setup, choose **Create custom prize**. Name it, set its points, and select numbers by row, column, range or populated position. Every condition in one group must match (AND); any one complete group can qualify (OR). Specify which owned tickets count and how many must match. Use **Winning example** and **Clear sample calls** to try the rule, or tap the sample numbers yourself. These sample tickets are separate from the real deal.

Save the prize, then deal the tickets. Rules and points stay fixed for that round. A normal round can end at its final house while a custom prize remains unawarded; choose **Call all 90 numbers** if you want to continue. An empty selected range never wins. Lowering the ticket allowance below a custom prize's requirement keeps the rule and explains what needs correcting.

## Private rooms in this build

The default debug endpoint is `http://127.0.0.1:8080`. It is useful with the local service and a dedicated Android emulator using `adb reverse tcp:8080 tcp:8080`; it does not connect a phone to a hosted game by itself. See [the native client guide](client/README.md) for configuration and test commands.

With a reachable configured service, select **Play online**, choose a display name, and create a room or join with its eight-character code. The host can change standard/custom rules before play; everyone must ready again after a change. The host starts once at least two connected players are ready and enough tickets exist for the chosen house prizes. Each player sees only their own cards.

Calls and prizes are server-confirmed. Host controls support pause/resume, ending and rematches. The room can continue while you leave the app; returning catches up without replaying old announcements. If an action cannot be confirmed, **Retry pending action** checks its original result rather than creating a second action. Completed results have a private sharing preview and encrypted local online history.

Online profiles and cached rooms are encrypted on the device. Signing out revokes the session and removes local online data; an explicit recovery reset removes local data only. Server-side account deletion and recovery remain unfinished. The room service has a separate retention policy; offline-round deletion does not delete server records.

Still in development: hosted online rooms and broader multiplayer recovery acceptance, final music/art/celebrations, Hindi interface, full device/accessibility/performance testing, and production signing/deployment. A regional prize name is only a label; its exact pattern must be configured. The documented alpha checks are emulator evidence, not physical-phone acceptance or a production-readiness claim.
