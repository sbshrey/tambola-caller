# Online coin Tambola — current scope

26 September 2026. This supersedes the earlier offline-first release plan and single-button/all-tickets interaction. The user confirmed Wi-Fi testing first on this Windows computer. No public endpoint is required for that milestone.

## Player experience

- Online multiplayer is the only primary play flow. Short name/avatar entry, then ticket choice and Play. Retain legacy save compatibility internally; do not advertise practice, solo, family, badges or tutorials in the main flow.
- Manual marking, five-second automatic calls, short caller audio, visible ball entry and dab stamps. No instruction paragraphs during play.
- Up to six disjoint tickets. Show two readable tickets when height permits, otherwise one. Explicit up/down controls and a page counter. Preserve marks and page across calls and rotation. Never switch pages unexpectedly when a number arrives.
- Claim belongs beside each ticket and opens a compact prize picker. The command identifies exactly that ticket, chosen prize, round, call and marks. A choice cannot silently claim another ticket or scheme. Closed/already-owned prizes are unavailable; invalid claims get a brief message, not a ticket-destroying penalty.
- Recent calls and round counters across the top; prizes and players on the left. A new call closes the picker to avoid accidentally submitting an old choice against a new call.
- Computers fill empty seats with delayed reactions using the same revealed numbers and rules. Identify them as computers; do not fabricate human participation. Human arrivals join the next lobby, not a sold/started round.

## Virtual economy defaults

These are tuning defaults. Coins are free game currency without purchase, transfer or redemption.

1. Grant 1,500 starter coins once per server wallet. Tickets cost 100 coins, independently chosen per player from 1–6. Below one ticket's balance, allow 500 free coins at most once per five minutes; no purchase gate.
2. Reserve/debit purchases atomically with lobby allocations. Quantity changes adjust only the difference. Leaving, removal or expired unstarted lobbies release reservations once. Display total before purchase. Retries never charge twice.
3. Freeze the pool and schedule at sales closure. Include explicitly identified computer seats in the virtual pool. Publish total tickets, pool, prize values and prize count before the first call. Never change the schedule after observing draws.
4. Allocate 10% each to Early 5, Corners, Top, Middle and Bottom; 50% to houses. Under 12 tickets: one house (50%). 12–23 tickets: two houses (35%, 15%). At least 24: three houses (30%, 15%, 5%). This yields 6, 7 or 8 prize slots. Integer allocation sums to the exact pool.
5. Same-call claims share a slot. Finalize credit when the call window closes, including the final house/90th call. Share by winning ticket with a deterministic remainder rule independent of arrival. Show verified claims immediately; unsettled wins are not spendable balance.
6. Transactional wallet/reservation/payout/refund ledger with unique operation identities. At cancellation/completion, distribute the remaining unawarded pool proportionally to purchased tickets. Never refund already-paid winnings a second time. Avoid casual host cancellation in shared economy rounds.
7. Rematches are new purchases and commitments. Reconnect restores the existing purchase, cards and receipt without a new debit. Securely retain guest identity and explain recovery limits before destructive reset.

## Implementation and acceptance order

1. **Reference interaction foundation (implemented, targeted checks passed):** selected-ticket/prize checks, stable pages, picker, marking and delayed computer reactions. JVM tests plus actual native geometry/touch checks. Full revised online-entry acceptance is still pending.
2. **Economy engine (implemented, domain tests passed):** exact allocation, deterministic tie/refund settlement, per-player ticket quantities and versioned persistence. Covers 2–192 ticket pools, ranked houses, ties, conservation, cancellation and invalid saves. This is not yet a server wallet or purchase flow.
3. **Durable service:** migrations, wallets, purchases/refills, frozen schedule, idempotency, concurrency, restart recovery and profile deletion. Preserve own-ticket privacy and concealed future draws.
4. **Online entry/UI:** concise wallet/ticket purchase, Play/matchmaking with computer fill, countdown, coin prizes, settled results and repeat-round purchase. No unrelated features.
5. **Persistent Wi-Fi host (installed, recovery checked):** separate durable database/runtime, restricted DB role, independent deletion journal, rotating backups, health probe and hidden restart supervisor. Current-user login startup is installed. Java, TLS proxy, worker and database recovery retained rooms and claim receipts. The [firewall administrator step and operating limits](WIFI_HOST.md) are documented; the shell is not elevated. Phone reachability, reboot behavior and installed-backup restoration remain unverified.
6. **Wi-Fi candidate:** uniquely versioned APK using the LAN address; two independent clients, purchases, selected claims, payouts, disconnect/rejoin and server restart. Physical-phone Wi-Fi reachability/audio/touch and latency remain required.
7. Public TLS, signing, Store and mobile-network testing are later gates. This PC cannot serve during sleep, power loss or lost connectivity. Record tested uptime/restart behavior precisely.

## Reference evidence

- The supplied 13.76-second clip shows two landscape tickets, recent calls, left avatars, direct dabs and per-ticket prize selection. Arrows suggest paging; the later explicit user instruction establishes it. The clip does not prove verification, latency or opponent identity.
- [Octro's Play listing](https://play.google.com/store/apps/details?id=com.octro.tambola&hl=en) showed 5M+ installs on 26 September, highest among the Tambola-specific candidates checked. This is a store snapshot, not an exhaustive market ranking.
- [Its published rules](https://tambola.octro.com/how-to-play.html) describe virtual ticket purchase, a ticket-funded pool and ticket-level prize choice. Our code, artwork and layouts are original.
- Visible Play reviews report costly progression, interruptions, connection losses and uncertainty about bots. These are reports, not verified findings about the app's algorithm. Design responses: fixed affordable tickets, free refill, durable receipts and visible computer identities.
- Official Android links lead to Play. Its installation picker offered a personal phone and Windows Play Games, not the test emulator. No personal-device install was submitted. The publisher's browser build stopped at Facebook login. Do not describe this as an installed/played Android review.
