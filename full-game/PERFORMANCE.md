# Performance acceptance

The current candidate is alpha14. This page separates measured results from remaining performance work; emulator behavior is not a physical-phone or mobile-network benchmark.

## Sustained automatic rooms

Full run `832f4957afc84935` **passed on 26 September 2026** against application/service source `cfd809ed1f290c7f65576afe80fe999adaefa6ab`: ten rooms, 320 persistent clients, six tickets each and nine complete automatic games. Gameplay lasted **4,292.587 seconds (71.54 minutes)**. All **259,200** expected deliveries arrived; p50/p95/p99/maximum were **408/537/574/892 ms**. Every game's results, histories, archives and rematch checks passed. All nine post-full-GC server checkpoints were **24 MiB**; 145 metric observations reported 320 streams, zero handled stream failures and a ready worker. No database failure type was recorded.

The fixture reported successful cleanup and Gradle exited zero. Independent process/SQL checks confirmed both owned processes and the two exact run databases were absent. All 51 runtime JARs and the application APK remain unchanged. [Archived evidence and hashes](reviews/soak-alpha14-2026-09-26/run-832f4957afc84935/validation.json) preserve the raw report, GC log, collection records, launcher status, build transcript and independent cleanup check. [Automatic soak instructions](server/AUTOMATIC_SOAK.md) define the timing and memory limits: these are local service measurements, not hosted TLS, mobile-network or Android-memory acceptance. Earlier interrupted runs remain incomplete.

The current 51-JAR service runtime has canonical identity `04440bb170091d3392a1e34e5be4c1da4f0f498acd34f661d539a2cd2aa3df0a`. The [manifest equivalence record](reviews/soak-alpha14-2026-09-26/runtime-identity.json) explains the different filename order in the original alpha14 packaging record; every JAR is identical.

## Native-client traffic measurement

The two-emulator journey counts transport bytes without changing the app or inspecting message contents. Run `3e77b813-a3b1-4b7d-a824-01eb5a666ec2` **passed on API30 host/API26 guest** with the unchanged alpha14 APK and service runtime: 90 matching calls/results, private two-ticket hands, rematch and native cancellation. Both installed app/test APK identities were checked before and after. Original device settings/mappings were restored; the owned service and proxies stopped. Independent ADB/listener inspection confirmed no reverse mappings or service listener remained. [Raw evidence and hashes](reviews/android-performance-alpha14-2026-09-26/network-validation.json) include both native transcripts and six screenshots.

| Cumulative checkpoint | Host upload bytes | Host download bytes | Guest upload bytes | Guest download bytes |
| --- | ---: | ---: | ---: | ---: |
| Setup plus first complete game | 38,600 | 402,073 | 2,302 | 125,110 |
| Through rematch and native cancellation | 40,271 | 414,732 | 2,811 | 132,247 |

Both devices used two connections, with zero rejected connections or transport errors. The first checkpoint was approximately 39.6 seconds after proxy startup; this is a fast manual-draw journey with two tickets per player. It does not measure a paced automatic game, a maximum-size room, mobile-network latency or data-plan overhead. The transparent proxy binds only loopback, bounds live connections and preserves streaming backpressure and half-close behavior. Three local Node tests cover binary transfer/counting, connection limits/cleanup and refused upstreams.

Install matching application and instrumentation APKs on two dedicated `tambola_full_game_*` emulators. Configure the isolated PostgreSQL/JDK environment described in the [service guide](server/README.md). Use a fresh output label:

```powershell
node --test tools/test-tcp-byte-meter.mjs
node tools/android-native-pair.mjs --host emulator-5582 --guest emulator-5586 --label alpha14-native-network --measure-network
```

The driver accepts `--apk` and `--test-apk` paths when checking archived candidates. It verifies both installed APKs before and after the journey and records the service runtime and fixture hashes. It refuses an existing evidence directory. Original animation settings are restored, owned ADB mappings are removed, and owned proxies/service are stopped on success or failure.

Evidence records cumulative upload/download bytes per device after setup plus the first game, and again after rematch plus native cancellation. Counts include HTTP headers and WebSocket framing, acknowledgements and pings; they exclude TLS, IP and radio overhead. Setup and first-game counts are deliberately combined. Calls in this correctness journey are driven rapidly through the native host UI, so its short connection lifetime cannot establish the bandwidth of a paced seven-minute game. Rejected proxy connections or transport errors fail acceptance. The final observed totals are retained even after a failed journey.

The driver adds a local forwarding hop. Do not infer production latency, data-plan cost, packet-loss behavior or battery consumption from these counts. Repeat paced gameplay on physical phones through the actual HTTPS endpoint before publishing network-use claims.

## Remaining device budgets

### Native long-session fixture — short probe passed; full run pending

`LongSessionTest` and `tools/android-long-session.mjs` add an opt-in session on a dedicated emulator. Instrumentation compilation passed in 31 seconds (five executed tasks), and the corrected short probe passed on API30 in **69.444 seconds**, with three deliberately shortened three-call rounds, rematches, history, frame collection and preference/device-setting restoration. The application APK remains `49344972…`; the diagnostic instrumentation is `7d75970630d0755f1756c7ed03de9fffae1cc33deb42812c685503cd9ec9ef25`. The full nine-game run is still pending.

The first driver attempt read the report before it existed; `adb exec-out` returned the missing-file diagnostic as text, which could not be parsed as JSON. The driver now uses `adb shell` with its remote exit status. That failed attempt remains archived, including its unconfirmed app-preference cleanup. The successful repetition confirms cleanup. [Probe evidence and hashes](reviews/android-performance-alpha14-2026-09-26/probe-validation.json) preserve both outcomes and the compilation transcript.

The short probe observed 116 non-first-draw frames, no dropped/unavailable reports, a **91 ms p95 upper bucket** and **138.43 ms maximum**; all exceeded the 16.67 ms reference. These debug/software-rendered emulator timings do **not** meet that reference and do not establish physical-phone performance. Managed heap after warmup spanned 3,488 bytes, PSS spanned 1,304 KiB, and native allocations rose across the short warmup. None of these short measurements substitutes for the sustained session or the physical release-device gate.

Commands for a new dedicated fixture run (use fresh output labels):

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
