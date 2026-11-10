# Tambola Keyboard · Google Play release worksheet

Draft for version 1.5.0 (version code 15). This file is a release handoff; Play Console declarations, signing setup, and submission still need to be completed by the account owner.

## Listing draft

**App name:** Tambola Keyboard

**Short description:** Call Tambola numbers and share winner updates from your WhatsApp chat.

**Full description:**

Tambola Keyboard helps one host run a Tambola game from a WhatsApp chat. Players use paper tickets; the host calls numbers and checks claims.

- Draw numbers from 1 to 90 without repeats.
- Insert an emoji number into an empty chat draft, then review and send it yourself.
- Track players, prizes, winners, Bogies, and game results on your phone.
- Share number, board, and results pictures or number audio clips when you choose.
- Hear bundled English, Hindi, and Hinglish number calls offline.
- Practise without changing the live game or sending a message.

Tambola Keyboard is an optional Android keyboard. Android asks you to enable it and shows its standard keyboard warning. While selected, Android connects the keyboard to the focused text field. This app checks the active draft to protect unsent text; it does not save chat drafts. Optional winner dictation uses Android's configured speech recognition service and may send audio to that provider. Microphone access is not needed for calls, manual winner selection, or other keyboard features. See the privacy policy for details.

No account, ads, or in-app purchases. Sharing is always started by you; choose the receiving app and recipient, review the content, and send it yourself.

## Release identity and signing gate

- Package: `io.github.sbshrey.tambola.keyboard`.
- Current source release line: version code 15, version name 1.5.0.
- The local 1.4.7 APK uses a new release key and cannot update the older 1.3.1 APK. Before creating a Play listing or uploading an update, the account owner must check whether this package already exists in Play Console and decide how existing installations will be supported.
- If this is the first Play listing, enroll in Play App Signing and choose the signing key deliberately. Keep the upload key separate from the Play app-signing key where appropriate. Do not upload the local APK-signing secret as an app artifact or commit it.
- Do not distribute a Play-signed update as a direct APK unless its signing identity supports existing direct-install users.

## Privacy and Play Console declarations

- Policy page: `privacy-policy.html`, intended hosted URL `https://sbshrey.github.io/tambola-caller/android-keyboard/privacy-policy.html`.
- The app has no account, analytics, ads, app backend, or Internet permission. Game data is local and excluded from backup/device transfer.
- The optional microphone flow calls Android's configured speech recognition service. That provider may process audio on-device or on remote servers. Confirm the provider's current behavior and complete Data safety answers to match the exact shipped app and provider; do not assume that “no Internet permission” means no audio can leave the device.
- The microphone permission is preceded by an in-app explanation and a user action. Keep this flow and the Help privacy link in release builds.
- Complete content rating, target audience, ads, app access, government/financial features, and other App content declarations in Play Console based on the intended audience and final functionality. No reviewer account is needed; reviewers must enable the keyboard in Android Settings to try it.
- Confirm that the Play developer contact details are monitored and that the policy inquiry mechanism remains usable. The repository issue tracker is public by default; users are told not to post sensitive details.

## Build and verification checklist

- Completed on Android 16 / API 36 with Java 20 (Java 17 source compatibility), SDK 36, AGP 8.13.2, and Gradle 8.13: 62 JVM tests passed; debug, test, release, AAB, and debug/release lint tasks passed. Ten selected API 36 UI tests passed. See [VALIDATION.md](VALIDATION.md) for exact scope.
- To create a signed local AAB and checksum, set `TAMBOLA_KEYSTORE_PATH` and `TAMBOLA_KEYSTORE_PASSWORD_FILE`, then run `bash ./build-playstore.sh` from this directory. The helper uses the configured `tambola` key alias and writes under the ignored `releases/1.5.0/` directory. Register the matching certificate as the Play upload key before upload.
- Before release, run the full API 36 instrumentation suite and repeat compatibility checks on API 35. Test install, enable/select keyboard, practice, number call and undo, manual winner flow, dictation, share chooser, saved-game upgrade, and new-round reset. Review at large font scale, landscape, and a large-screen window. Check actual speech recognition and WhatsApp handoff on a physical phone.
- `bundletool validate` and `jarsigner -verify` passed. The manifest was checked for package, code/name, target API 36, microphone/IME permissions, and absence of debug-only components.
- Use Play Console's app signing and upload key. Verify the uploaded bundle against the chosen Play listing; a local key does not settle the identity mismatch with older direct APKs.

## Device screenshots still to capture

- Capture current, clean-device screenshots on API 36: setup, active keyboard in a chat-like editor, called-number board, and winner/results sharing. Use sample names and numbers only; do not include personal WhatsApp content.
- Confirm assets comply with the current Play Console text, device, and content requirements when creating the listing.

## External owner actions

1. Check package ownership and existing Play Console state for `io.github.sbshrey.tambola.keyboard`.
2. Choose the Play app-signing and upload-key strategy, considering the old direct APK signature.
3. Review the policy and Data safety responses against the configured speech provider and desired supported countries.
4. Set the final developer display name and monitored contact details, capture listing assets, complete declarations, and submit the AAB for review.
