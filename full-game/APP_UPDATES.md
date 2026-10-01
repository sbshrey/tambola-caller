# Internet Beta APK updates

Product name: **Tambola Jalsa**. All full-game updates are committed and pushed to **`shrey/tambola-jalsa`**. Use release titles like `Tambola Jalsa · Beta vN — short change description`; retain the asset naming contract below for updater compatibility. See [BRAND.md](BRAND.md).

The publicBeta variant (starting at version 40) checks the public GitHub releases API on its first eligible lobby visit per process. Settings offers a manual check. No push-notification service or game-server change is required. Existing installations must manually install version 40 once to gain this feature.

Only published (including prerelease) releases in `sbshrey/tambola-caller` with an asset named exactly `tambola-beta-v<versionCode>.apk` participate. Draft releases and legacy APK attachment names are ignored. The highest newer version among the most recent 20 releases is offered. GitHub must provide its SHA-256 asset digest. If there is no newer opted-in asset, the current version remains installed. Failures are quiet for automatic checks and visible for manual checks.

The lobby prompt has Download and Later. Downloads display progress and can be cancelled. After verification, Install update opens Android confirmation; if necessary, the user first allows this app to install packages in Android Settings and returns to tap Install update again. Denial or cancellation does not alter the game profile. Closing the progress dialog allows the download to continue; Settings can reopen it. Process death discards the in-memory offer and requires a fresh check/download. Interrupted partial files are overwritten on retry.

Prompts and installation are gated while an offline round, an online waiting/active room, an invitation, a pending operation, loading or rewarded ad is present. Downloads already explicitly started can complete in the background; they never install automatically. Long optional Settings content can scroll; no updater controls are added to the main lobby layout.

## Verification boundaries

HTTPS only; redirects restricted to GitHub's asset hosts; no game credentials sent. Feed response capped at 2 MiB, APK at 150 MiB, reads have timeouts and downloads have a five-minute limit. Exact size and SHA-256, package name, strictly newer version, advertised version, compatible minimum Android version and the installed app's complete signer set are checked before offering installation and again before launching it. The private cache APK is shared through a non-exported FileProvider with temporary read permission. Android performs final APK signature verification. Signing-key rotation is deliberately rejected until a reviewed migration supports it.

Install permission and provider exist only in the direct-distribution publicBeta variant, not ordinary release/debug builds. A future Play-distributed build must use the Play update mechanism instead.

## Preparing the next update

1. Increment `versionCode` and `versionName` in `app/build.gradle.kts`; keep the beta application ID and signing key unchanged.
2. Run relevant tests and `./gradlew.bat :app:assemblePublicBeta`. For a beta with opt-in Firebase diagnostics, use `-PtambolaFirebase=true`, verify the configured package and the manifest's default-off collection flags, and record that exact build command with the release. Never rebuild the same version with different flags after publishing its asset.
3. Run `./tools/prepare-app-update.ps1 -Apk app/build/outputs/apk/publicBeta/app-publicBeta.apk -PreviousApk <previous-phone-apk>` from `full-game`. The tool verifies both signatures and rejects incompatible identities, non-increasing versions and reuse of a version with different bytes.
4. Attach the prepared `tambola-beta-vN.apk` to a published GitHub release in this repository. Drafts do not notify users. Keep the newest beta within the most recent 20 published releases. Compare GitHub's asset digest against the generated `update.json` and do not replace an existing version asset.
5. Verify detection and installation on an older updater-enabled app, confirming profile persistence. Publish rollback fixes with a higher version code; do not downgrade.

No release is automatically published by the preparation tool. This keeps a build or test run from notifying every installed beta app.

Project delivery preference: after validating an APK update, commit and push its corresponding source and publish the versioned APK in a GitHub prerelease. Return the public download link. Publishing is part of completing an update; the preparation script remains read-only with respect to GitHub so intermediate builds cannot notify users accidentally.

## Permanent hostname update (v42)

Public beta v42 keeps the saved publisher profile identity and invitation format while connecting directly to `https://play.thefinxperts.com`. It no longer fetches the temporary server directory for game traffic. The old quick tunnel and feed remain available to v40/v41 during migration. Publishing the immutable `tambola-beta-v42.apk` release makes it discoverable by their existing updater; users must confirm download and Android installation. This is a foreground update check, not a push notification or silent installation.
