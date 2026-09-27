# Firebase beta diagnostics

Account setup is pending. The saved Firebase CLI session returned HTTP 401, and the user chose to sign in later. No Firebase project has been created and no real crash or performance trace has been confirmed in the console.

## Free account setup

1. Run `firebase login --reauth` on this PC. Do not paste credentials or CLI account JSON into chat.
2. Choose an available project ID and run `./tools/configure-firebase.ps1 -ProjectId <project-id> -CreateProject` from `full-game`. Re-running with the same project reuses its Android registration. The helper creates no billing account, Cloud Functions, database, or storage bucket.
3. Keep the project on Spark. Crashlytics and Performance Monitoring are no-cost services on the [Firebase pricing page](https://firebase.google.com/pricing).
4. Build `:app:assemblePublicBeta -PtambolaFirebase=true`. The ignored configuration belongs to `io.github.sbshrey.tambola.game.beta`; debug/Wi-Fi builds remain unconfigured. The Crashlytics plugin supplies the build identity and release mapping upload.

## Collection and verification

Collection is off by default. The game-data dialog exposes an optional crash/performance sharing switch in configured builds. The app does not add names, room codes, ticket grids, credentials or URLs to custom telemetry. Traces measure login rewards, joining a table, claiming a prize and other authenticated requests; their only custom attribute is success, failure or cancellation. Google also collects SDK-defined device/app metadata. Analytics is not enabled.

The Performance Gradle instrumentation plugin is deliberately not applied: automatic instrumentation of room URLs is unnecessary for the request-duration traces. See [custom trace documentation](https://firebase.google.com/docs/perf-mon/custom-code-traces?platform=android).

After account setup, use an isolated diagnostics candidate, enable collection, generate an intentional test crash, restart and confirm its exact build in the Crashlytics console. Remove the test crash entry point before publishing. Play and claim in a test room and confirm all trace names in the Performance console. Switch reporting off and verify new custom traces stop. SDK integration alone does not establish telemetry delivery. Follow the [official Crashlytics setup](https://firebase.google.com/docs/crashlytics/android/get-started) for console verification.

SDK pins were taken from the official Android guides on 27 September 2026: Firebase BoM 34.19.0, Google Services plugin 4.5.0 and Crashlytics plugin 3.0.8. Dependency checksums remain enforced. Dashboard verification and the configured release build are still outstanding until reauthentication.
