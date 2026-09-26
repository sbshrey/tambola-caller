# Operating private Tambola rooms

This is a deployment handoff for the local service candidate. Provider/project/budget, public DNS/TLS, secret provisioning, alert delivery, backup independence and physical-phone acceptance still need a real environment. Use the exact source/JAR/image recorded in [operations validation](OPERATIONS_VALIDATION.md). The image and example monitoring configuration are not a deployed service.

## Build and start

From `full-game/`, build `:server:installDist :server:distZip` with JDK 17 and the committed dependency verification metadata. Then run `docker build --file server/Dockerfile --tag tambola-rooms:reviewed .`. The build context allowlist contains only the compiled distribution and Dockerfile. Source, Android assets, test data, local secrets and signing keys are excluded. The runtime base is an official [Eclipse Temurin image](https://github.com/adoptium/containers), pinned by digest in the Dockerfile; review security updates and rebuild deliberately. A digest establishes identity, not absence of vulnerabilities.

The image runs the JVM directly as UID/GID **10001**, has no embedded database/monitoring credentials and exposes port **8080**. `--migrate` is passed to the same entrypoint for a separate migration job. Follow [DATABASE_PERMISSIONS.md](DATABASE_PERMISSIONS.md) to provision two independently retained databases, separate owners/runtime roles, migrations and exact grants. An application container must receive only runtime credentials. Owner credentials belong only to the isolated one-off migration job.

Run with a read-only root filesystem, a small `/tmp` tmpfs, all Linux capabilities dropped, `no-new-privileges`, and explicit CPU/memory limits. The tested container smoke envelope is **2 CPUs / 512 MiB**, with `-XX:MaxRAMPercentage=60`; it is not a 320-client capacity claim. JVM native memory and the probe also need headroom. The previous capacity workload used a different Java-process envelope. Size the real deployment using monitored staged load and cost limits.

Provide database variables through the secret manager as described in the role runbook. Set `TAMBOLA_TLS_PROXY=true` only behind the verified TLS ingress; the image binds `0.0.0.0`, but the flag does not implement encryption. Keep `TAMBOLA_LOCAL_DEVELOPMENT` unset/false. Configure PostgreSQL TLS and CA verification for provider connections. Restrict database networking to application/migration/backup operators. A localhost port mapping is used in fixtures; do not expose their unencrypted HTTP to the Internet.

The initial live service should use one replica until hosted multi-process capacity, ingress address-based limits and connection draining are verified. Set connection/body/WebSocket limits at ingress, reject forged forwarding headers, keep health/metrics on private routes, and deny `/internal/*` at public ingress. Run operator probes and metrics over the protected management network. Start with a conservative player/room enrollment budget and an explicit provider spending limit; no autoscaling or paid resources are configured here.

## Reproduce the isolated deployment check

For invitation links, configure `TAMBOLA_PUBLIC_ORIGIN` to the same HTTPS origin compiled into the APK. Publish the installed app's public signing fingerprints with `TAMBOLA_ANDROID_CERT_SHA256`; set a real `TAMBOLA_ANDROID_INSTALL_URL` when distribution is available. Expose `/invite/*` and `/.well-known/assetlinks.json`, keep their privacy headers intact, and redact invitation paths from proxy logs. See [invite deployment acceptance](../INVITES.md). The earlier image/capacity evidence has its own recorded JAR hash; rebuild and validate a new image after source changes.

After installDist, provide `JAVA_HOME`, the three `TAMBOLA_TEST_DATABASE_*` variables and `TAMBOLA_PG_BIN` as in the role runbook. The exact loopback `tambola_test` fixture administrator needs CREATEDB/CREATEROLE; no production credentials are accepted by this driver. `node tools/server-permissions-smoke.mjs` first exercises standalone Java. To run the same proof in Docker, build the image and set `TAMBOLA_TEST_SERVICE_IMAGE` to its exact `sha256:<64 hex>` image ID, then run the driver again. Remove that override to return to standalone checks.

The driver verifies the image JAR against installDist, supplies random owner/runtime/monitoring secrets only through child environments, creates fresh roles/databases and binds only a free localhost HTTP port. It uses `host.docker.internal` for the isolated PostgreSQL connection; on Linux it adds the host-gateway mapping, so the test database must be reachable on that private bridge (as with CI's explicitly published isolated database port). It never changes an existing PostgreSQL listener or access policy. It cleans only its generated labelled containers, roles and databases. The final readiness test temporarily revokes one runtime SELECT grant, observes failure and restores that exact grant. Safe evidence and byte-exact metrics remain under `.test-workspace/permissions-<run>/`.

## Probes, worker and retention

- `/health/live` returns 200 while the HTTP process responds, even when its databases or worker fail. Container health uses this probe so a database incident does not cause a restart storm.
- `/health/ready` requires a complete worker pass within 30 seconds, no subsequent worker failure, primary connectivity and healthy deletion recovery. It returns 503 during initial worker startup, failure or stalling. Configure ingress/orchestrator readiness checks separately from liveness; removing readiness must not be interpreted as deletion success.
- The worker replays up to ten deletion intents, ticks active rooms, then runs cleanup if due. Cleanup runs in the first pass and at least every 60 seconds after its last success when the worker is making progress. A failed cleanup remains due and is retried next pass. Every pass records its step outcomes; cancellation is not reported as an operational failure. Existing retention periods and journal suppression rules are unchanged.
- The packaged `HealthProbe` class checks loopback HTTP without credentials, response output or a curl dependency. It accepts `live` or `ready` and exits 0 only for HTTP 200. The image's liveness probe uses a 32 MiB maximum heap, 30-second interval/start period, five-second outer timeout and three retries.

For a release, stop admitting new rooms, drain or explicitly notify existing sessions through an approved operational channel, stop the old worker, run reviewed migrations/grants, start the replacement without public ingress, verify probes and a fictional full round/deletion/retry, then admit traffic. Use SIGTERM and a bounded termination grace period. The local container check verifies clean idle shutdown after gameplay; it does not prove draining under live load. Clients retain command IDs for uncertain responses. Never roll back primary and journal together or downgrade past incompatible migrations; use the [restore runbook](BACKUP_RECOVERY.md).

## Private metrics

Set `TAMBOLA_METRICS_TOKEN` to an independent cryptographically random URL-safe secret (43–128 characters; 32 random bytes encoded base64url is suitable). Missing configuration leaves `/internal/metrics` disabled with 404. When configured, it requires `Authorization: Bearer <monitoring secret>`; guest tokens, query parameters and missing/wrong credentials get 401. Never share this secret with the APK or reuse a guest/database credential. Rotation currently requires replacing the secret and restarting/revalidating the service and collector.

Metrics use the [Prometheus text format](https://prometheus.io/docs/instrumenting/exposition_formats/) and process-local cumulative counters/histograms. Every label comes from a fixed enum: route category, HTTP status class, worker step/outcome, pool store/state, or histogram bucket. There are no room codes, names, player/session/command IDs, addresses, paths, query strings, SQL or exception messages. Scraping reads in-memory state and pool counters and performs no database query.

| Metric | Meaning and interpretation |
| --- | --- |
| `tambola_http_duration_seconds` | Completed HTTP response histogram; count also supplies request volume/error rate. Fixed categories cover health, guest, logout, deletion, create, join, read, commands and other. Metrics requests and WebSocket sessions are excluded. Aborted responses that never reach Ktor's ResponseSent hook are not included. |
| `tambola_worker_step_duration_seconds` | Replay/tick/cleanup duration and success/failure counts; unfinished cancellation is excluded. |
| `tambola_worker_enabled`, `tambola_worker_ready`, `tambola_worker_success_age_seconds` | Worker mode and recent complete-pass health. Age is -1 until the first pass. Readiness still separately checks database/recovery state. |
| `tambola_cleanup_success_age_seconds` | Age of last successful cleanup, -1 before the first success. |
| `tambola_deletion_replayed_total` | Intents applied by this process's periodic worker; excludes pre-listener startup replay. |
| `tambola_websocket_active`, `tambola_websocket_opened_total`, `tambola_websocket_failures_total` | Session counts; failures cover handled API/database closures, not every possible network closure or invalid frame. No session-duration or delivery-latency claim. |
| `tambola_database_connections` | Active/idle/pending/total per primary/journal pool. Snapshots can change between reads. |
| JVM heap/thread, process CPU/uptime | Local JVM usage, not host/container memory or a retained-heap leak measurement. Also collect provider/container resource, disk, network and database metrics. |

`ops/prometheus.example.yml` is a syntax-checkable HTTPS template using a mounted secret file and a placeholder private target. Replace it with actual private routing/discovery, TLS and secret provisioning. `ops/alerts.yml` covers unavailable scraping, failed/stalled workers, overdue cleanup, HTTP errors/p95 duration, waiting database connections and heap pressure. Thresholds are initial operating choices to tune under staged traffic. HTTP latency is not the separate call-to-player delivery metric. [Prometheus alert rules](https://prometheus.io/docs/prometheus/latest/configuration/alerting_rules/) must be connected to an operator-approved Alertmanager receiver; this repository sends no notifications. Verify delivery and recovery with a controlled incident before launch.

Run rule validation and the six synthetic scenarios with `promtool check rules alerts.yml` and `promtool test rules alerts.test.yml` from `server/ops/`. Use a temporary writable directory when running promtool tests in a read-only container. Validate actual scrape output with `promtool check metrics`. The local evidence pins the exact promtool image; no dependency is added to the game service.

## Incident actions

| Signal | Operator response |
| --- | --- |
| Scrape unavailable | Check collector reachability, private TLS, monitoring token/rotation and process status. Use separate external liveness/readiness probes; absence of metrics is not a healthy zero. |
| Worker unavailable / readiness 503 | Keep ingress unready. Check sanitized worker exception class, per-step failure counter, PostgreSQL connectivity/locks and effective runtime grants. Restore the underlying dependency and verify a successful pass; do not bypass replay or broaden grants to owners. |
| Replay failure / deleted-profile concern | Preserve the current independent journal, isolate primary traffic and follow the restore runbook. Verify journal identity/sequence and known fictional deletion confirmation. Never erase intent rows or skip the cursor. |
| Cleanup overdue | Inspect transaction/lock/pool pressure and retention backlog administratively. Retain due retries; do not delete journal suppression records as cleanup. Validate remaining backup/PITR policy separately. |
| HTTP errors or latency | Check route/status aggregates, worker/DB/pool pressure, ingress limits and deployment changes. Reduce admissions before increasing capacity; avoid request-body/header logging. |
| Heap/pool pressure | Check sustained load, active streams, connection caps and database health. Capture only reviewed, access-controlled diagnostics; heap/thread dumps can contain credentials or profile data. Do not publish them as release evidence. |

Application failures log exception class only, without request bodies/headers. No access-log plugin is enabled. Configure ingress/provider redaction and bounded log retention independently; do not turn on unrestricted framework/driver debug logs. Startup framework/driver diagnostics still require restricted operator access and review. Record source revision, image digest, migration versions and sanitized incident times, never session secrets. Test alert delivery, restores, rotation and rollback on the real provider before public release.
