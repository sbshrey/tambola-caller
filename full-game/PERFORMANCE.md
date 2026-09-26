# Performance acceptance

The current candidate is alpha14. This page separates measured results from remaining performance work; emulator behavior is not a physical-phone or mobile-network benchmark.

## Sustained automatic rooms

Full run `832f4957afc84935` **passed on 26 September 2026** against application/service source `cfd809ed1f290c7f65576afe80fe999adaefa6ab`: ten rooms, 320 persistent clients, six tickets each and nine complete automatic games. Gameplay lasted **4,292.587 seconds (71.54 minutes)**. All **259,200** expected deliveries arrived; p50/p95/p99/maximum were **408/537/574/892 ms**. Every game's results, histories, archives and rematch checks passed. All nine post-full-GC server checkpoints were **24 MiB**; 145 metric observations reported 320 streams, zero handled stream failures and a ready worker. No database failure type was recorded.

The fixture reported successful cleanup and Gradle exited zero. Independent process/SQL checks confirmed both owned processes and the two exact run databases were absent. All 51 runtime JARs and the application APK remain unchanged. [Archived evidence and hashes](reviews/soak-alpha14-2026-09-26/run-832f4957afc84935/validation.json) preserve the raw report, GC log, collection records, launcher status, build transcript and independent cleanup check. [Automatic soak instructions](server/AUTOMATIC_SOAK.md) define the timing and memory limits: these are local service measurements, not hosted TLS, mobile-network or Android-memory acceptance. Earlier interrupted runs remain incomplete.

The current 51-JAR service runtime has canonical identity `04440bb170091d3392a1e34e5be4c1da4f0f498acd34f661d539a2cd2aa3df0a`. The [manifest equivalence record](reviews/soak-alpha14-2026-09-26/runtime-identity.json) explains the different filename order in the original alpha14 packaging record; every JAR is identical.

## Native-client traffic measurement

The existing two-emulator journey can optionally count transport bytes without changing the app or inspecting message contents. It still verifies 90 matching calls/results, private two-ticket hands, rematch and native cancellation. Its transparent proxy binds only loopback, bounds live connections and preserves streaming backpressure and half-close behavior. Three local Node tests pass for binary transfer/counting, connection limits/cleanup and refused upstreams; the full native measurement has **not run yet**.

Install matching application and instrumentation APKs on two dedicated `tambola_full_game_*` emulators. Configure the isolated PostgreSQL/JDK environment described in the [service guide](server/README.md). Use a fresh output label:

```powershell
node --test tools/test-tcp-byte-meter.mjs
node tools/android-native-pair.mjs --host emulator-5582 --guest emulator-5586 --label alpha14-native-network --measure-network
```

The driver accepts `--apk` and `--test-apk` paths when checking archived candidates. It verifies both installed APKs before and after the journey and records the service runtime and fixture hashes. It refuses an existing evidence directory. Original animation settings are restored, owned ADB mappings are removed, and owned proxies/service are stopped on success or failure.

Evidence records cumulative upload/download bytes per device after setup plus the first game, and again after rematch plus native cancellation. Counts include HTTP headers and WebSocket framing, acknowledgements and pings; they exclude TLS, IP and radio overhead. Setup and first-game counts are deliberately combined. Calls in this correctness journey are driven rapidly through the native host UI, so its short connection lifetime cannot establish the bandwidth of a paced seven-minute game. Rejected proxy connections or transport errors fail acceptance. The final observed totals are retained even after a failed journey.

The driver adds a local forwarding hop. Do not infer production latency, data-plan cost, packet-loss behavior or battery consumption from these counts. Repeat paced gameplay on physical phones through the actual HTTPS endpoint before publishing network-use claims.

## Remaining device budgets

### Native long-session fixture — awaiting compilation and execution

`LongSessionTest` and `tools/android-long-session.mjs` add an opt-in session on a dedicated emulator. They have not yet been compiled or run. Run a short fixture probe after the service endurance workload ends, then the default workload only if the probe passes:

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest --no-daemon --console=plain
node tools/android-long-session.mjs --serial emulator-5582 --label alpha14-session-probe --rounds 3 --draws 3
node tools/android-long-session.mjs --serial emulator-5582 --label alpha14-session-full
```

The default is nine full 90-call automatic games, six owned tickets, two labelled computers, five-second real timers, assisted marking, and enabled voice/music/effects/motion. Setup and rematch use the app's ViewModel actions while a real Activity renders; this is not a touch-navigation or cold-start benchmark. It checks fresh complete strips, stable cards, calls/marks, completed history and the app's own keep-screen-on behavior. A three-call probe deliberately cancels rounds; it cannot satisfy `fullSessionAtLeast60Minutes`. That flag requires nine full games and at least 3,600 seconds of actual game phases, excluding between-game memory sampling.

After every game the fixture stops audio and samples memory in the same finished-table state after two GC requests. It records managed heap, native allocated heap, process PSS/private dirty memory and available ART GC counters. GC requests do not prove a particular full collection. A fixed 16 MiB managed-heap span after the first warmup round is a diagnostic regression guard, not proof of no leak; PSS and native memory must be reviewed separately. History remains retained through rematches. The fixture stores only counters and test identity, with no tickets, names or online credentials in its report.

During gameplay a fixed-size histogram records Android Window frame durations, with first-draw frames separate, unavailable durations and dropped reports explicit. The 16.67 ms count is a 60 Hz reference, not a measured display deadline or a physical-device jank certification. Percentiles use one-millisecond upper buckets, with values above two seconds reported as overflow. A zero frame count fails the fixture; missing reports prevent complete frame evidence. Android documents the [duration metrics](https://developer.android.com/reference/kotlin/android/view/FrameMetrics), [reused callback objects and dropped reports](https://developer.android.com/reference/android/view/Window.OnFrameMetricsAvailableListener), and [memory units](https://developer.android.com/reference/android/os/Debug.MemoryInfo).

The driver verifies exact app/test APKs before and after execution, uses a new output directory, records device/display settings, and restores global animation scales. The instrumentation restores original app preferences and closes its Activity/listener/thread. A crash or forced timeout that prevents preference restoration is reported as incomplete cleanup. Ordinary smoke runs exclude this long fixture. No production app code or runtime dependency was added for collection.

The [plan](../docs/FULL_GAME_PLAN.md) sets provisional targets for cold launch, mark feedback, frame time, resync, APK size and a 60-minute session. Alpha14's universal debug APK is 42,497,990 bytes. Physical-device cold-launch p95, draw/mark-to-frame timing, jank, audio routing and battery measurements remain open. The service endurance test does not measure Android retained memory. Reduced-motion and emulator layout tests establish their named behaviors only.
