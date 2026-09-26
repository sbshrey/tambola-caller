# Tambola Together alpha17 — Wi-Fi test build

Install `Tambola-Together-0.17.0-alpha17-wifi.apk` on Android 8 or newer. It uses the same development certificate as alpha16. Update in place and keep app data; do not uninstall or reset to update. This is a debug-signed private-network build, not the Store release.

1. On the PC, run this once in **Administrator PowerShell**:
   `& 'D:\Github\App Ideas\tambola-caller\full-game\tools\enable-wifi-firewall.ps1'`
2. Keep the PC signed in, awake and connected. Connect phones to the same network as `192.168.1.4`, with guest-network isolation off for these devices.
3. Open the app, choose 1–6 tickets and tap Play. New profiles receive 1,500 free coins; tickets cost 100. Sales close after 12 seconds and numbers arrive every five seconds. Empty seats fill with labelled computers. Coins cannot be bought, transferred or redeemed for cash.

Mark manually. Use the arrows to page through readable tickets; choose a prize from the Claim button beside its ticket. The ticket pool determines six to eight prizes. Results show winnings and offer another round.

New in alpha17: game sessions renew on the same installation without replacing the wallet or replaying a purchase charge. Sign-out and deletion records prevent newer recorded revocations from being undone by an older primary-database restore. Recovery after reset, uninstall or loss of this device is still unavailable.

The PC host has been upgraded with fresh retained backups and restricted runtime credentials. Verified HTTPS purchase/refund/session-renewal checks and Android HTTPS/WSS transport passed. The exact APK passed an 84-call native emulator round with all eight selected claims, 2,400 coins paid, a 3,300 final balance, and a second purchase/cancellation with an exact refund. Its frame-time p95 was 50 ms on the debug emulator; optimized smoothness remains work. This package is for Wi-Fi testing, not physical-phone or public release acceptance.

The Windows firewall rule is not yet installed because the current shell is not elevated. Router isolation, physical-phone play, optimized frame times, native refill, recovery after reinstall, Windows reboot, installed-backup restoration and release signing remain open. Changing the PC address requires rebuilding both host configuration and the Wi-Fi APK.

App/service source: `9fe335d47b5b76688c0a148f64e074af4630fbdf`.
APK SHA-256: `575bcd865231a3e5fe96c972c22ac6c657d95e4973beb10a8edcdb73c56dc2e4`.
Evidence: `full-game/reviews/session-wifi-alpha17-2026-09-27/README.md`.
