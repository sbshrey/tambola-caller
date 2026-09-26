# Alpha14: build migration and device acceptance

26 September 2026. Internal candidate on `shrey/full-tambola-game`; production hosting/signing and broader release gates remain open. Local candidate and sustained-service acceptance are recorded below; Android long-session and public-release work remain. Alpha13 and the earlier build checkpoint remain unchanged.

## Changes

The build now uses checksum-verified Gradle 9.7.1, AGP 9.3.3, Kotlin/Compose compiler 2.4.20 and a verified portable Temurin 17.0.20.1+1. AGP built-in Kotlin, its matching legacy-KAPT bridge and the variant asset API replace the old build integration. Target/compile API 36, minimum API 26, JVM 17, stored data schema and Room runtime 2.8.4 are unchanged. Build-only constraints address observed plugin/lint advisories; the Room metadata reader is aligned with the compiler. See [build-tool review](BUILD_TOOL_REVIEW.md).

This candidate retains the [alpha13 gameplay redesign](ALPHA13_VALIDATION.md): immediate fast solo play, 1–6 owned tickets together, non-overlapping numbers within a hand, private opponent cards, large manual-dab controls and paused family handoff. No speculative number-rendering workaround is included. Direct screenshot pixels and enlarged crops disproved the apparent clipped-digit issue in full-screen previews. The layout test now measures repeated digit ink as well as geometry; this is not OCR or a substitute for real-device review.

## Exact candidate

| Artifact | SHA-256 |
| --- | --- |
| Debug APK, 42,497,990 bytes | `49344972eaf57381bfbd02a445882b71f3104259bed0ea4f638cc21c1a0cc891` |
| Packaged migration instrumentation APK | `dcd8b9dbc7cb87c7add974ab6d6f80625008eaed6000d251c43389cc97381edc` |
| Optimized unsigned release APK | `c433330e970d386b113ee659eedcea75d252dbaf2abcfa11d3894b24f46fc097` |
| Unsigned release AAB | `184555d7a45834b79fccc05fa3c9e9eb9581fba3885d77c61c89130a73822a73` |
| Complete 51-JAR service runtime, ordinal filename order | `04440bb170091d3392a1e34e5be4c1da4f0f498acd34f661d539a2cd2aa3df0a` |

Version code/name: **14 / 0.14.0-alpha14**. The debug APK uses the existing Android Debug certificate, SHA-256 `55546680e8d8f41fb68a37c6f3c494ac7c0da7dec62e19f4cc215b8ba1200d6c`. Its configured online endpoint is loopback `127.0.0.1:8080`; ordinary phone installs support offline/family play without a hosted room service. No OpenAI credential is included or needed. All 270 voice clips, five WAV assets and four manifests match alpha13 byte-for-byte.

Manifest-order correction: the immutable alpha14 package and original migration record use `ff5978407960993fee3fc2d17d76016caac346757fd1ccaa3576b9c94ed8a72b`, produced with PowerShell's case-insensitive filename sorting. The Kotlin/Node runtime verifier uses ordinal order, placing `HikariCP` first, and produces the value in the table. All 51 filenames and individual JAR hashes match exactly; no runtime bytes changed. The [equivalence record](reviews/soak-alpha14-2026-09-26/runtime-identity.json) preserves both identities. Earlier package files remain unchanged.

## Executed acceptance

