# Phone connection correction

The previously linked `app-debug.apk` was a fixture build configured for `http://127.0.0.1:8080`. On a physical phone that address refers to the phone, so it cannot reach the PC-hosted game server. Sharing that artifact for phone use was incorrect.

The corrected artifact uses the existing `publicBeta` variant. It resolves the current public HTTPS server through the publisher directory, retains the stable beta profile identity, and includes the current no-scroll lobby changes. The beta package is `io.github.sbshrey.tambola.game.beta`; it appears separately from the fixture/debug package and updates an existing beta installation using the existing signing configuration.

Current live validation: public discovery is unexpired; readiness returns HTTP 200/protocol 6; temporary guest creation and an authenticated wallet read both pass. The temporary QA profile was deleted successfully. These checks used the public HTTPS route from the PC; they do not prove connectivity on the user's phone/network.

Build command: `./gradlew.bat :app:assemblePublicBeta --console=plain`. Default ad and telemetry opt-ins are disabled for this local candidate. No live server or public download was changed.

Build and release lint passed. The resulting APK installed and cold-launched successfully on the dedicated API30 emulator without adb reverse. No complete native online round was run in this correction.

Phone artifact: `full-game/releases/lobby-phone-20260928/Tambola-Internet-Beta-Lobby.apk`.

SHA-256: `335f1b442a7239b18144fe57db272da7c1a14e034f8c8b91d0e6237bb686cb3c`.
