# Service operations validation — 26 September 2026

This candidate adds worker-aware readiness, protected fixed-label metrics, alert rules and a constrained Docker runtime. It remains a local candidate; provider deployment, external alert delivery, private ingress/TLS and physical-client acceptance are not established by these checks.

Service JAR SHA-256: `fd258243ffd7a05e0a9e2e427d737642f18b8edc1649edb7dfd2c00b8256fd4e`. Docker image ID: `sha256:670348fef0edbbe72932c39b8be046a1bcdb0a340cc136f32f0d7b8406e82df9`, Linux AMD64, **284,105,361 bytes** as reported by Docker. Its JAR matches the standalone candidate. The image uses pinned Temurin JRE **17.0.20.1+1**; standalone tests used Microsoft JDK **17.0.14.7**, PostgreSQL **16.9** and Windows. Protocol 2, primary migrations 001–003, journal migrations 001–002 and the alpha10 APK remain unchanged.

## Service and operations tests

All **56 service tests passed**, with zero failures/errors/skips, in a **3m 50s** Gradle run that also built installDist/distZip. Six new cases cover worker failure/staleness/recovery, retrying failed cleanup, metrics authorization, concurrent cumulative histograms, fixed labels and actual HTTP responses. Existing two-client WebSocket gameplay now also verifies two active monitored sessions. Domain/client/Android test suites were not newly executed; their unchanged evidence remains separate.

The earlier six focused monitoring cases passed in a 31-second build. The first focused invocation put Gradle's `--tests` option after installDist and failed at task configuration; no test ran in that attempt. Correcting the invocation required no application change.

The real standalone Java proof **ebbef33000654615** passed migration/runtime permission guards, two-player gameplay, restart (**14208 → 8496**), deletion/retry/peer continuation and denied destructive SQL. It additionally rejected missing/guest monitoring credentials and checked that metrics contained none of the fixture names, room code, player IDs or session/monitoring secrets. Revoking SELECT on rooms after startup made the worker fail and readiness return 503 while liveness remained 200 and metrics exposed the failed tick. Restoring the exact grant recovered readiness. Its databases, roles and owned processes were removed.

## Container proof

The same driver ran against the exact image in **de74fff68349428a**, with local-development mode false, fresh non-superuser migration owners and restricted runtime logins. It passed the same startup rejection, gameplay, deletion, monitoring and permission-fault recovery checks. The two serving container IDs were:

- `795e9f3faa76e134ac520fd9c65d473cdda0eca6e90be1b4168bb57cf072008a`
- `04718dd989a00120f97f7303d29ede4dd3b12ba2adf106d1f07183e0880169a3`

The fixture verified image JAR identity, UID/GID **10001:10001**, read-only root filesystem and actual Docker limits of **536,870,912 bytes / 2 CPUs**. It requested all capabilities dropped, no-new-privileges and a 32 MiB `/tmp` tmpfs. Both running containers passed the packaged readiness probe. The first was killed to test restart; the replacement stopped using SIGTERM within Docker's ten-second grace period with exit **143**, rather than requiring SIGKILL. The recorded host PIDs **9208 / 8976** identify Docker CLI children, not JVM PIDs inside the containers. All owned containers/databases/roles were removed. This establishes local idle shutdown after gameplay, not graceful draining under live load or cloud orchestration.

Docker Desktop **28.1.1** was available but stopped initially; it was started for these checks. No registry publication, remote CI run or cloud resources were used. The image build context was **18.30 MB** and allowlisted only compiled runtime distribution files. Provider network/TLS and separately durable journal infrastructure remain outside this fixture.

## Metric and alert verification

Promtool **3.13.3**, image `prom/prometheus@sha256:6976aa8a60fec930796ce5772b8d12da7a318a5daa8d40d69c5c7819a05eeed7`, accepted the seven alert rules, the example configuration syntax and all six synthetic alert scenarios: healthy, worker fault/recovery, scrape/cleanup failure, absent job, sustained resource pressure, and slow/failing API traffic. Actual standalone/container scrape bodies of **58,395 / 58,426 bytes**, each **779 lines**, passed exact-byte exposition parsing/linting.

