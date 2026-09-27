# Tambola Together alpha21 — Wi-Fi testing

Install [the optimized alpha21 Wi-Fi APK](releases/0.21.0-alpha21-wifi-optimized/Tambola-Together-0.21.0-alpha21-wifi-optimized.apk) on Android 8 or newer. This is a private-network development build with the existing development signing certificate. Install over the previous full-game alpha and keep its data; uninstalling or resetting can lose access to the saved wallet. Tambola Keyboard is a separate app and is not required.

Alpha21 fixes a cancellation race that could show “room no longer available” after a refund or discard the saved retry when its response was lost. It preserves the exact cancellation until confirmed. It also retains remembered ticket quantities, affordable choices, server-based free-refill timing and the earlier animation/results improvements. Use this APK with the [updated Wi-Fi service](reviews/server-wifi-2026-09-27/README.md).

The optimized build is 29.1 MB, uses code/resource shrinking and disables debugging. It passed an 87-call online round, a forced app restart with 20 retained marks, all eight prizes, remembered ticket quantity and a replay refund. A separate native regression verifies a lost cancellation response and exact retry. Emulator timing still misses the smoothness target; physical-phone performance remains unverified.

## Connect and play

1. On this PC, run the [firewall setup](../docs/WIFI_HOST.md#one-administrator-action-for-phone-testing) once in Administrator PowerShell. Keep the PC signed in, awake and connected.
2. Connect the phone to the same local network as `192.168.1.4`. Guest-network isolation may prevent access. The app uses the PC's private HTTPS endpoint on port 8443; no adb mapping or certificate installation is required on the phone.
3. Choose 1–6 tickets and tap **Play**. A new profile receives 1,500 free coins. Tickets cost 100 each; sales close after 12 seconds and calls arrive every five seconds. Empty seats fill with labelled computers.

Mark numbers manually. One or two readable tickets appear at once; use the up/down controls for more. Each ticket has its own **Claim** button and prize picker. The round's ticket pool funds six to eight prizes; house prizes open in order. Results show winnings and offer another round. Coins have no purchase, transfer or cash redemption.

## Saved wallet and settings

Alpha17 renews expired game sessions automatically on the same installation. It keeps the same wallet, tickets and pending requests. Previously expired legacy profiles that never enrolled a device credential cannot be silently recovered or replaced. Recovery after reinstalling, resetting, losing the device or losing its encryption key remains unavailable.

Settings retain language, caller audio, music/effects and reduced-motion preferences. Your game data explains retention and deletion. Confirmed profile deletion removes server access and wallet data; sign-out revokes access without immediately erasing shared game history. Recovery records prevent older server backups from reactivating deleted or signed-out credentials recorded by this version.

The [alpha21 candidate evidence](reviews/leave-receipt-2026-09-27/README.md) distinguishes completed checks from pending acceptance. APK SHA-256: `0ad8d86287ae91902847475a7ac09ec104abf78db6303b5fa5975040cfd08716`. This is not a Store release or proof of physical-phone Wi-Fi, smooth animation performance, Windows reboot behavior or installed-backup restoration. See [host operation](../docs/WIFI_HOST.md) and [the remaining release work](../docs/ONLINE_COIN_GAME_PLAN.md).
