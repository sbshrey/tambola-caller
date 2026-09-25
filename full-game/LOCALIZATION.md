# English and Hindi interface

Settings → App language selects Device language, English or हिन्दी. This controls menus, validation, accessibility text, standard prizes, structural custom-rule descriptions, results and sharing. Number voice remains an independent English/Hindi/Hinglish setting; all recorded voices and the interface work offline.

Player names, custom prize titles, room codes, stored computer names and protocol IDs retain their original values. A deleted online identity is service-authored `Deleted player`; the Hindi deletion explanation names that marker explicitly. Standard Tambola numbers remain 1–90 in both interfaces so tickets, the caller board and recorded calls agree. Dates use the selected interface locale.

## Implementation

Android string/plural resources live in `app/src/main/res/values` and `values-hi`. `GameText` maps enum values and structural rule data to those resources. Shared domain rules and wire values do not depend on a language or Android context. `UiMessage` carries resource IDs and arguments through retained ViewModels and setup validation; text resolves when displayed, including after language changes. Online error codes map to local messages, with a bounded generic fallback for unknown service errors.

`MainActivity` uses AppCompatActivity and AppCompatDelegate per-app locales. Locale configuration declares English and Hindi. AppCompat stores the choice on older Android versions; Android 13 and later provide the platform per-app locale integration. This follows [Android's per-app language guidance](https://developer.android.com/guide/topics/resources/app-languages). The dependency is pinned to AppCompat 1.8.0 with SHA-256 verification metadata, as listed in the [official release notes](https://developer.android.com/jetpack/androidx/releases/appcompat).

Changing the language recreates the Activity. ViewModels retain active game and unsaved editor state. Existing lifecycle handling pauses local calls and stops audio while the Activity is unavailable; changing language must never draw, replay a win, reset marks or change the recorded caller preference. Devanagari headings avoid expanded Latin letter spacing.

## Validation

Run `python tools/check-localization.py` to compare resource keys, plural forms, placeholders and Unicode integrity. The CI build runs this check. `LocalizationTest` covers localized rule formatting, retained messages, unsaved setup/editor state, caller independence, active-round identity/ticket/mark preservation, Hindi claim inspection and anonymous result sharing. Test screenshots support separate visual review; test success alone is not a typography or translation review.

For persistence beyond Activity recreation, install the candidate and its instrumentation APK on a dedicated `tambola_full_game_*` emulator, then run:

```powershell
node tools/android-locale-recovery.mjs --serial emulator-5582 --label locale-recovery --apk releases/0.9.0-alpha09/Tambola-Together-0.9.0-alpha09.apk
```

The opt-in fixture creates a marked practice round, selects Hindi independently of the Hinglish caller, and waits for the host to terminate the actual app process. Verification requires a different process and the exact saved round. On API 33+, the driver also changes the platform's app language to English while the app is stopped, then verifies the platform locale, declared language choices, interface and saved round. It restores the original app locale and system animation scales, retains sanitized logs/screenshots under `.test-workspace/<label>`, and never exports the private round witness. It changes test game data, so its emulator guard is intentional. The general smoke runner excludes this externally coordinated fixture.

This is an authored Hindi translation. Native-speaker editorial review, TalkBack review and the supported API/device matrix remain release acceptance work. See `ALPHA09_VALIDATION.md` for checks actually executed against a particular APK; this document describes behavior and test intent rather than asserting that all acceptance gates have passed.
