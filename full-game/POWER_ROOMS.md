# Power rooms — alpha38

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
