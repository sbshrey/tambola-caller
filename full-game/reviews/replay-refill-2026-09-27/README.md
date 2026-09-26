# Alpha20 replay and free-refill acceptance

Source: `fe59cdb401386f5cd57dd87ada2f1eaaad6f5833`, branch `shrey/full-tambola-game`.
The corrected external test driver is `72885ae49d128cc5ad01ee404ea000fd0d2df6be`; the application binary did not change.

Previously, returning from a six-ticket game recreated the lobby with three tickets selected. A low wallet could also leave Play disabled while unaffordable quantities remained selectable. The refill countdown used a finished room's old timestamp, potentially extending the apparent wait.

Alpha20 saves the last confirmed ticket quantity with the exact retryable purchase in the encrypted profile. Legacy profiles infer it from their pending purchase or owned coin tickets. The next lobby offers at most the affordable quantity, while smaller manual choices remain possible. A change in balance does not rewrite a pending purchase, and every new purchase requires a Play tap with its total visible.

Refill timing now uses the HTTP `Date` header advanced by a monotonic clock. It is independent of wallet revision and room timestamps. An absent or malformed header falls back to the existing estimate/device time; the server always enforces eligibility. The clock is not persisted across processes, and a fresh response replaces it. HTTP-date precision may show up to about one extra second. The installed Caddy host and the native fixture provide the header; no service/protocol migration was required.

## Executed checks

- 34 client JVM tests and 15 Android JVM tests passed, including preference migration, serialization, exact pending identity, wallet ordering and whole-ticket affordability. Lint: 0 errors, 87 warnings.
- Six native lobby tests passed on the API30 emulator: English/Hindi shared results, lobby countdown, six-ticket preference after remount, a 230-coin balance selecting two tickets, disabled unaffordable options, a broke-wallet refill action, and a server-time countdown that expires across remount.
- The native refill fixture uses the real HTTP service and a fresh PostgreSQL test schema. It buys/cancels six tickets, recreates the activity, spends only its disposable QA wallet through a test-classpath-only route, then drops a committed refill response. The app retains the exact pending refill while an independent wallet read sees 500 coins. Retry retains 500 rather than granting twice, selects five affordable tickets, and preserves the six-ticket preference. A subsequent low balance shows a disabled countdown, and a direct premature refill is rejected by the server. The QA profile and schema are removed, owned fixture processes stop, adb mappings are removed, and animation settings are restored.
- The first refill fixture attempt incorrectly expected activity recreation to construct a new ViewModel and automatically refresh the wallet. It timed out. The corrected test explicitly requests the independent wallet refresh; both the corrected development run and final alpha20 run passed. `refill-fixture-first-attempt.txt` preserves that failure. Activity recreation is not claimed as process-death coverage.
- The first optimized round reached 87 calls, all eight claims and the correct 2,400-coin payout, then stopped at the new replay selector assertion. The archived accessibility tree shows `buy-tickets-6` with `checked="true"` and `selected="false"`; its screenshot shows six highlighted and a 600-coin total. Compose maps this non-tab choice to Android's checked state. The driver was corrected to verify all six checked states. The app/APK was unchanged, all first-run QA profiles were deleted, and the transcript/tree/image are retained under `first-round-*`. That attempt did not reach replay/refund and is not a passing full journey.
- The exact optimized APK passed the complete online round: 86 calls, six disjoint tickets, all eight prizes, 2,400 coins paid, 3,300 final wallet and 6,000 conserved aggregate coins. A forced process stop/reopen restored 20 marks. Results retained six selected tickets and a visible 600-coin total. A subsequent three-ticket purchase/cancellation refunded 300 coins and retained three selected tickets. All four QA profiles were deleted. JUnit reported one passing test; see the raw transcript and `validation.json`.

The four local UI images are debug fixture captures of the new screens. They are not photographs of a physical phone. The optimized round's results image is recorded separately.

## Reproduction

Use the repository's pinned JDK and configured Android SDK. The refill fixture requires explicit `TAMBOLA_TEST_DATABASE_URL`, `TAMBOLA_TEST_DATABASE_USER` and `TAMBOLA_TEST_DATABASE_PASSWORD` for loopback `tambola_test`; it refuses the installed host database. It targets only the dedicated `emulator-5582` Tambola AVD and requires an empty app profile, preserving any existing wallet by refusing to proceed.

```powershell
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew.bat :server:coinRefillAndroid '-PserverOnly=true'
node tools/android-smoke.mjs --class io.github.sbshrey.tambola.game.CoinLobbyTest --label alpha20-lobby-native --animations
```

The full-round check installs the minified, non-debuggable LAN release plus the separate macrobenchmark driver and runs `CoinReleaseBenchmark#realCoinRound`. Its player actions use the visible native UI; three passive HTTP QA peers fund the 24-ticket pool. No live wallet, draw order or server clock is altered. Compilation is retained to avoid older-Android benchmark data wipes.

## Boundaries

Coins have no purchase, transfer or redemption. Free refill remains 500 coins below a one-ticket balance, at most once per five minutes. The local fixture accelerates reaching a broke wallet; it does not establish how often real players will need a refill. Its short countdown UI check and existing server ledger tests cover expiry separately; the native run does not wait five real minutes for a second grant.

The PC service remains the existing alpha17 deployment. Wi-Fi phone reachability, router/DHCP behavior, physical ARM64 performance, installed-backup restoration, actual Windows reboot behavior, current coin-flow concurrency/endurance, production signing and Store release remain open. The private firewall administrator step is still required on this PC. An emulator round is not a smoothness or public-release claim.

The APK is 29,126,773 bytes, version code 20, SHA-256 `97601ca8d68c32260bf39b4e5909c7348dc9070b89de4241ffad06bb2fd1357f`, with the existing development certificate. Installed emulator bytes matched the packaged file. It is minified, resource-shrunk, non-debuggable and shell-profileable. The unchanged API30 software emulator recorded CPU-frame p50/p90/p95/p99 of 16.149/43.799/55.763/109.076 ms. No overall smoothness improvement is claimed: rounds use different random draws and retained compilation state. No alpha20 cold-start measurement was performed.
