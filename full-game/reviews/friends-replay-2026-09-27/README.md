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

Validation is in progress. Do not treat this source checkpoint as a published APK or deployed runtime until the acceptance/deployment evidence below is added.

The first targeted run passed 26 PostgreSQL scenarios (14 coin-match, 7 friends-table, 5 new replay), 37 client tests and 15 Android JVM tests. Ten native lobby tests passed in 42.621 seconds, including replay quantity/cost callbacks and pending-purchase lockout in English and 150% Hindi text. A subsequent copy refinement labels the alternative action Other table; the final screenshots/checks must reflect that refinement.

The broader server run then passed **all 156 tests**, zero failures/errors/skips. [Per-suite counts](server-suites.json). Every test used the isolated `tambola_test` database on loopback 55432.

Final local candidate: 156 server, 37 client and 15 Android JVM tests passed; the optimized alpha26 APK and its external driver built successfully. Lint has 0 errors / 121 warnings. The added plural-candidate warning concerns a coin total that is always a multiple of 100. [Build/APK identity](build-validation.json). All ten native UI tests passed again in **39.061 seconds**, including the final Other table label; [transcript](native-ui-final.txt). The English/Hindi screenshots in this directory are from that final run. The isolated test PostgreSQL process was stopped after the JVM checks.

The Figma plugin read the existing friends frame and its Material 3/Roboto dependencies. The next inspection hit the Starter-plan MCP call limit, before any writes. The existing board is unchanged; the new replay screen is represented by native implementation screenshots, and its editable Figma addition remains pending tool access.
