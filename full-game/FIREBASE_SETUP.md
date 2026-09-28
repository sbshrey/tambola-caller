# Firebase beta diagnostics

The user reauthenticated successfully on 28 September 2026. Project `tambola-together-beta-sbshrey` and Android app `1:759172862735:android:0062dcf7dd9f49ba23a47a` are registered for `io.github.sbshrey.tambola.game.beta`. The configuration is saved in ignored `app/src/publicBeta/google-services.json`. No billing account, paid database, storage bucket or Cloud Functions were enabled. Dashboard verification is tracked separately from SDK configuration.

## Free account setup

1. This PC's Firebase CLI login is working. Reauthenticate only if it expires; never paste credentials or CLI account JSON into chat.
2. To restore the configuration, run `./tools/configure-firebase.ps1 -ProjectId tambola-together-beta-sbshrey` from `full-game`. The existing project and Android registration are reused.
3. Keep the project on Spark. Crashlytics and Performance Monitoring are no-cost services on the [Firebase pricing page](https://firebase.google.com/pricing).
4. Build `:app:assemblePublicBeta -PtambolaFirebase=true`. The ignored configuration belongs to `io.github.sbshrey.tambola.game.beta`; debug/Wi-Fi builds remain unconfigured. The Crashlytics plugin supplies the build identity and release mapping upload.

## Collection and verification

Collection is off by default. The game-data dialog exposes an optional crash/performance sharing switch in configured builds. The app does not add names, room codes, ticket grids, credentials or URLs to custom telemetry. Traces measure login rewards, joining a table, claiming a prize and other authenticated requests; their only custom attribute is success, failure or cancellation. Google also collects SDK-defined device/app metadata. Analytics is not enabled.

The Performance Gradle instrumentation plugin is deliberately not applied: automatic instrumentation of room URLs is unnecessary for the request-duration traces. See [custom trace documentation](https://firebase.google.com/docs/perf-mon/custom-code-traces?platform=android).

Delivery was checked on 28 September with an emulator-only `0.37.0-alpha37-firebase-probe` build. The Crashlytics reporting API returned exactly one intentional fatal event, issue `b69771b81ba35a7065e0d32f9d86191e`. All four fixed custom traces logged through the application telemetry wrapper and the Firebase logging endpoint returned HTTP 200. Turning sharing off stopped subsequent custom traces. Individual Performance dashboard trace visibility has not been inspected; browser control is unavailable in this session. The crash entry point and verbose performance logging exist only in an ignored probe source set, never the normal APK. See [delivery evidence](reviews/admob-firebase-2026-09-28/README.md).

For future verification, use a separate diagnostics candidate, enable collection, generate an intentional test crash, restart and confirm its exact build. Remove the test entry point before publishing. Follow the [official Crashlytics setup](https://firebase.google.com/docs/crashlytics/android/get-started).

SDK pins were taken from the official Android guides on 27 September 2026: Firebase BoM 34.19.0, Google Services plugin 4.5.0 and Crashlytics plugin 3.0.8. Dependency checksums remain enforced. Open the [beta Firebase console](https://console.firebase.google.com/project/tambola-together-beta-sbshrey/overview) to inspect delivery.
