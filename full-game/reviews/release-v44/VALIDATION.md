# Tambola Jalsa v44 release acceptance

Candidate: version 44, `0.44.0-alpha44-internet-beta`, package `io.github.sbshrey.tambola.game.beta`.

The APK adds the shared game home and online/practice Bingo. It retains the v43 Tambola layout: called numbers above tickets, labelled Tickets / Board tabs, circular eligible-mark prize progress, power progress and confirmed-win confetti.

## Build and native evidence

- Optimized publicBeta build and lint passed with the existing Firebase/ad build configuration. Lint reports zero errors and 186 warnings.
- Candidate SHA-256: `94446d814499278e0f8155aa95b2f86f95d12d6bf8db19371d5da6980d79200c`; size 31,758,550 bytes.
- Signing SHA-256: `55546680e8d8f41fb68a37c6f3c494ac7c0da7dec62e19f4cc215b8ba1200d6c`, matching v43. The preparation tool verified signatures, package continuity and increasing version code.
- Installed v43 baseline preparation passed in 64.838 seconds: authenticated six-ticket purchase/refund, 50,500-coin saved wallet and six-ticket preference. The test owns the disposable profile; it does not clear application data.
- See `../bingo-native/VALIDATION.md` and `../bingo-online-native/VALIDATION.md` for practice, online six-card play, recovery, results, Hindi large text and landscape evidence.

## Pending release gates

The complete regression gate passed: 75 domain, 53 client and 215 server tests, with zero failures, errors or skips. The server suite and refreshed distribution completed in 16m 2s; domain/client tasks reused their current passing results. This includes migrations, actual runtime database roles, receipts, settlement, deletion/restore and variant isolation.

Production migration/health checks, public multi-game checks, GitHub publication and actual v43-to-v44 updater/profile acceptance are pending. This candidate report is not evidence of deployment or publication.

No physical-phone test or sustained concurrency/load test has been performed. Native acceptance uses the owned Android emulator; recovery acceptance uses fresh ViewModels and disposable databases, with test clock advancement for complete-round settlement.
