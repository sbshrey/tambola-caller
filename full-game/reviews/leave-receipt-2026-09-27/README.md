# Alpha21: reliable ticket cancellation

Application source: `72b73b7`, branch `shrey/full-tambola-game`. Installed Wi-Fi service source remains `b6d5eb6f7cc9c6c70006e4d9c422c2abe75187e0`; no server or protocol change is needed for this client fix.

Cancelling a newly purchased ticket could refund its coins, then show an unrelated “room no longer available” dialog. The room stream was still running while the leave HTTP request committed. Its membership failure could also clear the saved leave command before the receipt arrived, breaking exact recovery after response loss. The [original optimized alpha20 failure](../server-wifi-2026-09-27/native-first-attempt/coin-release-journey.json) occurred after a complete 85-call round and a correct payout; its transcript, screenshot and UI tree remain archived.

Alpha21 stops the room stream before submitting or retrying a leave, and keeps it stopped while the saved leave awaits confirmation, including lifecycle reconnect. The exact HTTP command confirms the refund and clears the local room. Cancelled or stale stream failures cannot detach a different current room or override an operation that is deliberately ending membership. A genuine external room removal still clears the room and presents the existing error.

## Regression evidence

The same native test APK (`3e3a7d718baff3d4e5d9aa59a5cd4242164c83b72f41ce128932bdf06f70fae2`) ran against the original alpha20 debug APK and the fixed debug build. It used a real isolated PostgreSQL service, the native app and the existing loopback fault proxy. The proxy discarded a committed leave's complete HTTP response; no production wallet or server clock was changed.

- Original APK `17ac4a6f043083ea5ac814c26d48e07ba4c256ec4a2f3a3681a8bcff18838e4f` failed at the explicit assertion that the saved exact leave must remain pending. The server wallet independently showed the refund. [Expected failure](regression-baseline.txt).
- Fixed APK `7c658fa8e07183115b9b051bf1e068e62dac8321f3f0b78c6d05551de9e3e8b4` passed in 11.775 seconds. It retained the command through foreground/background reconnect, retried the same command, kept the wallet at 1,500, completed three ordinary purchase/cancel cycles without a dialog, and still surfaced an external leave as a real room loss. The fixture verified no QA guest remained in its isolated schema. [Passing test](regression-fixed.txt).
- Fifteen fresh Android JVM tests passed. The optimized alpha21 build, resource shrinking/minification and lint completed; lint reports zero errors and 90 warnings, predominantly unused resources. The packaged manifest confirms version 21, debugging disabled and shell profiling enabled. The existing development signing certificate verifies successfully; this is not production signing.

[Regression identities and counts](regression-validation.json), build transcripts, the manifest and signature output are retained. The lifecycle check is background/foreground activation, not a process-death check for a pending leave. The full gameplay fixture separately checks process death with marked tickets.

## Packaged Wi-Fi candidate

`releases/0.21.0-alpha21-wifi-optimized/Tambola-Together-0.21.0-alpha21-wifi-optimized.apk` is 29,126,773 bytes, version `0.21.0-alpha21-wifi-optimized` / code 21. SHA-256: `0ad8d86287ae91902847475a7ac09ec104abf78db6303b5fa5975040cfd08716`. The certificate SHA-256 remains `55546680e8d8f41fb68a37c6f3c494ac7c0da7dec62e19f4cc215b8ba1200d6c`; install over the earlier full-game alpha and retain its data.

The exact packaged APK passed the complete external native journey in **514.485 seconds**: **87 calls, six disjoint tickets, all eight prizes, 2,400 coins paid, 3,300 final wallet and 6,000 aggregate conservation**. Process death restored 20 marks. Results retained the six-ticket selection and 600-coin total. A new three-ticket purchase reduced the wallet to 3,000, and cancellation returned it to 3,300 with no unavailable-room dialog; the three-ticket selection remained. The native profile and all three QA peers were deleted. JUnit reports one passing test. [Final validation](native-validation.json) and [the transcript](native-transcript.txt) retain the result.

Both installed APKs matched their pre-run bytes afterwards, device animation settings were restored and no adb reverse mapping existed. Verified HTTPS health remained ready with the exact tested server runtime. The private firewall rule is still absent, so this emulator round does not establish physical-phone Wi-Fi access.

The API30 software emulator recorded **2,615 frames**, with CPU-frame p50/p95/p99 of **17.024/55.032/144.626 ms**. This is not a smoothness pass or a controlled speed comparison: the round is random and compilation is retained. No new cold-launch measurement was made. [The table](game-table.png) and [results](coin-release-results.png) are captures of the actual alpha21 APK on the emulator, not physical-phone screenshots.

## Remaining boundaries

The game is still a Wi-Fi development build. Same-host purchase burst latency, a sustained session of current manual coin rounds, installed-backup restoration, physical Wi-Fi/firewall, physical ARM64 frame/audio/input measurements, actual Windows reboot behavior, public hosting and protected release signing remain open. The server is healthy with its hidden current-user supervisor, but it cannot serve while the computer is asleep, powered off or disconnected. [Host operation](../../../docs/WIFI_HOST.md) and [the current plan](../../../docs/ONLINE_COIN_GAME_PLAN.md) preserve those limits.

`artifact-hashes.json` records retained review bytes. Large system traces stay local; their identities belong in the final validation record.
