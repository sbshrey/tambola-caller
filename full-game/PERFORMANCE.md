# Performance acceptance

The current candidate is alpha14. This page separates measured results from remaining performance work; emulator behavior is not a physical-phone or mobile-network benchmark.

## Sustained automatic rooms

The current full run is `832f4957afc84935`, started on 26 September 2026 against application/service source `cfd809ed1f290c7f65576afe80fe999adaefa6ab`. It is **in progress**, not accepted. [Automatic soak instructions](server/AUTOMATIC_SOAK.md) define the ten-room, 320-player, nine-game workload, delivery and retained-memory guards, and cleanup requirements. Do not use the earlier interrupted five-game run as a substitute.

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

The [plan](../docs/FULL_GAME_PLAN.md) sets provisional targets for cold launch, mark feedback, frame time, resync, APK size and a 60-minute session. Alpha14's universal debug APK is 42,497,990 bytes. Physical-device cold-launch p95, draw/mark-to-frame timing, jank, audio routing and battery measurements remain open. The service endurance test does not measure Android retained memory. Reduced-motion and emulator layout tests establish their named behaviors only.
