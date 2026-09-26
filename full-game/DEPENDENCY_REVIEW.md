# Runtime dependency review

26 September 2026. This review covers resolved Maven runtime coordinates for the service, native client, and both Android build variants. It is one release check, not a penetration test or a guarantee that the app has no vulnerabilities.

## Findings and update

The baseline inventory queried 223 distinct module/version pairs. OSV returned 35 distinct active advisory IDs across eight service module versions: Logback core 1.5.21 and Netty 4.2.7.Final compression, HTTP/1, HTTP/2, handler, epoll classes and native epoll/kqueue artifacts. These are package-version matches; the report does not establish that every affected feature is reachable in this service.

The service now aligns all Netty modules through the **4.2.18.Final BOM** and uses **Logback 1.6.4**. Ktor remains 3.3.3. The exact native-client and Android debug/release component and artifact inventories are unchanged. The app's alpha12 APK was not rebuilt for this service update.

The complete updated query at **06:35:20 UTC** covered **225 distinct module/version pairs** and returned **zero active OSV matches**. The resolved scopes contain 73 service, 46 client, 183 Android debug and 176 Android release modules, including platforms and shared coordinates. The version-pair total deduplicates across scopes.

Maintainer sources supplement the database query. Netty's [4.2.18 release](https://netty.io/news/2026/09/09/4-2-18-Final.html) contains security and correctness fixes. Its [HTTP/1.1 pipelining advisory](https://github.com/netty/netty/security/advisories/GHSA-pvjx-v7vp-62vq), published 10 September, identifies versions through 4.2.17 as affected and 4.2.18 as fixed; this particular ID was absent from the baseline OSV response. A clean database result alone therefore cannot replace release-note review. [Logback's release history](https://logback.qos.ch/news.html) documents the 1.6.4 stable release and the 1.6 migration. This application uses a static console appender and no removed Janino conditional configuration.

## Artifact identity and trust

The 41 added Gradle artifact checksums were compared with freshly downloaded HTTPS Maven Central bytes. All matched; all 1,280 existing artifact entries were preserved. This is checksum/source consistency, not independent publisher-signature verification. An initial request to `repo.maven.apache.org` failed certificate-expiry validation; the successful check used `repo1.maven.org` with normal certificate verification, without relaxing TLS checks.

- Inventory SHA-256: `c40d09fd0dc989aab8954aaebc6486ca78e2d5b76927b01c1ddfd35b37f70fc6`.
- Service runtime SHA-256: `a717f30a18158027ab0902ecddc7b0c830312a9bae57b178c789592fcc47ba14`.
- Service main JAR SHA-256: `a1307739a2c83c47acb1176c2ea61a2dd0e6ac28c499e9803e4f0ffd8bed5b3a`.
- Distribution ZIP SHA-256: `24209342a1c7860afd1351851e65f9caed93b0623f8f0766277473df6423af79`.

The main JAR is unchanged from the earlier service implementation even though its libraries changed. Process, restore, permission, capacity and automatic-recovery reports now identify the **entire 51-JAR runtime**. The canonical manifest sorts ASCII filenames and contains `<lowercase SHA-256><two spaces><filename><LF>` for each JAR; its UTF-8 SHA-256 is the runtime fingerprint. Both Node and Kotlin fixtures use this convention, and container checks compare all image libraries with the local distribution. Fixtures reject runtime changes during execution.

All **eight native `.so` files** in the unchanged alpha12 APK are byte-identical to their verified upstream AAR entries: `androidx.graphics:graphics-path:1.0.1` and `androidx.datastore:datastore-core-android:1.1.7`, each across four ABIs. Those exact module versions are included in the completed advisory query. The provenance report ties them to APK SHA-256 `5fcc685d5e253d7b674208a02efbec4726b0f25810df419745ddfd056d9e5fc9`; no extra bundled native library was found. This identifies their source artifacts and package-level advisory coverage, not an independent native-code audit.

## Repeatable check

From `full-game/`, with the normal JDK/Android SDK setup:

```text
python tools/test-runtime-advisories.py
node --test tools/service-runtime.test.mjs
gradlew -I tools/runtime-inventory.gradle runtimeDependencyInventory --no-daemon --console=plain
python tools/check-runtime-advisories.py .test-workspace/runtime-dependencies.json --output .test-workspace/runtime-advisories.json
```

Choose a fresh report path for each local run. `-PserverOnly=true` deliberately omits Android scopes and cannot establish the four-scope result above. The scanner sends only public Maven coordinates to the [OSV batch API](https://google.github.io/osv.dev/post-v1-querybatch/), follows per-package pagination, retrieves matched advisory details and retains withdrawn records without treating them as active. Exit 0 means a completed query with no active matches; exit 1 means active matches; exit 2 means an incomplete/network-failed check. Malformed inputs also fail the command. CI retains inventory/report artifacts even when the scan fails. Four pagination/error tests and two complete-runtime identity tests cover the fail-closed behavior and dependency-only changes.

Advisories evolve. Repeat the query and maintainer review immediately before a release. Do not regenerate trusted Gradle checksums merely to silence a mismatch: investigate the changed artifact and review its provenance.

## Validation and remaining scope

All **106 domain/client/service cases passed** (25/14/67), with zero failures, errors or skips, in a 3m 46s Gradle invocation. Six advisory/runtime-identity helper tests also passed. The service distribution uses the runtime fingerprint above. The following real-process checks ran sequentially after the unit/integration suite:

| Check | Updated-runtime result |
| --- | --- |
| HTTP restart and idempotent retry | Passed, preserving private tickets, event replay and the original command receipt |
| Primary-backup restore | Run `e29fc62323a646bb`; 18,723-byte archive; restored deleted identity proved present, wrong journal rejected before listening, correct journal replay and peer continuity passed; PIDs 10508 → 11020 |
| Restricted roles and worker recovery | Run `59278d697d8a403d`; startup/grant guards, gameplay, restart, deletion, denied destructive SQL, protected metrics and recovery from a deliberately failed worker passed; PIDs 21364 → 10784 |
| Full automatic game with faults | Run `4f30a387795d49e9`; 32 clients, 192 private distinct tickets, two services, all 90 calls, exactly one pause/resume, host succession, lost-response retry, slow consumer and both-service outage passed |
| Ten-room gameplay capacity | Run `10bc9a69b1064e23`; 320 clients, 1,920 tickets and all 90 calls per room; 28,800/28,800 deliveries and matching results; p95 724.655 ms, cleanup complete |

All owned resources were cleaned by the accepted process fixtures. The automatic run completed in **502.507 seconds** including cleanup (8m 34s Gradle invocation). Stored draw gaps were **5,008–15,888 ms**; the full-service outage lasted **12,161.602 ms**, with no persisted progress during it. All players recovered matching results and their manual marks, without duplicate calls or a catch-up burst. A scanner database download overlapped part of this correctness/recovery run; this is not an isolated timing benchmark. The [automatic fixture guide](server/AUTOMATIC_RECOVERY.md) describes its 250 ms native-client retry loop and Android/hosted limitations.

The default [capacity fixture](server/CAPACITY.md) completed in an **8-minute** Gradle invocation. Dispatch-to-snapshot latency was p50 **414.721 ms**, p95 **724.655 ms**, p99 **847.130 ms**, maximum **1,039.218 ms**; every room's p95 was under one second (679.643–766.985 ms). This conservative bound includes the command revision read, execution/commit and delivery, so it satisfies the local p95 target. The maximum is allowed to exceed a percentile target. Command round-trip p95 was 329.013 ms; acknowledgement-to-snapshot p95 was 515.860 ms. Gameplay took 446.261 seconds and 653.531 server CPU-seconds. No scanner/build workload ran during this capacity measurement.

The service used four active JVM processors and a 512 MiB heap on the shared six-logical-processor Windows host with Microsoft JDK 17.0.14.7 and PostgreSQL 16.9. These JVM settings are not OS quotas. There were 3,136 post-GC heap observations ranging from 3–109 MiB, ending at 54 MiB; this eight-minute run does not establish an hour-long retained-memory plateau. The evidence retains full per-room distributions and the safe GC log. Separate queries confirmed zero databases/roles remaining for the four accepted process runs.

Earlier service reports retain their original binary identities. No APK rebuild or Android instrumentation rerun was needed for the unchanged Android/client runtime. The CI definition has been updated, but no remote CI run is claimed. The immutable `releases/service-security-2026-09-26/` package ties the updated ZIP, exact runtime manifest, inventories, scans, native provenance, tests and process evidence to its committed source revision. It records `productionReady: false`; hosting, a freshly validated container, physical acceptance and signing remain separate.

This inventory excludes build plugins and test-only tools. OSV coverage and indexing can be incomplete, including native code published within Maven artifacts. Android platform libraries are supplied by the device OS and are outside the APK inventory. The subsequent [container review](server/CONTAINER_DEPENDENCY_REVIEW.md) records JRE/Ubuntu findings, three patched OS packages, remaining lower-severity matches and exact container behavior. Unknown vulnerabilities, feature reachability and hosted ingress/configuration are outside this query. Build-tool review, deployed configuration, physical-device acceptance and the other production gates remain open.
