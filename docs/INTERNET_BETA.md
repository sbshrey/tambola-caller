# Internet beta hosted on this PC

The `publicBeta` Android variant uses public HTTPS/WSS through a Cloudflare Quick Tunnel to the existing PC game service. It has package `io.github.sbshrey.tambola.game.beta`, label **Tambola Internet Beta**, and its own local profile. It can coexist with the Wi-Fi app. It uses the existing development signing key, not a production/Store identity. Do not uninstall it to fix a connection: reinstalling loses its local wallet credentials.

## How players connect

The APK reads only the publisher-controlled [server directory](https://raw.githubusercontent.com/sbshrey/tambola-caller/codex/public-beta-channel/server.json). The directory contains no secrets, expires after 24 hours and accepts only an HTTPS `trycloudflare.com` origin. The client refreshes it after a minute, sends no credentials to GitHub and refuses redirects. Directory lookup never creates, replaces or retries purchase commands. The encrypted profile uses a stable identity while the tunnel address changes. Existing saved request IDs, wallet/session renewal and reconnect behavior still apply. This beta does not support private-room invitation links.

The hidden host supervisor renews the directory every twelve hours and republishes it after a tunnel process restart. A reconnect can take a few minutes during tunnel creation and GitHub cache propagation. Existing connections are not redirected mid-request. The public directory's GitHub account is part of the transport trust boundary; protect that account and keep the original database/journal together with their existing recovery rules.

## Play with friends (alpha25)

Choose 1–6 tickets, then **Play with friends**. One player chooses **Create table** and shares its eight-character code using **Invite friends** or **Copy code**. Others choose **Join with code** with their own ticket quantities. Only the code holders can buy into that table; quick matchmaking never fills it. The group can include 2–8 people, with no computer seats. Codes are bearer invitations, so share them only with the intended group.

The host chooses **Start game** after everyone arrives. Each member must still be connected; the server freezes the actual ticket pool and starts five-second calls. There is no automatic 12-second start for friends. A table waiting for more than 15 minutes closes and returns its purchases; leaving before the start refunds the leaving player. A host who leaves hands over to a remaining member; an offline host hands over to the oldest connected member. Disconnected members must return before a start. Once started, reconnect to the same purchased tickets and saved marks. A completed group currently creates a new code for another friends round.

Install the alpha25 Internet Beta over alpha24 to retain its profile. Code sharing is supported; tapping an invitation to open the app directly is not yet supported. The share sheet prepares a message for the user to send and never sends it automatically.

## PC operation

Installed helpers live in `%LOCALAPPDATA%\TambolaTogetherPublicHost`. The original game/database service stays in `%LOCALAPPDATA%\TambolaTogetherHost`; its databases, runtime and private LAN TLS are unchanged.

The public gateway listens only on `127.0.0.1:18081`, forwarding allowlisted game paths to `127.0.0.1:18080`. It blocks internal metrics, arbitrary proxy paths and invitation routes, bounds request bodies to 32 KiB, limits per-address requests/registration, and caps WebSocket streams at 80. It removes forwarded headers before contacting the game service. Cloudflare supplies `CF-Connecting-IP` to this loopback-only gateway; the original service retains its own conservative shared-peer quotas. Neither database ports nor router inbound ports are exposed.

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
