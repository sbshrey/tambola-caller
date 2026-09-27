# Same-group replay and hands-on rival review

## Observed rival flow — 27 September 2026

Used the computer-use plugin in the in-app browser to exercise [Tambola Online's friends mode](https://tambolaonline.com/play). This was a free private test with one fictional guest, not a native Android comparison or a multi-human reliability test.

- Declined optional cookies, entered `Guest QA`, and reached Host Game / Join a Party without signing in. Hosting presented six ticket candidates and a shuffle option before confirmation.
- Created a room, observed its six-character party code, visible host, lock control and Start Game. The host could start with one player. Calling used a manual Call Next Number button; the observed number was 38.
- After that call, all six claim buttons were enabled. A deliberately invalid full-house submission changed to Claim Sent and stayed there when revisiting the room; the winners board still said no prizes claimed. This does **not** prove that an invalid claim was awarded or that server validation failed. A clearer accepted/rejected/pending distinction would help the player.
- The desktop screenshot shows an advertisement above the room and a shopping overlay across the ticket area. Returning to the lobby first showed an advertising interstitial; after closing it, a second Lobby click returned successfully.
- Returned to the lobby after inspection. No messages/invites were sent, no real money was involved, and no personal account was used. The temporary room was not deleted. Its full-round results/rematch flow and native mobile behavior were not tested.

Evidence: [live table](rival-table.png), [observed accessibility state](rival-table-ax.txt), [returned lobby](rival-lobby.png). Screenshots are observations of that session, not claims about every player, screen size or ad configuration. The separate Octro and Party Tambola publisher research remains in the earlier review; native Octro gameplay is still unverified.

## Product response

The existing native arena keeps ads out of the playfield, uses automatic five-second calls, and gives server-confirmed ticket/prize feedback. The remaining group-coordination gap was visible in our own alpha25 results: another round required creating and sharing another table code.

The results screen now prioritizes **Same friends · [cost]**. Players choose their own quantity and explicitly buy into the shared next-round lobby. Quick play and creating/joining another friends table remain accessible. English and Hindi copy explain that each friend confirms their own tickets. The first person to opt in hosts the new lobby; only those who opt in are included. The existing host-start, 2–8 people, no-computer and fifteen-minute refund rules apply.

The next round has a fresh room ID/code, tickets and purchase ledger. Previous members reach it through their results without typing that code. This is not a permanent same-code room or automatic enrollment of the old group. New invitations use the new lobby's code. A late click after the successor starts or expires is rejected without buying into an unrelated round.

## Implementation and verification scope

`MatchRequest.previousFriendRound` is optional and omitted by default, retaining old request hashes and quick-play wire shapes. The request binds replay consent to the previous room code and finished round ID. The server locks that room, checks original membership, creates at most one successor and records the link atomically with the first purchase. A failed debit rolls back both. Exact saved request IDs retain the existing lost-response recovery path. Finished tickets, results and payouts are never reset.

The internal `nextFriendCode` is not sent to older clients. Once used, its stored payload requires the new server binary; rollback must restore the retained paired databases together with the previous runtime. Client upgrades preserve the encrypted profile and request.

The deployed runtime and optimized public acceptance are recorded below. This remains a small-group prerelease, with physical-device and performance gates open.

The first targeted run passed 26 PostgreSQL scenarios (14 coin-match, 7 friends-table, 5 new replay), 37 client tests and 15 Android JVM tests. Ten native lobby tests passed in 42.621 seconds, including replay quantity/cost callbacks and pending-purchase lockout in English and 150% Hindi text. The final copy refinement labels the alternative action Other table and is covered by the final run below.

The broader server run then passed **all 156 tests**, zero failures/errors/skips. [Per-suite counts](server-suites.json). Every test used the isolated `tambola_test` database on loopback 55432.

Final local candidate: 156 server, 37 client and 15 Android JVM tests passed; the optimized alpha26 APK and its external driver built successfully. Lint has 0 errors / 121 warnings. The added plural-candidate warning concerns a coin total that is always a multiple of 100. [Build/APK identity](build-validation.json). All ten native UI tests passed again in **39.061 seconds**, including the final Other table label; [transcript](native-ui-final.txt). The English/Hindi screenshots in this directory are from that final run. The isolated test PostgreSQL process was stopped after the JVM checks.

The Figma plugin read the existing friends frame and its Material 3/Roboto dependencies. The next inspection hit the Starter-plan MCP call limit, before any writes. The existing board is unchanged; the new replay screen is represented by native implementation screenshots, and its editable Figma addition remains pending tool access.

## Deployed service and exact public APK — passed

The guarded idle-host upgrade installed source **`bf03af542183a8d4e39a596dad9d21449a1100b8`**, retaining and verifying both fresh database archives and the previous libraries. [Deployment checksums](deployment.json), [public transport/route checks](public-entry.json). The PC now serves the new runtime; old clients retain their ordinary request/response shape.

`InternetBetaTest#friendsRound` passed: **JUnit OK (1 test), 483.648 seconds**. The non-debuggable alpha26 APK used public HTTPS/WSS through the published directory, with no adb reverse. One native QA player and three passive HTTP QA peers bought 24 tickets into a private table. This is not four independent human operators or a physical-phone test.

The round completed at **call 87**, with **all eight prizes / 2,400 coins settled**. Native balance **1,500 → 900 → 3,300** matched the ledger, public ticket-share verification and aggregate wallet conservation. A forced process stop/reopen restored **20 marks** and the purchased tickets. The six-ticket preference remained selected at results.

The native player then chose three tickets and Same friends; an original peer chose two. Both reached **one new room**, with a **500-coin pool**, two human-type seats and no computers. The peer's exact request replay returned the same receipt. A second native process stop/reopen retained the purchase without another debit. Leaving refunded the native 300 coins; the peer became host and leaving refunded its 200 coins. The old round ID/results remained available. The next three-ticket quick purchase and refund also passed. The test deleted the native profile through the app and all three QA peers through the service.

[Journey report](public-round/coin-release-journey.json), [JUnit transcript](public-round/instrumentation.txt), [results](public-round/coin-release-results.png), [next lobby](public-round/friends-replay-ready.png), [validation](public-round/validation.json).

APK **Tambola-Internet-Beta-0.26.0.apk**, Android 8+, version 26 / `0.26.0-alpha26-internet-beta`, package `io.github.sbshrey.tambola.game.beta`. **29,122,377 bytes**. SHA-256 **`f3cc69e943a4b0e3e68469aba9bc2419960eb1b287f10275e6405b60b6ca1ca1`**. The APK pulled from the emulator matches the packaged file byte for byte; the GitHub draft asset digest matches too. [Installed identity](installed-validation.json). The same development certificate supports updating alpha24/alpha25 without uninstalling.

[Download alpha26](https://github.com/sbshrey/tambola-caller/releases/tag/full-game-alpha26-friends-replay). Keep the PC awake, signed in and online. Direct invitation links, cross-installation identity recovery, physical-phone/mobile-data/audio/frame checks, the outstanding purchase-latency target and Store signing remain open. Visual follow-up: the eight-prize results grid still ellipsizes some long prize names; values remain visible, but the grid should adapt to long labels.
