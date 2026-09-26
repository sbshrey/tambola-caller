# Build-tool review — 26 September 2026, in progress

This review is separate from the [runtime dependency scan](DEPENDENCY_REVIEW.md) and [container scan](server/CONTAINER_DEPENDENCY_REVIEW.md). It is not yet complete. No build-tool update or rebuilt APK is claimed by this document.

## Confirmed wrapper finding

The checked-in wrapper currently selects Gradle **8.13**, with distribution SHA-256 `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78`. The build selects AGP **8.13.2**, Kotlin/Compose compiler **2.2.21**, and JDK **17**. Its repositories are Google Maven, Maven Central and the Gradle Plugin Portal for plugins; project repositories are rejected. The dependency-verification file contains SHA-256 checksums and enables metadata verification, with no trusted-artifact bypass configured there.

Gradle's maintainers list two High advisories affecting versions below 8.14.4 (also 9.0.0–9.2.1):

| Advisory | Problem | Maintainer fix |
| --- | --- | --- |
| [GHSA-w78c-w6vf-rw82 / CVE-2026-22816](https://github.com/gradle/gradle/security/advisories/GHSA-w78c-w6vf-rw82) | Repository fallback after an unresolvable host can resolve artifacts from an unintended repository | 8.14.4 or 9.3.0+ |
| [GHSA-mqwm-5m85-gmcv / CVE-2026-22865](https://github.com/gradle/gradle/security/advisories/GHSA-mqwm-5m85-gmcv) | Fallback after repeated repository response failures has a similar resolution risk | 8.14.4 or 9.3.0+ |

Both advisories identify strict repository filtering or dependency verification as mitigations. The checked-in checksum verification provides that control for approved artifacts; this review does not infer compromise or waive a patched-wrapper update. The advisories concern dependency resolution during builds, not the installed game's room protocol.

## Selected next update and verification

The official [version feed](https://services.gradle.org/versions/all), checked 26 September 2026, lists **8.14.5** as the newest stable 8.14 patch. Its [release notes](https://docs.gradle.org/8.14.5/release-notes.html) include the preceding security fixes and two subsequent build fixes. Remaining on the 8.x line limits migration scope. [AGP's compatibility table](https://developer.android.com/build/releases/agp-8-13-0-release-notes) lists Gradle 8.13 and JDK 17 as minimums; this does not replace testing the chosen pair.

Official HTTPS checksum responses retained locally in `.test-workspace/gradle-8.14.5-official-checksums.json`:

- Distribution: `6f74b601422d6d6fc4e1f9a1ab6522f642c2fdcbc15ae33ebd30ba3d7198e854` ([source](https://services.gradle.org/distributions/gradle-8.14.5-bin.zip.sha256)).
- Wrapper JAR: `7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172` ([source](https://services.gradle.org/distributions/gradle-8.14.5-wrapper.jar.sha256)).

These are published expected hashes, not evidence that new binaries have been downloaded, verified or executed. The sustained automatic service workload is running against the existing candidate, so avoid competing builds/scans and changing its library directory until it finishes.

After that measurement:

1. Verify the 8.14.5 distribution and update the wrapper, preserving the distribution checksum and HTTPS validation. Verify generated wrapper bytes against the official hash.
2. Resolve an exact inventory of buildscript/compiler/annotation-processor and Gradle-distribution dependencies. The historical verification file alone is not a selected build-tool inventory. Run dated advisory checks and assess any findings in their build-only context.
3. Build/test the service, domain and client, then assemble/lint/test the Android candidate and optimized unsigned APK/AAB with existing strict dependency verification. Record new artifact identities and differences; retain existing immutable packages.
4. Check CI action/tool pins and wrapper verification, and document any remaining findings or compatibility issues. A local build cannot establish a remote CI run or production signing.

Until those steps finish, the build-tool review remains an open release gate.
