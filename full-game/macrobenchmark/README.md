# Optimized Wi-Fi app checks

This opt-in external driver measures the non-debuggable `lanRelease` APK. It uses public UI controls for the measured player and never links app implementation classes, calls ViewModels, or reads the app's private files. The driver APK is separate and debuggable so its own safe diagnostic reports can be collected.

Use a dedicated `tambola_full_game_*` emulator with an empty online profile. The full journey refuses an existing wallet. Do not uninstall the game or clear its data to prepare a user's device. The driver creates three fictional passive peers through the public HTTPS API, buys six tickets through the app, marks the revealed numbers, selects each prize, force-stops and reopens the app, checks settlement and a new purchase/refund, then deletes its four profiles. It validates real five-second calls; allow approximately eight minutes. Computer-player behavior and genuine human competition are outside this fixture.

Build with Java 17, the configured Android SDK, and the host's **public** root certificate:

```powershell
.\gradlew.bat :app:assembleLanRelease :macrobenchmark:assembleLanRelease `
  '-PtambolaBenchmarks=true' '-PtambolaApiUrl=https://192.168.1.4:8443' `
  "-PtambolaLanCa=$env:LOCALAPPDATA\TambolaTogetherHost\tls-data\caddy\pki\authorities\local\root.crt"
```

Install `app/build/outputs/apk/lanRelease/app-lanRelease.apk` and `macrobenchmark/build/outputs/apk/lanRelease/macrobenchmark-lanRelease.apk` with `adb install -r`. The app uses the existing development certificate for update compatibility; it is not Store signed. R8 minification and resource shrinking are enabled. Only this variant allows shell profiling. Its generated CA trust is restricted to the configured private IP; ordinary release/debug variants do not receive it. The external driver's Perfetto processor additionally needs cleartext HTTP on localhost, confined to the driver's own network configuration.

```powershell
node tools/android-smoke.mjs --benchmark --animations --label optimized-gameplay `
  --class 'io.github.sbshrey.tambola.benchmark.CoinReleaseBenchmark#realCoinRound'
node tools/android-smoke.mjs --benchmark --animations --label optimized-startup `
  --class 'io.github.sbshrey.tambola.benchmark.CoinReleaseBenchmark#coldCoinEntry'
```

Use a fresh label. The runner restores system animation settings and copies benchmark outputs before another invocation can clear them. Full-journey reports and screenshots are read only from the driver package. Check `completed`, `nativeProfileDeleted`, and `deletedQaPeers` as well as JUnit status. A failed fixture is not app acceptance; inspect its captured screen and report. Never silently discard failed cleanup.

The prize picker closes when a new call arrives. The driver handles a missing, disabled or stale captured choice only after a fresh public snapshot proves that the call or active phase changed. It does not blindly repeat a click; it still checks any accepted award before continuing. Missing/disabled controls within the same active call remain failures. A focused regression holds the actual picker object across the next real call, reproduces the original stale enabled-state read, checks the recovery path and reopens/dismisses a fresh picker:

```powershell
node tools/android-smoke.mjs --benchmark --animations --label picker-call-boundary `
  --class 'io.github.sbshrey.tambola.benchmark.CoinReleaseBenchmark#claimPickerCallBoundary'
```

This short test deletes its four profiles and records `pickerCallBoundaryVerified`; it does not complete a round or replace endurance acceptance. Retain its report under its own label.

## Sustained coin play

The explicit endurance check uses nine rounds and requires at least one hour of elapsed native gameplay. It keeps the same main identity, wallet and three passive QA peers throughout. Each round purchases six native tickets, manually marks numbers, claims the available pool prizes, verifies aggregate coin conservation, then checks a new three-ticket purchase/refund and retained selection. Peers buy only affordable tickets and use the normal free-refill API when eligible; no wallet balance or clock is modified out of band.

```powershell
node tools/android-smoke.mjs --benchmark --animations --endurance --label optimized-endurance `
  --class 'io.github.sbshrey.tambola.benchmark.CoinReleaseBenchmark#realCoinEndurance'
```

Allow approximately 75–90 minutes; the host runner has a 95-minute watchdog. The driver records per-round frames through Macrobenchmark and safe PSS/heap/activity measurements at entry, reconnect and completion. Rounds normally reconnect through background/foreground; the third deliberately kills/reopens the process and checks retained marks. Memory samples include process IDs so a restart cannot be mistaken for improved retention. `coin-release-journey.json` records incremental progress and only marks the endurance run complete after nine rounds and the elapsed-hour assertion. Profile cleanup still runs once at the end.

This checks sustained use by one UI-driven player with three passive peers, not competitive human behavior or network capacity. The live five-second caller remains unchanged. It does not inject a lost HTTP response; the separate `CoinLeaveTest` covers that fault against the isolated service. Per-round pool/prize counts vary as peer balances drain and refill. Do not call a run accepted until its final JUnit report, hour assertion, wallet checks and all four profile deletions pass.

After collection, `tools/analyze-coin-endurance.py` reconstructs the nine-round economy from starter balances, ticket quantities and eligible free refills. It checks the wallet chain, pools, prize counts, replay/refund flags, retained marks, observed third-round process change, full duration and profile cleanup. Supply the journey JSON, the collected `*benchmarkData.json`, the runner transcript and a new output filename:

```powershell
python tools/analyze-coin-endurance.py <journey.json> --benchmark <benchmarkData.json> `
  --transcript <runner.txt> --output <new-analysis.json>
```

Final acceptance additionally requires nine frame-sample runs and passing JUnit output. Per-round CPU-frame percentiles use linear interpolation at `(n-1)*p`; aggregate Macrobenchmark values are preserved separately. Memory is grouped by process ID, with no leak-free claim based on snapshots or Activity object counts. `--partial` is diagnostic only and always exits **2**; final rejection exits **1** and accepted endurance evidence exits **0**. This is not production-readiness or smoothness acceptance. Verify packaged/installed APK hashes, host runtime/health and restored device settings separately before archiving the complete result.

The current peer origin is `https://192.168.1.4:8443`, matching the Wi-Fi host. Change it alongside the candidate endpoint if the host address changes. `CompilationMode.Ignore` preserves installed data and the existing compilation state; it does not establish a controlled AOT or baseline-profile comparison. The runner suppresses only Macrobenchmark's emulator restriction. The test asserts that the target app is non-debuggable and shell-profileable. App animation duration is enabled; window and transition scales are zero during the check, then restored.

Emulator timing is diagnostic. Measure the same optimized app on physical ARM64 phones before making smoothness, battery, audio, or startup-percentile claims. Three cold launches are not a p95 sample. No custom baseline profile is generated by these tests. Keep system traces local; record their hashes and compact reports in review evidence.

Implementation follows Android's [Macrobenchmark setup](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview), [external Compose UI control](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-control-app), and [measurement environment guidance](https://developer.android.com/topic/performance/appstartup/setup-env).
