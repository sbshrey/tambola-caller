# Tambola Together 0.18.0-alpha18 — Wi-Fi test build

Install `Tambola-Together-0.18.0-alpha18-wifi.apk` over the previous full-game alpha on Android 8 or newer. Keep the existing app data so the saved wallet remains available. This uses the same development certificate as alpha17; it is not a Store release.

Alpha18 makes the result screen personal: only your winning prizes appear, and shared prizes show your actual ticket shares. It also removes the idle lobby timer and keeps countdown work within the controls that need it. The existing Wi-Fi service remains compatible and running.

1. On the host PC, run the [private firewall setup](../../../docs/WIFI_HOST.md#one-administrator-action-for-phone-testing) once in Administrator PowerShell.
2. Keep this PC signed in, awake and connected. Put the phone on the same local network as `192.168.1.4`. The app uses private HTTPS on port 8443; it does not require installing a certificate on the phone.
3. Choose 1–6 tickets and tap Play. Mark manually, page using up/down, and use the Claim button beside the ticket to choose a prize. Free coins have no purchase or cash value. Empty seats are filled by labelled computer players.

Results offer another round immediately. The app renews an expired session on the same installation; recovery after uninstalling, resetting or losing the device remains unavailable.

SHA-256: `fb90428ba2eb51b3b7434b7f9534bcc47e7c54b49a99945f571e19925ca1cbf0` (41,917,571 bytes).

See [validation and limitations](../../reviews/coin-ui-2026-09-27/README.md). Physical-phone Wi-Fi, optimized frame performance, actual Windows reboot, installed-backup restoration and production signing remain release gates.
