# Optimized alpha18 Wi-Fi acceptance

Source `14c20f0042c53c7f9c64f593ee7deebc302773ff` adds a non-debuggable, minified, resource-shrunk Wi-Fi variant and an external UI benchmark. Game rules, economy, artwork and the installed alpha17 service are unchanged. Root/dialog resource tags expose existing controls to UiAutomator without linking app implementation classes into the driver. [Validation identities](validation.json) cover the APK, driver, transcripts, reports and four retained local Perfetto traces.

The [optimized APK](../../releases/0.18.0-alpha18-wifi-optimized/Tambola-Together-0.18.0-alpha18-wifi-optimized.apk) is **29,126,773 bytes**, 30.51% smaller than the 41,917,571-byte debug alpha18. SHA-256: `06be6a1ebee61d755d171a8caa3cf441d30229045444a8e08e757f8c9b93ba2a`. It keeps version code 18 and the existing development certificate, with version name `0.18.0-alpha18-wifi-optimized`. Update in place; do not uninstall or reset a wallet. The debug alpha18 remains an unchanged fallback. This is not Store signing.

The exact optimized APK was read back from the dedicated API30 emulator and matched the build. Its scoped public CA matches the host certificate; compiled network configuration permits HTTPS only and trusts that CA only for `192.168.1.4`. Shell profiling is confined to this variant. Normal app and server-only project configuration pass without a LAN CA or benchmark module. Ordinary release manifest processing passes without debuggability or shell profiling.

## Full visible gameplay

The external journey passed in **510.492 seconds** against the running HTTPS host, with real five-second calls. It bought six tickets, read all 90 distinct owned numbers through the visible ticket pages, manually marked them and chose prizes beside the corresponding tickets. Three passive fictional HTTP peers funded the remaining seats; this was not a computer-opponent or human-competition test.

- Force-stopping and reopening the actual app process retained all 20 existing marks and the purchased round.
- All eight prizes were claimed across 86 calls. Settlement paid 2,400 coins, with a final native balance of 3,300 and conserved aggregate balance of 6,000 across four wallets.
- A new three-ticket purchase debited 300 coins, and cancellation returned exactly 300.
- UI deletion confirmed an empty native profile; all three HTTP peer profiles were deleted. App preferences were not reset, and the runner restored its system animation changes.

The [safe journey report](coin-release-journey.json), [results screenshot](coin-release-results.png), [mid-round screenshot](optimized-gameplay-midround.png) and [JUnit transcript](optimized-gameplay-5.txt) retain the checks. No app private file, token, device key or host secret was copied into the evidence.

## Performance limits

The measured journey reported **2,603 frames**, with CPU-frame-duration p50 **16.102 ms**, p90 **41.379 ms**, p95 **53.436 ms**, and p99 **122.340 ms**. This includes paging, claims, the forced restart and results; it is not an isolated steady-state animation sample. It does **not** meet the provisional smoothness target. `FrameTimingMetric` CPU duration is a different measurement from the previous debug `Window.FrameMetrics` total duration; no before/after speedup is claimed.

Three subsequent cold launches passed in 15.409 seconds, with initial-display times **1,007.533 / 808.166 / 773.614 ms** (median 808.166 ms). These are three observations, not a p95 estimate. The [gameplay](gameplay-benchmark.json) and [startup](startup-benchmark.json) reports retain samples; local trace paths and hashes are in the validation record. Timing ended before the final Gradle checks began.

Both tests used `CompilationMode.Ignore`, preserving installed data and existing compilation state. Only the emulator restriction was suppressed; non-debuggability and shell profiling were asserted. The benchmark library disabled method tracing for this ART version. The emulator uses software rendering and external UI automation also consumes resources. Physical ARM64 timing, audio/touch, battery and mobile-network checks remain required. No custom baseline profile was generated.

## Build and driver validation

Strict dependency verification, optimized APK and driver assembly, 14 Android unit tests, release manifest processing and optimized lint all passed. Lint reports zero errors and 90 existing warnings. Dependency review preserved all 1,636 prior SHA-256 pins and checked 122 added pins against authenticated official Google/Maven Central publisher digests; [the provenance record](benchmark-dependency-provenance.json) identifies every artifact. A Python certificate-validation attempt failed; the successful independent check used Node HTTPS defaults without bypassing validation.

Earlier driver attempts failed on Compose's clickable-parent/button-child representation, a ten-second wait for a twelve-second lobby, and expanded touch targets having different row centers. The retained failed transcripts are driver-development evidence, not successful app tests. The row-geometry failure's [cleanup report](failed-row-geometry-cleanup.json) confirms all four QA profiles were deleted. The driver now groups printed number centers and reads descendant accessibility labels. The collector also checks remote file existence and PNG signatures; earlier missing-file diagnostics were not valid screenshots and are excluded here.

See the [driver instructions](../../macrobenchmark/README.md), [performance ledger](../../PERFORMANCE.md) and [Wi-Fi host operating limits](../../../docs/WIFI_HOST.md). The host remains running without a service upgrade. The firewall administrator step, physical-phone reachability, Windows reboot and installed-backup restore, wallet recovery after reinstall, broader current-flow load/endurance, public hosting and production signing remain open. The production goal remains active.
