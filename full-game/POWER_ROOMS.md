# Power rooms

## Upcoming power and one-tap activation (included in v41)

New clients opt into `previewPowers`. At round start the server independently
chooses and persists each human player's next power. The fifth new correct
manual mark awards exactly that preview, then selects and persists the next
preview in the same transaction. Duplicate marks, reconnects and restarts do
not reroll it. A full two-power inventory skips that milestone and retains its
preview. The draw order and prize pool do not change.

The landscape header shows the charging icon and mark progress, then a glowing
one-tap action. It uses the first queued power on an eligible visible ticket,
preferring the last marked visible ticket. If no visible ticket is eligible,
it asks the player to change ticket pages instead of targeting a hidden ticket.
Reduced-motion mode uses a steady highlight.

Shield now requires activation before a claim and protects only its selected
ticket, once. A ready but unactivated Shield does not protect a false claim.
Existing rooms retain their passive Shield behavior. Optional fields are
omitted from legacy wire payloads, and matchmaking segregates preview and
legacy rooms. Friends joining an incompatible version are rejected before a
purchase; replay preserves the previous table's power capability.

Validated 2026-09-29: 61 domain tests, 44 client tests, 18 focused PostgreSQL
integration tests, 33 app unit tests, and 3 native UI tests passed with no
failures or skips. Native checks include preview at zero/four marks, fifth-mark
unlock, one-tap Shield activation, the following preview, and English/Hindi
six-ticket layout. English/Hindi parity passed for 894 resources. Debug app and
instrumentation APKs built successfully. These were source validation checks. The compatible protocol 9 server and signed v41 APK have since been released; see RELEASE_41.md for hosted and release acceptance evidence.

## Historical alpha38 behavior

The Android lobby offers Classic and Power rooms before buying tickets. The server keeps their matchmaking separate. Friends must select the same mode. Existing rules-v1/v2 Classic clients, saved rounds and receipts remain readable; Power rooms use protocol 6 with optional fields omitted from Classic responses.

Power rooms award one free random power after every five new correct manual marks across the player's hand. Shield, Auto-Dab and Prize bonus each have probability 1/3. At most two powers may be held. A milestone reached with a full inventory skips the drop. Powers expire with the round. There are no paid random drops or cash rewards.

* **Shield:** automatically saves one false claim. Each ticket can be shielded once in a round. Another false claim discards only that ticket. Earlier verified wins remain valid.
* **Auto-Dab:** marks already called and newly called numbers on one ticket for 15 seconds, measured by server time. One activation per ticket. Automatic marks and repeated taps never earn manual-mark progress.
* **Prize bonus:** arm one ticket before claiming. Its next successful prize receives 25% extra virtual coins on its final share, rounded down and credited once when the round completes. It never reduces the shared prize pool. One activation per ticket.

Power-room correct marks are permanent. The server owns marks, inventory, timer deadlines, penalties and promotional payouts. `mark`, `use_power` and `claim` commands are authenticated and stored with their immutable retry receipts in the same database transaction. Each player receives only their own power state and ticket numbers. The future draw order stays private until completion.

A claim targeting an old call, a different round, an already won category or a closed category is rejected without a ticket penalty. A new current-call claim with an incomplete pattern consumes a usable Shield or discards its selected ticket. Wrong number taps are rejected immediately in all modes. Classic and offline rounds retain warnings for unsuccessful claims.

Quick Power rooms add actual simulated seats at three-second intervals during the twelve-second lobby. Their stable display names are `Player` plus six digits derived from the randomly generated room identity. Avatars and the small **Practice** label identify them; lobby help explains that practice personas are simulated. Real users can fill the seats, and friends tables contain invited users only. No arriving-person animation inflates the server's roster count or creates a fictitious connected user.

The recent-number arrow opens the entire 1–90 board fitted to the available screen, with no board scrolling. The separate History tab retains scalable text and scrolling. Every grid cell exposes its number, call position and latest-call state to accessibility services. The number grid alone fits its font to the available cells.

Verified live prize claims show winner/category feedback and the win sound. An available offline Android speech voice reads the announcement, respecting the voice setting, audio focus and app foreground state. Number calls take priority. A missing offline speech voice leaves visual and sound feedback available; names are never sent to a network speech engine. Reconnection snapshots and repeated receipts do not replay old wins.

Firebase remains opt-in. Live AdMob ads stay disabled in the shareable gameplay build until consent, app readiness and Google's signed callback have been verified. This update does not activate purchases or real-money prizes.
