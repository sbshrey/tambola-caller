# Publisher consent configuration check — 28 September 2026

The user reported that AdMob's app status is **Requires review or Getting ready**, and was unsure whether a consent message was published. Browser control was unavailable with `unsupported Codex auth method: apikey`, so the actual publisher configuration was checked through Google's Android UMP SDK instead.

The private test uses the real beta package and publisher app ID, only on a debuggable APK and the owned API30 emulator. It resets that emulator's consent state and forces EEA test geography, then requests consent information and attempts to load a form. It does not request or display advertising, contact the game service, award coins, or collect Firebase diagnostics.

## Observed result

- Debug app and instrumentation APKs compiled successfully.
- The native configuration assertion failed: UMP returned **error 3**, category **publisher_misconfiguration**, during `consent_update`.
- Google's SDK explanation was: **no form(s) configured for the input app ID**. The app ID matched the supplied publisher ID. This is a console configuration failure, not a passing consent test.
- `publisher-consent-check.json` records the bounded diagnostic. `instrumentation.txt` retains the failed assertion. No raw device identifier or callback credentials are retained in this review.
- The tested alpha39 public APK was restored on the owned emulator afterward. Its SHA-256 remains `94eeb08414986517c652285acaa6070068720076183c4a17aad9a0952d70826c`. The temporary emulator was then stopped; the PC game host and public tunnel were left running.

## Next steps

The user supplied the public support/privacy address `sbshrey@gmail.com`; the publisher name uses the existing public handle `sbshrey`. The new static privacy page covers the current Internet Beta, opt-in Firebase diagnostics, conditional rewarded ads, actual local retention/backup limitations and in-app/email deletion requests. It contains no scripts or form collection.

Publish the page at `https://sbshrey.github.io/tambola-caller/privacy/`, then use that URL when publishing a European regulations message for the matching app in AdMob. Rerun the same consent check after the console update. Publishing the page alone does not create an AdMob message or approve the app.

For app readiness, **Requires review** needs a supported store listing linked and submitted; **Getting ready** means Google's review/account verification is underway. The exact label remains to be distinguished. Do not call the overall advertising activation complete while the consent assertion, account readiness and completed-ad wallet delivery remain unverified.
