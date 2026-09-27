# Call history and number-board accessibility — 27 September 2026

The live manual-claim arena previously showed called numbers using colour alone and offered no complete ordered history. The regression test reproduces the missing accessibility state on the previous implementation. The replacement lets players open Call history directly from recent numbers, or choose Numbers from Game options. Both views use only already revealed calls, update during play, and return to the selected ticket page without marking or claiming anything.

The Numbers tab adds a visible checkmark and a localized called/uncalled state, including the call index and the latest number. Its grid adapts to available width and text size. The history lists calls newest first, with explicit call indices. Stable item keys preserve the older entry being read when another call arrives. A fixed close control remains reachable while either list scrolls. The assisted/offline arena uses the same read-only component.

## Design basis

The Game UI Frontend and Game Playtest skills guided the secondary-view approach, existing Game night colours, readable hierarchy, actual text geometry and screenshot inspection. The playfield retains its current ticket geometry; the recent-number strip gains a chevron and a named accessibility action. No generated artwork or new visual theme was needed.

The earlier hands-on [Tambola Online review](../friends-replay-2026-09-27/README.md) recorded a game-board/called-number reference surface in its [accessibility snapshot](../friends-replay-2026-09-27/rival-table-ax.txt). The publisher's [friends-play guide](https://tambolaonline.com/how-to-play-tambola-online-with-friends) was rechecked during this work. This change improves our own catch-up and accessibility gap; it does not establish that every competitor lacks history or that this app is universally better. Native rival gameplay remains unverified.

Number states use Compose's [accessibility semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics), with grid collection/item information and selected tabs. These checks inspect rendered semantics; they do not constitute a physical TalkBack usability test.

## Native verification

- The previous implementation fails the called-state assertion. [Baseline regression](baseline-regression.txt), [baseline screenshot](baseline-number-board.png). Two initial test setup errors—a one-player online fixture and an ambiguous selector that included the underlying arena—are retained separately.
- **Seven new native tests pass in 54.466 seconds.** They cover explicit called/uncalled/latest states, every number's call index, complete history through call 90, newly arriving calls, preserved older-history position, an empty round, the assisted arena, and return to the same ticket page without invoking mark or claim callbacks. [Transcript](native-tests.txt).
- English landscape and English/Hindi portrait/landscape large-text cases inspect rendered text geometry. The large cases assert the actual dialog font scale is **200%**, including grid numbers. Initial text-width metadata mismatches and a nested dialog language/context failure were corrected and retained in [initial layout failures](initial-layout-failures.txt), [text geometry](initial-text-geometry.txt), and [dialog-context failure](dialog-context-failure.txt).
- The existing three manual-table checks and four claim-feedback checks also pass, **seven tests in 64.857 seconds**, covering ticket marks, per-ticket claims, roster/page preservation and English/Hindi claim text. [Regressions](native-regressions.txt). These are scoped UI fixtures, not real server purchases.

[English history](history-en-landscape.png), [Hindi history at 200%](history-hi-landscape.png), [Hindi number board at 200%](board-hi-portrait.png), [English number board](board-en-landscape.png). Large text requires scrolling; the tests reach the oldest call and number 90 while retaining the close control.

## Web compatibility

The invitation page's download and intent fallback now select the alpha32 release. **71 web tests pass**, and the browser fixture verifies the exact old caller-worker upgrade, offline caller retention, explicit intent target, code validation/copy behavior, keyboard access and English/Hindi at normal/200% text. [Web tests](web-tests.txt), [browser report](browser/validation.json). Publication and browser verification helpers now write to configurable/current output paths rather than overwriting the historical alpha31 review.

## Optimized build

The optimized alpha32 APK and external driver build successfully. All **18 Android JVM tests** pass; lint reports **zero errors and 123 warnings**, including the now-unused older number-board title. [Build transcript](android-build.txt), [test summary](test-summary.json). English/Hindi catalog validation passes for 798 resources.

APK SHA-256: `a6767986b21dd987fc6c9e15b6bd6552be30f58ce6aed5af4fc26eebdb33d7fa`, 29,192,205 bytes. It is version 32 / `0.32.0-alpha32-internet-beta`, package `io.github.sbshrey.tambola.game.beta`, with the existing development signing certificate. Signature verification passes and the optimized package is not debuggable. [Identity](apk-identity.json).

## Public friends-round acceptance

The exact optimized APK passed `InternetBetaTest#friendsHistory`: **OK (1 test), 483.023 seconds**, using the public directory and trusted HTTPS/WSS without adb reverse. The native player and three passive HTTP QA peers completed **88 calls and all eight prizes**. Public winner shares accounted for the complete 2,400-coin pool; the native balance finished at 3,300 and total balances remained 6,000. The test independently checked history against revealed server calls before and after process death, restored 20 marks, and preserved the selected ticket page. [Journey](public-round/coin-release-journey.json), [transcript](public-round/instrumentation.txt), [exact APK identity](public-round/validation.json), [restored history](public-round/call-history-restored.png).

Same friends replay placed the native player and one peer together with three/two tickets, verified the exact peer receipt and process recovery, then refunded both purchases. The native QA profile and all three HTTP QA peers were deleted. The owned emulator was stopped afterwards. This is a full functional round, not remote-human competition, physical mobile-data acceptance or a frame benchmark.

The public endpoint remained healthy at 13:48 UTC. The configured installed server/runtime hashes are unchanged, and no service or bridge restart was performed. [Public entry](public-entry.json), [host identity](host-verification.json).

Production signing, physical-phone and TalkBack usability, native-rival gameplay, frame performance, editable-Figma updates and high-load latency remain open. The broader goal remains active.
