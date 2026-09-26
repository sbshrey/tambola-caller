# Container dependency review — 26 September 2026

The Linux AMD64 service image now includes reviewed Ubuntu fixes for `libc6`, `libc-bin` and `perl-base`. Its completed Grype scan has **no High/Critical matches and no Medium matches with available fixes**. Lower-severity package advisories remain below; this is not a claim of zero vulnerabilities or hosted production acceptance.

## Identity and fixes

- Patched image ID: `sha256:91d29dd68779dd83fc290c2543ef1dc84fcc474ea52a3184165091d322d859bb`; Docker-reported size **309,800,691 bytes**, Linux AMD64.
- Prior image ID: `sha256:35106eb7d931f967ea58dcebf24bf9ab252772e14e5cb6c46b73f1d4082deaac`.
- JRE: **Temurin 17.0.20.1+1**, confirmed by executing `java -version` in the patched image. It includes Adoptium's [September security release](https://adoptium.net/news/2026/09/eclipse-temurin-8u504-110321-170201-210121-25041-26021-available).
- Entire 51-JAR runtime SHA-256: `a717f30a18158027ab0902ecddc7b0c830312a9bae57b178c789592fcc47ba14`; main JAR SHA-256: `a1307739a2c83c47acb1176c2ea61a2dd0e6ac28c499e9803e4f0ffd8bed5b3a`.

The Dockerfile retains the pinned Temurin/Jammy base and upgrades exactly three already installed packages, with no new or removed packages:

| Package | Before | After |
| --- | --- | --- |
| `libc6`, `libc-bin` | `2.35-0ubuntu3.14` | `2.35-0ubuntu3.15` |
| `perl-base` | `5.34.0-3ubuntu1.8` | `5.34.0-3ubuntu1.9` |

Seven advisory IDs disappear from the scan: CVE-2026-80489, CVE-2026-77117, CVE-2026-6791, CVE-2026-19542 and CVE-2026-6368 for glibc, plus CVE-2026-19487/CVE-2026-15534 for Perl. They produce twelve package matches because both libc packages match the five glibc records. Canonical documents the Jammy fixes in its [glibc advisory](https://ubuntu.com/security/CVE-2026-80489) and [two](https://ubuntu.com/security/CVE-2026-19487) [Perl advisories](https://ubuntu.com/security/CVE-2026-15534).

Application JARs, protocol, migrations, grants and alpha12 APK are unchanged from source `36cb4383d11c273df9f37a23f86dafbe300ec085`. Its [runtime review](../DEPENDENCY_REVIEW.md) retains the 106-test, automatic-game and 320-player capacity evidence. Those suites were not rerun for this OS-package-only change; their Windows timings do not establish container capacity.

## Scanner provenance and findings

Grype **0.119.0** comes from the [official immutable release](https://github.com/anchore/grype/releases/tag/v0.119.0). Linux AMD64 archive SHA-256: `3fa2dc4b924621ab65404cf08d0b8438d896d80ab949c9d5a4ca283c36004c9b`; executable SHA-256: `e02ba25615668c6bae03473e3c6493b6dfffc2e4e419f65f9bd62555ac10cd0e`. GitHub CLI 2.101.0 verified its signed release attestation before execution; the published checksum also matched. The Windows archive was independently verified the same way.

Database schema **v6.1.9** was built **25 September 2026 at 06:31:49 UTC**; archive SHA-256 is `04c01bc3418d0d53f64d041c2f28070a5ce1f814ee891b3f5d90102f09232dfe`. Its hash and age were validated. Both image scans use identical scanner/database bytes, without custom exceptions or VEX documents. Default kernel-header indirect-match filters remain; no suppressed matches were returned.

| Package/advisory matches | Prior | Patched |
| --- | ---: | ---: |
| High / Critical | 0 / 0 | 0 / 0 |
| Medium | 109 | 97 |
| Low | 29 | 29 |
| Negligible | 9 | 9 |
| Total matches / distinct IDs | 147 / 75 | 135 / 68 |
| Medium matches with available fixes | 12 | 0 |

All remaining matches are Ubuntu `deb` packages: 104 are classified `not-fixed`, 31 `wont-fix`. These are distribution database states, not proof of feature reachability or exploitability through Tambola. Full IDs, versions, fix states and source links remain in the raw reports. No blanket suppression or zero-vulnerability assertion is made. Host kernel/Docker, provider PostgreSQL, ingress and unknown/uncatalogued components are outside the image scan.

The first Windows archive scan failed on layer-cache filenames containing colons and produced no valid vulnerability report. Accepted scans ran the verified Linux executable in an isolated container: no network or Docker socket, read-only scanner/archive/database mounts and root filesystem, UID/GID 10001, capabilities dropped, no-new-privileges, 1 GiB non-executable temporary filesystem, 2 GiB memory and two CPUs. Reports used a separate writable directory. Both scanner containers were removed automatically; the failed Windows log remains separate.

## Container behavior

Run **`a3a1f75b98704403`** passed with this exact image, local-development mode false, four independent migration/runtime roles and fresh primary/journal PostgreSQL databases. It compared every image JAR with the standalone runtime and verified startup/grant guards, restricted gameplay, restart, deletion/retry, denied destructive SQL, protected metrics, worker-failure readiness and recovery. Both serving instances passed the packaged readiness probe.

The fixture verified UID/GID **10001:10001**, read-only root, **512 MiB memory / two CPUs**, and requested no-new-privileges, dropped capabilities and a 32 MiB temporary filesystem. It killed the first serving container and restarted in a new instance. The replacement handled SIGTERM within ten seconds, exiting **143** without forced termination. Exact container IDs are in the evidence; Windows PIDs 18668/22236 identify Docker CLI children. Owned containers/databases/roles were cleaned. This proves local behavior and idle shutdown after gameplay, not live-load draining, hosted ingress/TLS or physical networking.

## Repeat and maintain

On a Linux release/CI host, from `full-game/`, after building `:server:installDist`:

```sh
docker build --file server/Dockerfile --iidfile .test-workspace/service-image-id.txt .
python3 tools/test-container-advisories.py
bash tools/check-container-image.sh "$(cat .test-workspace/service-image-id.txt)" .test-workspace/container-review
```

Use a fresh evidence directory. The wrapper verifies the pinned scanner checksum, refreshes/validates its database and scans the exact local image. The gate rejects wrong image/version, stale/invalid database, filtered report, custom ignore or unknown severity. High/Critical findings and fixable Medium findings fail. Three focused tests pass; the actual prior report fails on twelve fixable matches and the patched report passes. The wrapper passed shell syntax checking; equivalent scanner/gate operations ran through the isolated Linux path. Full native-wrapper/remote CI execution has not occurred.

The workflow retains reports on failure. Review scanner version/hash/guard and Ubuntu package pins alongside the base digest; do not disable verification or silently drop unavailable pins. Deploy an exact image ID/digest with its report, and repeat the scan before release: this dated result is not perpetual clearance.

`releases/service-container-security-2026-09-26/` retains the image archive, reports, tool attestations, installed-package inventory and committed source identity, with `productionReady: false`. Hosted operations/provider recovery, build-tool review, wider fault/soak/device/accessibility/audio/editorial acceptance, signing and privacy/support/store work remain in the [full plan](../../docs/FULL_GAME_PLAN.md).
