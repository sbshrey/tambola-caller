# Alpha22 continuous native coin endurance — accepted

27 September 2026. Nine consecutive rounds completed on the same optimized alpha22 installation and wallet, at the installed host's normal five-second pace. Measured round duration was **4,137,515 ms (68.96 minutes)**. The actual runner exited 0 with `OK (1 test)` and confirmed restoration of system animation settings. This closes the native hour/nine-round correctness check, not overall release readiness.

## Verified behavior

- 758 calls and all 65 prize slots across nine rounds; per-round pool and prize counts followed purchased ticket quantities.
- Six owned tickets covered 1–90 exactly once. Manual dabs, ticket paging, selected-ticket prize claims, results and the next purchase/refund passed in every round. Replays retained the confirmed three-ticket preference.
- Every round restored 20 manual marks after leaving/reopening the app. Round 3 deliberately killed the process; PID changed from 7967 to 12338 and the marks survived.
- Ordinary peer refill requests and exact duplicate retries conserved coins. Before profile cleanup, the combined wallet balance was 15,000: 6,000 initial coins plus 9,000 free refill coins. The native QA player held the balance because the three API peers intentionally did not claim prizes.
- The native test profile and all three QA peer profiles were deleted at completion. The analyzer verified nine round reports, 27 memory samples, nine frame/trace iterations, cleanup flags and the complete JUnit transcript.
- Independent collection confirmed unchanged installed app/driver hashes, unchanged installed server source/runtime, a fresh HTTPS-ready response, no adb reverse mappings, and original animation/font settings.

| Round | Calls | Prizes | Pool | Native wallet after settlement | Reconnect |
| --- | ---: | ---: | ---: | ---: | --- |
| 1 | 85 | 8 | 2,400 | 3,300 | Foreground |
| 2 | 87 | 8 | 2,400 | 5,100 | Foreground |
| 3 | 86 | 7 | 1,500 | 6,000 | Process death |
| 4 | 85 | 7 | 2,100 | 7,500 | Foreground |
| 5 | 80 | 7 | 2,100 | 9,000 | Foreground |
| 6 | 86 | 7 | 2,100 | 10,500 | Foreground |
| 7 | 84 | 7 | 2,100 | 12,000 | Foreground |
| 8 | 82 | 7 | 2,100 | 13,500 | Foreground |
| 9 | 83 | 7 | 2,100 | 15,000 | Foreground |

## Candidate and evidence

- App and driver source: `998b8a8ae36c0feffeb73da8104f1beefedcf5e0`.
- App APK SHA-256: `05dfaa405629fec0a5ab57234941eca1229b9f2cb13034a496e3549759bdb4b4`.
- Driver APK SHA-256: `93803ae35cece132857567dd3414b0c34b4250a8c7c290c966a7d622a4dfab91`.
- Installed server source: `b6d5eb6f7cc9c6c70006e4d9c422c2abe75187e0`; runtime SHA-256: `f2b39e5bb622b0bd9c368da654a9412fc3e34d617e171497a1f521b7ec80ee10`.
- [Final analysis](endurance-analysis.json), [independent validation](validation.json), [actual runner terminal result](runner-terminal.json), [native transcript](native-transcript.txt), [journey report](coin-release-journey.json), [benchmark](benchmark.json), [preflight](preflight.json), and [host health after the run](host-health-after.json).
- [Guarded collector](collector.py) refuses unfinished/failed runs or changed identities/settings. Local trace paths, sizes and SHA-256 values are retained in validation; large traces remain in the local test workspace.

## Diagnostic limits and remaining acceptance

This was one API30 software emulator with three passive API peers, not physical-phone performance, competitive balance or capacity acceptance. The separately accepted [computer-opponent round](../computer-round-alpha22-2026-09-27/README.md) covers real computer claims and a shared prize. Controlled HTTP-response loss was not injected into this endurance run; [alpha21 cancellation retry](../leave-receipt-2026-09-27/README.md) is separate evidence.

Macrobenchmark captured 23,003 frames and reported aggregate CPU-frame p95 **58.02 ms** and p99 **126.18 ms**. These do not meet the smoothness target. Browser/Figma design work and a lightweight local preview ran concurrently from round 5 onward, so this is not an isolated performance comparison with earlier candidates. Per-round timing distributions are retained instead of claiming improvement.

Twenty-seven memory snapshots covered both processes. Peak total PSS was 68,836 KB; the replacement process ended at 52,785 KB. Sparse snapshots and Activity object counts cannot prove leak freedom. Physical ARM64 input/audio/frame/reconnect tests, concurrent purchase latency, actual reboot/startup behavior, public hosting, protected production signing and Store acceptance remain open.

The new landscape Figma design and all-screen orientation source draft were not compiled or installed during this run. The result applies to the exact alpha22 APK above.