| Check | Result and boundary |
| --- | --- |
| Full fresh migration build | 162 executed tasks, 7m52s; 130 JVM tests pass: 29 domain / 17 client / 77 service / 7 app; no failed or skipped tests |
| Reproducibility | Two clean Android rebuilds reproduce the same debug APK, optimized release APK and AAB; final rebuild executes 141 tasks in 3m03s, with nine unrelated tasks up-to-date |
| Separate lint | Zero errors, 72 warnings per variant; 67 unused resources and five individual maintenance/API recommendations |
| API30 offline suite | 33 methods pass; eight online-only cases explicitly skipped, 437.802s. Instrumentation `81802f7c...` predates the added pixel check |
| API30 layouts | 48 combinations: 1/3/6 tickets, English/Hindi, dark/light, small phone, 200% text, ordinary phone and landscape. Six-card captures include 2,736 glyph comparisons; lowest reference ratio 98.89%, above the 70% missing-stroke threshold. Display/font/animation settings restored |
| API36, 16KB | 12 layouts pass. Four core gameplay methods, explicit loading of both bundled native libraries and 40 close/reopen/create/delete cycles pass; six methods, zero skips, 118.369s. Actual page size 16,384 bytes |
| APK alignment | Debug and optimized release APKs pass the SDK's 16KB ZIP alignment check |
| Alpha13 in-place update | A real process is killed with a marked Hindi round, then alpha14 and its instrumentation are installed without clearing data. A new process restores the exact saved round, caller choice and language; round digest `40e6fa847929e781e36f3df75c51681591656921dccee2c6410d7904cf9b2851` |
| Native online pair | API30 host / API36 guest finish 90 unique calls with matching scores, awards and draw audit, then rematch with new grids and retained rules. Only each player's two tickets are visible. Native cancellation and two histories agree; owned service/mappings/settings cleaned up |
| Cold online recovery | Draw and deletion each discard three committed replies, terminate the original Android process and verify reconciliation in a new process with the same pending identity and preserved offline progress. Owned services/mappings/settings cleaned up |
| API26 minimum | Four core gameplay methods (33.936s), four full offline methods (77.584s) and 12 layouts (17.660s) pass. Four six-card captures add 684 pixel comparisons. Initial emulator transport registration failed before testing; relaunch on a fresh port succeeded |
| Sustained service | Nine complete automatic games in ten rooms, 320 persistent clients and six tickets each; 71.54 minutes, all 259,200 deliveries, 537 ms p95 / 892 ms maximum local delivery. Every comparable post-full-GC heap is 24 MiB; all results/history/rematch/archive checks pass, zero recorded stream/database failures. Owned process/database cleanup independently confirmed |
| Native transport-byte journey | API30/API26 pair, 90 manual calls and rematch/cancellation, two private tickets each. Setup plus first game: 440,673 combined upload/download bytes for host, 127,412 for guest; zero proxy errors/rejections. Excludes TLS/IP/radio overhead and paced-game/network acceptance |

The APK bytes are the same for all accepted app checks above. Broad offline, in-place upgrade and initial API36 checks use instrumentation `81802f7c...`; final API30/API26 pixel matrices, native pair and cold online recovery use `dcd8b9db...`. Total layout coverage is 72 combinations, with 3,420 glyph comparisons across 20 six-card captures. Instrumentation changed only to correct obsolete navigation selectors and add screenshot-ink assertions. Raw transcripts, inventories, provenance, screenshots, failed probes and exact artifact records are under [the migration evidence directory](reviews/toolchain-migration-2026-09-26/).

## Security and release boundary

Dated completed OSV queries report zero active matches across 469 observed build Maven version pairs and 225 runtime version pairs. All 1,321 prior dependency-verification entries are preserved; 314 new entries were independently checked against fresh official HTTPS bytes. This is coordinate/byte coverage, not proof against unknown vulnerabilities. The official Gradle distribution separately retains three unsuppressed Medium package matches; its JARs were not replaced. The review documents the limited direct-reference assessment.

The sustained run has separate [raw evidence and hashes](reviews/soak-alpha14-2026-09-26/run-832f4957afc84935/validation.json); its service runtime and application bytes match this candidate. Additional instrumentation `7d75970630d0755f1756c7ed03de9fffae1cc33deb42812c685503cd9ec9ef25` adds the Android session diagnostics and was used for the passing short probe and transport-byte journey. The packaged test APK remains unchanged. See [performance acceptance](PERFORMANCE.md) for exact evidence, slow debug-emulator frame measurements and the pending full Android session.

These are local Windows/emulator results. They do not establish production signing, hosted TLS/latency, mobile network switching, physical ARM64/TalkBack/audio quality, Hindi editorial approval, actual Linux/remote CI, or Android long-session performance. Operator/domain/support/privacy/audience decisions and verified public invitation links remain required for public release. No push, CI dispatch, hosted deployment or store publication occurred.
