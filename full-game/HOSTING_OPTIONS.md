# Hosting options and reboot recovery

Checked against provider documentation on 2026-09-30. The comparison itself did not change billing; the subsequently approved free domain/tunnel setup is recorded below.

## Free-tier fit

| Option | Published allowance | Fit for this game |
| --- | --- | --- |
| Firebase Spark Hosting | 10 GB stored; 360 MB/day transfer | Static publisher/download pages, not the Kotlin/JVM server. |
| Firebase Spark Realtime Database | 100 simultaneous connections; 1 GB stored; 10 GB/month download | Requires a different data/game architecture. Connections are not a tested player capacity. |
| Supabase Free | 500 MB database; 5 GB egress; 200 Realtime peak connections; 2 million Realtime messages/month; 500,000 Edge Function invocations | PostgreSQL is potentially useful; the existing Kotlin process still needs a host. |
| Google Cloud Run | Request-billed free allowance includes 180,000 vCPU-seconds, 360,000 GiB-seconds and 2 million requests/month | Can run a containerized JVM server, but requires billing and separate durable databases. It is not guaranteed to remain free. |

Sources: [Firebase pricing](https://firebase.google.com/pricing), [Supabase pricing](https://supabase.com/pricing), [Cloud Run pricing](https://cloud.google.com/run/pricing).

Firebase App Hosting and Cloud Functions are not available as a Spark-only game-server deployment. Supabase's hosted functions have a 256 MB memory ceiling, a 150-second free worker lifetime and a two-second CPU allowance per request; they are not a drop-in JVM daemon. Sources: [Firebase plans](https://firebase.google.com/docs/projects/billing/firebase-pricing-plans), [Supabase function limits](https://supabase.com/docs/guides/functions/limits).

Free plans are quota-based, not a promise of a fixed number of free months. Supabase can pause free projects after one inactive week and includes no free uptime SLA or automatic backups. Cloud Run WebSockets keep an instance active and incur billing; connection timeouts require reconnect handling. Builds, networking, storage, secrets and databases can add costs beyond compute. Source: [Cloud Run WebSockets](https://docs.cloud.google.com/run/docs/triggering/websockets).

## How many players?

No cloud player capacity has been measured for v41. The current 30–50-player room roster includes simulated players, so it is not evidence of 50 concurrently connected phones. Older local synthetic tests in PERFORMANCE.md reached 320 players but failed their purchase-latency target; they cannot establish current hosted capacity.

Supabase's 200 Realtime connections is a service quota, not an allocation of CPU for 200 game clients. Likewise Firebase's 100 database connections does not prove 100-player gameplay performance. If a hypothetical rewrite delivered exactly 90 counted realtime messages per player-round, two million messages would allow about 22,222 player-rounds per month before that quota alone. Marks, claims, presence, lobby changes and retries increase usage, and bandwidth or database limits may be reached first. This is arithmetic, not a capacity forecast for the existing app.

Before promising capacity, measure v41 with 25/50/100 real-equivalent connections, six-ticket marking traffic, simultaneous room starts, claims and reconnects, and record CPU, memory, database growth, bytes and p95 latency. Budget free usage from those measurements.

## Selected direction and remaining work

The user initially requested free/domain-free recovery, then confirmed an existing GoDaddy domain and authorized inspecting it for setup. Prefer a stable game subdomain with a named tunnel on the current PC. Domain registration remains with its current registrar; DNS requirements must be checked after sign-in. Inventory and preserve existing website/email records before any DNS migration. The selected domain is thefinxperts.com; the completed setup and APK migration are recorded below.

The current client explicitly trusts only publisher-discovered `*.trycloudflare.com` origins. A permanent hostname therefore also requires a narrowly scoped client allowlist/build configuration update, validation and a new versioned APK. Keep the old quick tunnel for old clients until migration is verified. Do not replace the hostname in the existing discovery feed while v41 rejects custom domains.

The stable address removes post-reboot address rotation and directory-cache propagation, not the reboot itself. The PC must remain powered and connected; automatic login/startup/watchdogs restore it after a restart. Eliminating downtime while the PC is off requires another host or failover.

Quick Tunnels are development infrastructure with no uptime guarantee and a 200 in-flight request limit, which is not a player-capacity promise. Source: [Cloudflare Quick Tunnels](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/do-more-with-tunnels/trycloudflare/).

## Domain preparation (2026-09-30)

Cloudflare Free has been selected for the existing GoDaddy domain. All eight application DNS records were imported and compared with the registrar: the root A record, www and _domainconnect CNAME records, three Zoho MX records with priorities 10/20/50, SPF and DMARC. Existing A/CNAME records were set to DNS-only in the pending Cloudflare zone to preserve website routing. The registrar's two authoritative NS records and SOA are provider-managed, not application records to copy. GoDaddy DNSSEC is currently off.

After explicit confirmation, GoDaddy saved chad.ns.cloudflare.com and lilith.ns.cloudflare.com in place of its previous nameservers. Public DNS confirms the delegation and Cloudflare reports the domain active. The root A record, www CNAME, Zoho MX priorities, SPF and DMARC were verified against Cloudflare DNS after the cutover.

The named tunnel `tambola-jalsa-pc` is healthy and routes `https://play.thefinxperts.com` to the existing loopback-only game gateway at `http://127.0.0.1:18081`. The root website stays DNS-only. A restricted token file is stored outside the repository under `%LOCALAPPDATA%/TambolaJalsaTunnel/token`. Never print or commit it. `tools/install-named-tunnel.ps1` installs a hidden current-user scheduled task with a login trigger, no runtime limit and up to 999 one-minute failure restarts. Existing Windows automatic sign-in is still required after boot; this is not proof of a new reboot test.

The two-minute `Tambola Jalsa Host Watchdog` also starts the named tunnel task when Task Scheduler leaves it in `Ready` after a connector exit. It does not read the tunnel token or restart an already running connector. This recovery still depends on the PC being powered on and the user session being signed in.

Validation: connector readiness 200, Cloudflare Healthy, public HTTPS readiness protocol 9, and `verify-expanded-beta.mjs --permanent --ads-enabled` passed the lobby/economy checks. During propagation the integration probe used 1.1.1.1's current answer with normal hostname/TLS verification because the local resolver cached NXDOMAIN; a subsequent ordinary curl without a DNS override also returned readiness 200. Evidence is in `reviews/permanent-domain-2026-09-30.json`. No phone acceptance, complete round or reboot test was performed for this tunnel.

The existing quick tunnel and v41 discovery feed remain running. Installed v41 APKs use the old address until users accept the v42 APK update. V42 has now been published as `full-game-alpha42-permanent-server`, with the fixed hostname transport and unchanged saved-profile identity. Publication makes the update discoverable; it does not silently update installed apps. See APP_UPDATES.md and reviews/release-v42 for validation.