The first promtool test invocation lacked writable temporary storage under a read-only root and failed before evaluating scenarios; adding a bounded `/tmp` tmpfs fixed the harness. A Windows text-pipe invocation of metric linting also failed; passing the saved HTTP body as exact stdin bytes passed. The accepted checks use the preserved byte-exact bodies. No alert receiver was configured and no notifications were sent; real incident delivery still needs verification.

## Recovery and capacity

The ordinary real-process restart/receipt/event/private-ticket smoke passed. Independent deletion-recovery run **e9879f643ee84e45** passed on this JAR, using processes **8952 → 8972** and a primary-only **18,703-byte** logical archive. It demonstrated the restored deleted identity, rejected a wrong journal, replayed the current journal before serving and retained peer continuity. Cleanup completed. This recovery fixture uses explicit local fixture privileges, separate from the restricted-role container proof.

Default capacity run **4f62dd5485994ded** passed in **8m 4s**: ten rooms, 32 players per room, six tickets each, all 90 calls, **1,920 unique tickets** and **28,800 measured deliveries**, with matching results for every player and successful cleanup. Service PID **24636** used four JVM processors/512 MiB maximum heap on the shared six-logical-processor Windows host. Fixture classes SHA-256: `1f9bf5116d443e08189cf6cf71685e3b1020fdf07221898dfcaf8508dda7a50a`.

Dispatch-to-snapshot latency was p50 **399.5 ms**, p95 **732.1 ms**, p99 **846.8 ms**, maximum **1,218.5 ms**; all room p95 values were **703.9–756.0 ms**. Command round-trip including revision read had p95 **307.0 ms**; acknowledgement-to-snapshot p95 was **534.0 ms**. The stricter dispatch-based p95 bound meets the local one-second committed-delivery target. Gameplay took **446.212 seconds** and **677.000 CPU-seconds** (~**1.52 CPU-seconds per wall second**). The 3,753 post-GC heap samples ranged **2–96 MiB**, ending at **44 MiB**; this is not a 60-minute retained-memory plateau.

For context, the earlier source `4192a84` run measured p95 **708.3 ms**, **643.563 CPU-seconds** and command p95 **277.2 ms**. The new observed CPU and latency are higher while still within the tested delivery budget; shared-host measurements do not isolate which change caused the difference. See [capacity definitions](CAPACITY.md). This rerun exercises active request/worker instrumentation but does not add periodic collector scrapes. It is a separate standalone Java workload using privileged local fixture mode, pre-seeded profiles and manual calls; it does not prove 320-player capacity in the two-CPU container, hosted/mobile latency, automatic-call fault tolerance or long soak behavior.

## Failed driver run and remaining scope

Run **9a6d997139474508** failed before migration because a newly added launch helper collided with the existing gameplay helper name. An unhandled rejected promise interrupted evidence writing, leaving a zero-byte original file; the reviewed failure note is preserved separately. The driver function was renamed. No owned databases/processes remained at inspection; one residual owned migration role was inspected and removed by exact name, then zero matching roles/databases were verified. That attempt is excluded from acceptance. Both corrected final-driver runs passed.

The [operations runbook](OPERATIONS.md) describes deployment, monitoring scope and incident response. Metrics reset on restart; HTTP latency excludes aborted responses and WebSocket delivery; heap usage is not retained-memory evidence. Hosted traffic/alert delivery, automatic-calling and broader faults/soak, large-history deletion, real role rotation/restore, journal durability/retention policy and in-app disclosure, dependency/image advisory review, physical-phone/editorial/audio/accessibility acceptance and production signing remain release gates. Source, safe evidence, distribution/image archives and manifests are preserved in the separate service-operations release package.
