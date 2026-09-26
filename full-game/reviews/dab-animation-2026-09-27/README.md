# Alpha19 dab animation work

Source `2d9684a` moves the animated ticket fill colour from composition into `drawBehind`, retaining the existing clipping, colours, stamp ring, text, semantics and input handling. Alpha19 uses version code 19 and the existing development signature. The installed alpha17 service and protocol are unchanged.

The [optimized Wi-Fi APK](../../releases/0.19.0-alpha19-wifi-optimized/Tambola-Together-0.19.0-alpha19-wifi-optimized.apk) is 29,126,773 bytes. SHA-256: `38c399ec12125482f17f9514e9947f4fe210bd353923786cf1268392c857c20f`. It matched the installed APK read back from the dedicated emulator and retains the scoped public host CA. [Validation identities](validation.json) include both focused-test APKs, the optimized APK, driver, raw reports and the local full-game trace.

## Optimized full-round acceptance

The exact alpha19 APK passed the external UI journey in **505.214 seconds**: six disjoint tickets covering all 90 numbers, 86 real five-second calls, all eight chosen prizes, 2,400 coins paid, a 3,300 final wallet and 6,000 aggregate conservation. A real process force-stop/reopen retained 20 marks and the purchased round. A subsequent three-ticket purchase and cancellation returned exactly 300 coins. UI deletion confirmed an empty native profile, all three passive HTTP QA peers were deleted, and system animation settings were restored. [The safe report](coin-release-journey.json), [live table](alpha19-midround.png), [results](coin-release-results.png) and [JUnit transcript](alpha19-gameplay.txt) preserve that evidence. No app preferences were reset.

Macrobenchmark recorded **2,551 frames** with CPU-duration p50/p90/p95/p99 of **16.033/40.327/53.528/122.100 ms**. Overall p95 remains near the prior 53.436 ms observation and does not meet the smoothness target. The rounds used different random draws and inherited compilation state, so these timings are not a controlled speedup comparison. The proven improvement is the isolated composition-work reduction below. The full trace includes external UI automation, one mid-round screenshot and a forced restart on a software API30 emulator; it does not establish physical-phone, battery or mobile-network performance. No new cold-start timing claim is made for alpha19.

## Reproduced cause and focused verification

The alpha18 optimized trace was verified against its archived SHA-256 before analysis. The existing Android Perfetto processor `v56.0-62048c13b` queried its app-thread slices. The full run contained 595 `DrawFrame` events before process restart and 2,008 afterwards, matching the 2,603-frame benchmark total. The trace contains long rendering/swap spans and repeated recomposition work. Slice durations include overlapping nested work and are not additive CPU utilization. The trace importer reported one empty power-rail packet; no power measurement is claimed. An attempted SQLite export produced an empty file and was not used; the successful analysis used a single SQL query against the native processor.

A separate real-clock `ActivityScenario` fixture then isolated two actual ticket grids from server timers and UI-automation polling. It observed three compositions while applying twelve mark/unmark state changes with Android animations enabled. The pre-change APK failed the new regression guard:

| Observation | Before | After |
| --- | ---: | ---: |
| Composition passes per change | 8–13 | 3 on every sample |
| Total passes across twelve changes | 140 | 36 |

The final alpha19 debug build repeated the twelve samples with three passes each. These counts demonstrate reduced composition work, not a frame-rate or battery claim. Unlike an idle test using a paused Compose test clock, this diagnostic uses a real activity and real elapsed time. It creates no online profile and changes no saved preferences.

Pixel/semantics checks separately verify white unmarked cells, green marked cells, mark/unmark labels, changing animation frames, eventual stillness, reduced motion and Android animation-off behavior. A native table regression also passes paging, retained marks, selected-ticket/prize claims and control bounds. Fourteen Android unit tests pass; optimized lint has zero errors and 90 existing warnings. The initial diagnostic build had a test-only `Instrumentation.arguments` reference error, corrected to `InstrumentationRegistry.getArguments()` before baseline measurement.

The investigation follows Perfetto's [command-line analysis](https://perfetto.dev/docs/getting-started/command-line-analysis). Run the isolated regression with the debug app and matching test APK installed:

```powershell
node tools/android-smoke.mjs --class io.github.sbshrey.tambola.game.CoinMarkTest --mark-guard --animations --label mark-guard
node tools/android-smoke.mjs --class io.github.sbshrey.tambola.game.CoinMarkMotionTest --animations --label mark-motion
node tools/android-smoke.mjs --class io.github.sbshrey.tambola.game.CoinMarkMotionTest --label mark-motion-off
```

Use the [external driver](../../macrobenchmark/README.md) for optimized full-round acceptance. Physical ARM64 performance, Wi-Fi phone reachability, installed-host reboot/restore and production signing remain release gates.
