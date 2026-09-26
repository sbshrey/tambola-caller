# Tambola Together alpha19 — Wi-Fi testing

Install [the optimized alpha19 Wi-Fi APK](releases/0.19.0-alpha19-wifi-optimized/Tambola-Together-0.19.0-alpha19-wifi-optimized.apk) on Android 8 or newer. This is a private-network development build with the existing development signing certificate. Install over the previous full-game alpha and keep its data; uninstalling or resetting can lose access to the saved wallet. Tambola Keyboard is a separate app and is not required.

Alpha19 removes repeated ticket-grid composition during dab animations while preserving the colour change, stamp feedback and motion controls. It retains alpha18's personal prize shares and reduced idle lobby work. The existing alpha17 Wi-Fi service remains running and compatible.

The optimized build is 29.1 MB, uses code/resource shrinking and disables debugging. It passed a complete online round, a forced app restart with retained marks, settlement and a replay refund. Emulator timing still misses the smoothness target; physical-phone performance remains unverified.

## Connect and play

1. On this PC, run the [firewall setup](../docs/WIFI_HOST.md#one-administrator-action-for-phone-testing) once in Administrator PowerShell. Keep the PC signed in, awake and connected.
2. Connect the phone to the same local network as `192.168.1.4`. Guest-network isolation may prevent access. The app uses the PC's private HTTPS endpoint on port 8443; no adb mapping or certificate installation is required on the phone.
3. Choose 1–6 tickets and tap **Play**. A new profile receives 1,500 free coins. Tickets cost 100 each; sales close after 12 seconds and calls arrive every five seconds. Empty seats fill with labelled computers.

Mark numbers manually. One or two readable tickets appear at once; use the up/down controls for more. Each ticket has its own **Claim** button and prize picker. The round's ticket pool funds six to eight prizes; house prizes open in order. Results show winnings and offer another round. Coins have no purchase, transfer or cash redemption.

## Saved wallet and settings

Alpha17 renews expired game sessions automatically on the same installation. It keeps the same wallet, tickets and pending requests. Previously expired legacy profiles that never enrolled a device credential cannot be silently recovered or replaced. Recovery after reinstalling, resetting, losing the device or losing its encryption key remains unavailable.

Settings retain language, caller audio, music/effects and reduced-motion preferences. Your game data explains retention and deletion. Confirmed profile deletion removes server access and wallet data; sign-out revokes access without immediately erasing shared game history. Recovery records prevent older server backups from reactivating deleted or signed-out credentials recorded by this version.

The [alpha19 candidate evidence](reviews/dab-animation-2026-09-27/README.md) distinguishes completed checks from pending acceptance. This is not a Store release or proof of physical-phone Wi-Fi, smooth animation performance, Windows reboot behavior or installed-backup restoration. See [host operation](../docs/WIFI_HOST.md) and [the remaining release work](../docs/ONLINE_COIN_GAME_PLAN.md).
