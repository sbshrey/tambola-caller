# Publisher consent configuration check — 28 September 2026

## Current result: published form verified

After the user reported publishing the message, the same native check was rerun at approximately **08:57 UTC on 28 September 2026**, using the unchanged private diagnostic APK and the actual beta package/app ID. **One test passed.** Google UMP successfully loaded the configured form, with `consentRequired=true` under forced EEA test geography. The earlier missing-form error is resolved.

The new evidence is preserved separately in [`published-0857Z/publisher-consent-check.json`](published-0857Z/publisher-consent-check.json) and [`published-0857Z/instrumentation.txt`](published-0857Z/instrumentation.txt). This proves consent form delivery on the owned Android emulator. The test did not display the form, choose consent, request an ad or award coins.

After verification, the exact published alpha39 APK was restored: version code 39, version `0.39.0-alpha39-internet-beta`, SHA-256 `94eeb08414986517c652285acaa6070068720076183c4a17aad9a0952d70826c`. The temporary emulator was stopped. No server, tunnel or public APK changes were needed.

The remaining gates are the exact AdMob app readiness status and a completed rewarded-ad flow with a valid server callback and exactly 1,000 credited coins. Live ads remain disabled in the published alpha39 APK. A consent form passing this check does not establish app approval or reward delivery.

## Earlier configuration failure

The user reported that AdMob's app status is **Requires review or Getting ready**, and was unsure whether a consent message was published. Browser control was unavailable with `unsupported Codex auth method: apikey`, so the actual publisher configuration was checked through Google's Android UMP SDK instead.

The private test uses the real beta package and publisher app ID, only on a debuggable APK and the owned API30 emulator. It resets that emulator's consent state and forces EEA test geography, then requests consent information and attempts to load a form. It does not request or display advertising, contact the game service, award coins, or collect Firebase diagnostics.

### Initial observed result

- Debug app and instrumentation APKs compiled successfully.
- The native configuration assertion failed: UMP returned **error 3**, category **publisher_misconfiguration**, during `consent_update`.
- Google's SDK explanation was: **no form(s) configured for the input app ID**. The app ID matched the supplied publisher ID. This is a console configuration failure, not a passing consent test.
- `publisher-consent-check.json` records the bounded diagnostic. `instrumentation.txt` retains the failed assertion. No raw device identifier or callback credentials are retained in this review.
- The tested alpha39 public APK was restored on the owned emulator afterward. Its SHA-256 remains `94eeb08414986517c652285acaa6070068720076183c4a17aad9a0952d70826c`. The temporary emulator was then stopped; the PC game host and public tunnel were left running.

### Privacy page publication

The user supplied the public support/privacy address `sbshrey@gmail.com`; the publisher name uses the existing public handle `sbshrey`. The new static privacy page covers the current Internet Beta, opt-in Firebase diagnostics, conditional rewarded ads, actual local retention/backup limitations and in-app/email deletion requests. It contains no scripts or form collection.

The privacy page was published at `https://sbshrey.github.io/tambola-caller/privacy/`. Pages run `36398350298` completed successfully for main commit `f04a54718da41e31452ff1f049a36aa598a811c5`. Anonymous HTTPS reads of both privacy assets and both changed invitation assets returned HTTP 200 and matched the reviewed local contents. The main-branch comparison changed only those four files, preserving the rest of the website. See `page-publication.json` and `public-page-check.json`.

That URL was supplied for publishing a European regulations message for the matching app in AdMob. The user subsequently reported publication and the native check above now passes. Publishing the page or message does not approve the app for advertising.

For app readiness, **Requires review** needs a supported store listing linked and submitted; **Getting ready** means Google's review/account verification is underway. The exact label remains to be distinguished. Do not call the overall advertising activation complete while account readiness and completed-ad wallet delivery remain unverified.
