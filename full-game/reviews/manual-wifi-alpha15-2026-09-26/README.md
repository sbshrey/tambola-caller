# Manual table and private Wi-Fi checkpoint

26 September 2026; branch `shrey/full-tambola-game`. Original code and graphics; reference material was observed, not copied into the app.

Verified: 163 JVM tests (domain 50, client 19, server 85, app 9), Android lint, two targeted native tests, and real installed-host recovery probes. Unit suites contain no skipped tests. These checks do not replace the full revised online UI acceptance suite; older offline navigation tests require scope updates.

The native table fixture shows six disjoint owned tickets across three pages, manual marks retained across pages, a Claim button per ticket and a six-choice picker. The selected Top Line claim awards that ticket only, and its used choice becomes unavailable. Controls were checked against screen bounds and 48dp targets, allowing one physical pixel of layout rounding. Screenshots use fictional data and a component fixture, not a completed multiplayer coin game.

The Android transport test used the actual installed `lan` variant's trust manager and app client against the PC's private HTTPS endpoint, including secure WebSockets. Cleartext was rejected. It did not use adb reverse. Temporary server QA profiles were deleted.

The host probe completed 90 calls with two authenticated clients, selected one ticket/prize, checked own-ticket privacy, then recovered Java, Caddy, the worker and PostgreSQL. Exact command receipt and round/card state survived every recovery. Latest observed recovery times are in `host-recovery.json`; they are individual local fault probes, not an uptime/latency SLA.

`installation.json` records the internal debug-signed Wi-Fi APK hash and installed server library hashes. This checkpoint has no public release, Store upload, release-signing attestation or physical-phone Wi-Fi acceptance. The virtual coin allocation engine is tested; durable wallet/purchase/payout integration and the new purchase/matchmaking UI remain unfinished.

Daily protected backup archives and current-user login startup are installed. Firewall setup needs the administrator command documented in `docs/WIFI_HOST.md`. Boot-before-login, reboot behavior and an installed-backup restore rehearsal remain unverified.

The supplied reference video is not included. No credentials, other projects' data, private TLS keys or database backups belong in this archive.
