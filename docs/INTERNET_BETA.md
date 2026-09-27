# Internet beta hosted on this PC

The `publicBeta` Android variant uses public HTTPS/WSS through a Cloudflare Quick Tunnel to the existing PC game service. It has package `io.github.sbshrey.tambola.game.beta`, label **Tambola Internet Beta**, and its own local profile. It can coexist with the Wi-Fi app. It uses the existing development signing key, not a production/Store identity. Do not uninstall it to fix a connection: reinstalling loses its local wallet credentials.

## How players connect

The APK reads only the publisher-controlled [server directory](https://raw.githubusercontent.com/sbshrey/tambola-caller/codex/public-beta-channel/server.json). The directory contains no secrets, expires after 24 hours and accepts only an HTTPS `trycloudflare.com` origin. The client refreshes it after a minute, sends no credentials to GitHub and refuses redirects. Directory lookup never creates, replaces or retries purchase commands. The encrypted profile uses a stable identity while the tunnel address changes. Existing saved request IDs, wallet/session renewal and reconnect behavior still apply. Alpha28 supports friends-table invitations; legacy private-room `/invite/` links remain separate and unsupported in this beta.

The hidden host supervisor renews the directory every twelve hours and republishes it after a tunnel process restart. A reconnect can take a few minutes during tunnel creation and GitHub cache propagation. Existing connections are not redirected mid-request. The public directory's GitHub account is part of the transport trust boundary; protect that account and keep the original database/journal together with their existing recovery rules.

## Play with friends (alpha32)

Choose 1–6 tickets, then **Play with friends**. One player chooses **Create table** and taps **Invite friends**. The prepared message includes a stable publisher-website link, the eight-character code and an APK download fallback. The English/Hindi web page opens the beta through an explicit Android intent, restricted to its package; this is not a verified Android App Link. New players install the APK, return to the invitation and choose their tickets. **Join · N coins** confirms the purchase. Manual **Join with code** and **Copy code** remain available. Opening a link never buys, registers or leaves an existing game. Only code holders can buy into that table; quick matchmaking never fills it. The group can include 2–8 people, with no computer seats. Codes are bearer invitations, so share them only with the intended group.

The host chooses **Start game** after everyone arrives. Each member must still be connected; the server freezes the actual ticket pool and starts five-second calls. There is no automatic 12-second start for friends. A table waiting for more than 15 minutes closes and returns its purchases; leaving before the start refunds the leaving player. A host who leaves hands over to a remaining member; an offline host hands over to the oldest connected member. Disconnected members must return before a start. Once started, reconnect to the same purchased tickets and saved marks.

After a friends round, choose tickets and **Same friends** to join the group's next lobby without entering another code. Each person confirms their own purchase; the first person to opt in hosts the next round. Everyone who chooses this from the same completed round reaches the same next lobby. Its new code can invite additional friends. An expired or already-started next lobby cannot be joined; use **Other table** for a new group. Finished results and tickets are preserved separately.

Install alpha32 over alpha24–alpha31 to retain the existing beta profile. During a connection loss, the waiting table retains its width and displays its last confirmed details with **Reconnect now**. The host's start button reads **Waiting for connection** until live state returns. A pending purchase or command retains its separate exact-retry flow. [Native and public reconnect verification](../full-game/reviews/friend-reconnection-2026-09-27/README.md) covers the same room, ticket purchase and pre-start refund on the owned emulator.

During a game, tap the recent numbers beside the latest call to open **Call history**. It lists revealed calls newest first, with their call indices. **Game options → Number board** opens the complete 1–90 grid; checkmarks and accessibility labels distinguish called numbers, and the latest call has its own label. Switch tabs and scroll to inspect older calls. Closing the view returns to the selected ticket page without marking numbers or submitting claims. Calls continue while the view is open. [Implementation and verification](../full-game/reviews/call-history-2026-09-27/README.md).

Results keep full wrapping prize names, with fewer cards per row at larger text sizes. Confirmed claims identify the ticket and prize in two measured lines, and the portrait prize-count label expands at larger text sizes. The share sheet prepares a message for the user to send and never sends it automatically. An invitation into the current table offers **Return to my game** without another charge. Another unfinished table or a pending request must be resolved before joining. Invalid links show a generic explanation; links cannot select a service address, identity or ticket quantity.

Alpha31 invitation URLs use `https://sbshrey.github.io/tambola-caller/friends/#CODE`, independent of the tunnel. The page reads only a validated code fragment in the browser, performs no server lookup and sends no code in its network requests. Sharing a saved code needs no directory lookup. The app resolves the current server when joining; the PC must still be online and codes still expire when their table closes or starts. Previously shared tunnel-hosted links remain temporary; use their manual code or share again from alpha31. Mobile-browser-to-app handoff on a physical phone remains an acceptance check; browsers without Android intent support can use the manual code.

The stable page is published on the caller website's existing `main` branch. Its isolated update preserves the caller's assets/cache and corrects the old service worker's broad navigation fallback. See the [browser upgrade and native acceptance record](../full-game/reviews/stable-invitations-2026-09-27/README.md). For later page updates, review the four published files and run `scripts/publish-friend-page.mjs --expected-base <inspected-main-SHA>` to preflight, then add `--apply` to publish. It refuses uncommitted page files, a changed main commit or a changed caller cache manifest. The current publication record defaults to `full-game/.test-workspace/friend-page-publication.json`; set `TAMBOLA_INVITE_PUBLICATION_RECORD` to retain it in the new review. The browser verifier similarly accepts `TAMBOLA_INVITE_REVIEW_DIR`. Verify the resulting Pages build and public page before distributing new links.

## PC operation

Installed helpers live in `%LOCALAPPDATA%\TambolaTogetherPublicHost`. The game/database service runs in `%LOCALAPPDATA%\TambolaTogetherHost`, with its existing databases and private LAN TLS. Service updates are tracked separately from the public gateway and APK.

The 27 September service update bounds concurrent quick-play purchases so an allocator backlog leaves database capacity for wallet checks and friend tables. [Validation and deployment](../full-game/reviews/purchase-admission-2026-09-27/README.md) include the reproduced availability failure, 158 passing server tests and public purchase/refund checks. Large quick-play bursts remain above the latency target; alpha28 players do not need to reinstall for this update.

The public gateway listens only on `127.0.0.1:18081`, forwarding allowlisted game paths to `127.0.0.1:18080`. It serves only the bounded `/friends/CODE` invitation pages locally, without querying room membership or reflecting request hosts. Pages have no scripts, external assets, analytics or automatic redirects and suppress referrers. Internal metrics, arbitrary proxy paths and legacy `/invite/` routes stay blocked. The gateway bounds API bodies to 32 KiB, limits per-address requests/registration, and caps WebSocket streams at 80. It removes forwarded headers before contacting the game service. Cloudflare supplies `CF-Connecting-IP` to this loopback-only gateway; the original service retains its own conservative shared-peer quotas. Neither database ports nor router inbound ports are exposed.

Use PowerShell 7:

```powershell
& "$env:LOCALAPPDATA\TambolaTogetherHost\runtime\powershell\pwsh.exe" -File full-game/tools/public-host.ps1 -Action Status
& "$env:LOCALAPPDATA\TambolaTogetherHost\runtime\powershell\pwsh.exe" -File full-game/tools/public-host.ps1 -Action Stop
& "$env:LOCALAPPDATA\TambolaTogetherHost\runtime\powershell\pwsh.exe" -File full-game/tools/public-host.ps1 -Action Start
```

`Stop` stops only the Internet bridge. It leaves the original Wi-Fi game/database service running. `RemoveStartup` removes its login shortcut. The supervisor runs after this user signs in, not before login. Keep this PC awake, signed in and connected. The directory publisher uses the existing Windows GitHub CLI credential store; no GitHub or database token is placed in the APK or public directory. Status timestamps are observations, not continuous health proof.

For a fresh installation, initialize the `codex/public-beta-channel` branch with `server.json`, authenticate `gh` for this repository, install Node and cloudflared, run `public-host.ps1 -Action Install`, then `Start` and optionally `InstallStartup`. Those actions install a persistent helper and expose the existing game service to the Internet. No cloud subscriptions are purchased.

## Build and validate

```powershell
cd full-game
.\gradlew.bat :client:test :app:testDebugUnitTest :app:assemblePublicBeta :app:lintPublicBeta
node --test tools/test-public-gateway.mjs
.\gradlew.bat -PtambolaBenchmarks=true :macrobenchmark:assemblePublicBeta
```

The external `InternetBetaTest#fullRound` driver opts in with instrumentation argument `tambolaInternetBeta=true`. It requires a fresh beta profile on the dedicated emulator, drives the actual optimized app, uses the public directory for its peer, and deletes only its QA profiles. It checks a full computer-opponent round, actual payout shares, process recovery and replay/refund; it is not a physical-phone or load test. Do not run it against a player's existing beta profile.

## Limits and follow-up

This is a small-group prerelease. [Cloudflare Quick Tunnels](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/do-more-with-tunnels/trycloudflare/) have no uptime SLA and a 200 concurrent-request limit. A restarted Quick Tunnel receives a different hostname; the directory handles that for this APK. The gateway cap is a protective limit, not a measured player-capacity claim. Hosting availability still depends on this PC and its home connection.

Public full-round/emulator checks do not establish mobile-data performance, physical touch/audio quality, router/ISP uptime, production signing or Store readiness. Use a stable named tunnel or cloud server for broader release. APK binaries can be distributed through [GitHub Releases](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases); the existing GitHub Pages caller remains separate.
