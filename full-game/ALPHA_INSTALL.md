# Tambola Together alpha23 — Wi-Fi testing

For players outside this Wi-Fi network, use the separate [Internet beta download](https://github.com/sbshrey/tambola-caller/releases/tag/full-game-alpha24-internet-beta). It installs as **Tambola Internet Beta** with its own player profile. The PC host must remain online. [Internet hosting details](../docs/INTERNET_BETA.md).

For design feedback without installing, use the [editable landscape Figma board](https://www.figma.com/design/MY3fG0NL8iLCs8sIZz9xqt/?node-id=4-2696) or [clickable browser preview](../designs/landscape-lobby-v1/README.md).

Install [the optimized alpha23 Wi-Fi APK](releases/0.23.0-alpha23-wifi-optimized/Tambola-Together-0.23.0-alpha23-wifi-optimized.apk) on Android 8 or newer. It now includes the Game night welcome/lobby, animated roster arrivals, five-second call ring, minimal Settings, cream tickets, coral per-ticket Claim buttons and adaptive prize picker. Install over the previous full-game alpha and keep its data; uninstalling or resetting can lose the saved wallet. Tambola Keyboard is separate and is not required.

The 29.1 MB APK disables debugging and uses code/resource shrinking with the existing development signature. It passed a complete **78-call round**, **all seven prizes**, exact wallet settlement, a forced restart with **20 retained marks**, remembered ticket quantity and a replay refund. Both QA profiles were deleted. [The evidence](reviews/game-night-alpha23-2026-09-27/README.md) also preserves earlier harness failures, a Windows-restart interruption and their cleanup. The earlier normal/150% English/Hindi UI suite used an isolated review package. The nine-round/hour endurance belongs to alpha22; physical-phone performance remains unverified.

## Connect and play

1. On this PC, run the [firewall setup](../docs/WIFI_HOST.md#one-administrator-action-for-phone-testing) once in Administrator PowerShell. Keep the PC signed in, awake and connected.
2. Connect the phone to the same local network as `192.168.1.4`. Guest-network isolation may prevent access. The app uses private HTTPS port 8443; no adb mapping or certificate installation is required on the phone.
3. Choose 1–6 tickets and tap **Play**. A new profile gets 1,500 free coins; each ticket costs 100. Sales close after 12 seconds, then calls arrive every five seconds. Empty seats fill with labelled computers using fictional gamer-style handles such as ChaiChamp, NeonNinja and LuckyMango. The [server update](reviews/gamer-handles-server-2026-09-27/README.md) is deployed; an existing compatible Wi-Fi APK receives these names without reinstalling.

Mark numbers manually. One or two readable tickets appear at once; use up/down controls for more. Each ticket has its own **Claim** button and prize picker. The ticket pool funds six to eight prizes; house prizes open in order. Results show winnings and offer another round. Coins have no purchase, transfer or cash redemption.

## Saved wallet and settings

Automatic session renewal keeps the same wallet, tickets and pending requests on the same installation. Expired legacy profiles without an enrolled device credential cannot be silently recovered. Recovery after reinstalling, resetting, losing the device or losing its encryption key remains unavailable.

Settings offer language, caller voice, music, effects, vibration and reduced motion. Your game data explains retention and deletion. Confirmed profile deletion removes server access and wallet data; sign-out revokes access without immediately erasing shared game history. Recovery records prevent older server backups from reactivating credentials already recorded as deleted or signed out.

APK SHA-256: `872b505cd397397940d88d190d65f7748ad7c4a85c17d397d7a3de390265d033`. This is a **private-network development build**, not a Store release. Physical-phone Wi-Fi, smoothness, controlled Windows restart recovery and live disaster cutover remain unverified. One post-login server recovery was observed after an unplanned restart. See [host operation](../docs/WIFI_HOST.md) and [remaining release work](../docs/ONLINE_COIN_GAME_PLAN.md).
