# Tambola Together alpha22 — Wi-Fi testing

For onboarding, lobby and layout feedback, use the [new landscape Figma design](https://www.figma.com/design/MY3fG0NL8iLCs8sIZz9xqt/?node-id=3-1215) or [clickable browser preview](../designs/landscape-lobby-v1/README.md). These review screens do not require an APK installation and are not yet included in alpha22.

The welcome/lobby now also has an [isolated native implementation and screenshot evidence](reviews/game-night-lobby-2026-09-27/README.md), tested at normal and 150% text. Its separate emulator-only review package preserves the installed Wi-Fi APK and wallet. It is not the downloadable candidate below.

Install [the optimized alpha22 Wi-Fi APK](releases/0.22.0-alpha22-wifi-optimized/Tambola-Together-0.22.0-alpha22-wifi-optimized.apk) on Android 8 or newer. This is a private-network development build with the existing development signing certificate. Install over the previous full-game alpha and keep its data; uninstalling or resetting can lose access to the saved wallet. Tambola Keyboard is a separate app and is not required.

Alpha22 lets you tap the player area to see everyone at the table and keeps all eight prizes visible with larger text. Closing the list preserves your ticket page and marks. It also retains alpha21's cancellation-retry fix, remembered ticket quantities, affordable choices, server-based free-refill timing and the earlier animation/results improvements. Use this APK with the [updated Wi-Fi service](reviews/server-wifi-2026-09-27/README.md).

The optimized build is 29.1 MB, uses code/resource shrinking and disables debugging. It passed a 75-call online round against two labelled computers, all seven prizes, a correctly shared prize, a forced app restart with 20 retained marks, remembered ticket quantity and a replay refund. English/Hindi UI checks include actual 150% system text. The earlier alpha21 native regression verifies lost cancellation response recovery. Emulator timing still misses the smoothness target; physical-phone performance remains unverified.

The same APK also passed [nine continuous native coin rounds](reviews/coin-endurance-alpha22-2026-09-27/README.md), totaling 68.96 measured minutes, with 758 calls, 65 prize slots, reconnects, process recovery, repeated purchases/refunds and cleanup of all QA profiles. This closes the native endurance correctness check. The report preserves the limits of software-emulator timing and concurrent browser work.

## Connect and play

1. On this PC, run the [firewall setup](../docs/WIFI_HOST.md#one-administrator-action-for-phone-testing) once in Administrator PowerShell. Keep the PC signed in, awake and connected.
2. Connect the phone to the same local network as `192.168.1.4`. Guest-network isolation may prevent access. The app uses the PC's private HTTPS endpoint on port 8443; no adb mapping or certificate installation is required on the phone.
3. Choose 1–6 tickets and tap **Play**. A new profile receives 1,500 free coins. Tickets cost 100 each; sales close after 12 seconds and calls arrive every five seconds. Empty seats fill with labelled computers.

Mark numbers manually. One or two readable tickets appear at once; use the up/down controls for more. Each ticket has its own **Claim** button and prize picker. The round's ticket pool funds six to eight prizes; house prizes open in order. Results show winnings and offer another round. Coins have no purchase, transfer or cash redemption.

## Saved wallet and settings

Alpha17 renews expired game sessions automatically on the same installation. It keeps the same wallet, tickets and pending requests. Previously expired legacy profiles that never enrolled a device credential cannot be silently recovered or replaced. Recovery after reinstalling, resetting, losing the device or losing its encryption key remains unavailable.

Settings retain language, caller audio, music/effects and reduced-motion preferences. Your game data explains retention and deletion. Confirmed profile deletion removes server access and wallet data; sign-out revokes access without immediately erasing shared game history. Recovery records prevent older server backups from reactivating deleted or signed-out credentials recorded by this version.

The [alpha22 candidate evidence](reviews/computer-round-alpha22-2026-09-27/README.md) distinguishes completed checks from pending acceptance. APK SHA-256: `05dfaa405629fec0a5ab57234941eca1229b9f2cb13034a496e3549759bdb4b4`. The server's [copied installed-backup rehearsal](reviews/installed-recovery-2026-09-27/README.md) passed. This is not a Store release or proof of physical-phone Wi-Fi, smooth animation performance, Windows reboot behavior or live disaster cutover. See [host operation](../docs/WIFI_HOST.md) and [the remaining release work](../docs/ONLINE_COIN_GAME_PLAN.md).
