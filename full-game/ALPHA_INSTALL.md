# Tambola Together 0.2.0-alpha02

This is an internal **offline alpha**, signed with an Android debug certificate. It is not the production release. Use fictional player names while testing. A future release signed with the production certificate may require uninstalling this alpha, which clears its local history.

Android 8 or later is required. The app installs separately from Tambola Keyboard; enabling a keyboard is not needed.

1. Copy `Tambola-Together-0.2.0-alpha02.apk` to your Android phone and open it. This version uses the same debug signing identity as alpha01, so Android can update that installation without clearing its data.
2. If Android asks, allow APK installation for the app you opened the file from.
3. Open **Tambola Together**, select **Play solo** or **Play on one device**, then configure your round and tap **Deal the tickets**.
4. Use **Call next number** or **Auto**. Tap **Mark ticket** for large buttons, or select assisted marking during setup.
5. Open **Check claims** for automatically verified prizes. **Inspect** shows the required numbers, missing calls and winning tickets. Finish at the chosen house, or continue through all 90 calls when that option is enabled.
6. **Play another round** keeps your players, ticket allowance and rules in setup; dealing starts a new round with fresh tickets. **Share these results** first shows a message preview. Player names are excluded unless you opt in.

Settings include English/Hindi/Hinglish calling, voice on/off, haptics, reduced motion, and calling pace. Leaving the app pauses offline calling. Reopen and choose **Resume round** to continue. Number recordings are AI-generated and bundled; the app has no Internet permission and needs no API key.

Included now: digital tickets, solo/computer/family play, standard prizes, one/two/three house settings, 90-call play, custom pattern prizes, points and ties, local round history, pause/resume, practice undo, number board, inspected claims, rematches and controlled text result sharing.

## Make a custom prize

In setup, choose **Create custom prize**. Name it, set its points, and select numbers by row, column, range or populated position. Every condition in one group must match (AND); any one complete group can qualify (OR). Specify which owned tickets count and how many must match. Use **Winning example** and **Clear sample calls** to try the rule, or tap the sample numbers yourself. These sample tickets are separate from the real deal.

Save the prize, then deal the tickets. Rules and points stay fixed for that round. A normal round can end at its final house while a custom prize remains unawarded; choose **Call all 90 numbers** if you want to continue. An empty selected range never wins. Lowering the ticket allowance below a custom prize's requirement keeps the rule and explains what needs correcting.

Still in development: native private online rooms (the backend is locally tested), badges, final music/art/celebrations, Hindi interface, full device/accessibility/performance testing, and production signing/deployment. A regional prize name is only a label; its exact pattern must be configured. The documented alpha checks are emulator evidence, not physical-phone acceptance or a production-readiness claim.
