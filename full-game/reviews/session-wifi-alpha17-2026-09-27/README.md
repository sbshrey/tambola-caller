# Alpha17 Wi-Fi candidate

This candidate brings same-installation wallet continuity and durable logout recovery to the private Wi-Fi game. Primary schema 006 and journal schema 003 are required; room protocol 4 remains unchanged. The service changes have passed [device-session](../device-sessions-2026-09-27/README.md) and [revocation](../session-revocations-2026-09-27/README.md) acceptance before this packaging step.

The Android version is 17 / `0.17.0-alpha17-wifi`. It uses the existing development signing identity and the host's public CA for `https://192.168.1.4:8443`; no private key is packaged. The short host transaction test now checks enrollment, exact renewal replay, refusal of old access, and preservation of wallet and purchase receipts. Native full-round acceptance also requires automatic device enrollment.

Status at candidate preparation: host upgrade, verified TLS transactions and native LAN acceptance are pending. The installed alpha16 server/APK remain the tested fallback reference; older server binaries must not be used after the new migrations. Final deployment and artifact evidence will be recorded here after those checks complete.

This remains a debug-signed Wi-Fi candidate. Physical phones, firewall reachability, lost-device/reinstall recovery, optimized frame performance, native free-refill acceptance, installed-host restoration, actual Windows reboot, public hosting and Store signing remain separate release gates. See [the active plan](../../../docs/ONLINE_COIN_GAME_PLAN.md).
