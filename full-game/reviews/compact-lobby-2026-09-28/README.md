# Compact lobby review

Candidate based on `9e133be` on `codex/internet-beta`. These are local Android changes; alpha39 and the independently running public host are unchanged.

## Design

- The main lobby has no scroll container. Room type, six ticket choices, total cost, Play and Friends fit together in one panel.
- Explanatory rules moved to the header help button; optional rewarded coins remain in the wallet dialog. The main screen retains a short, honest practice/invited-player disclosure.
- Large text uses a shorter welcome heading. Narrow large-text headers use the brand initial with the full accessible app name.
- Results show winnings and replay controls immediately; the individual prize breakdown opens separately.
- Friends waiting rooms show the member count and Start, with the full roster available on tap. Fifty members no longer create fifty rows in the lobby.
- Pending operations and session/storage recovery show the saved room code and recovery controls instead of placing those controls after the full waiting room.
- The power picker explains discarded tickets, active Auto-Dab, armed bonuses, already-used powers and empty inventory. Its dock can wrap; finished rounds cannot activate a power.

Optional rules, prize breakdowns, player lists and account details retain scrollable dialogs for long content and accessibility. The purchase, replay and recovery lobby itself does not scroll.

## Validation

The dedicated `tambola_lobby_review` API30 emulator at `emulator-5580` was created for this chat from an already installed image. No other emulator, game host or tunnel was stopped or reconfigured. Tests use local fictional fixtures, not public purchases.

- Android debug build and instrumentation APK compile.
- Final Android debug lint passes.
- 21 Android local unit tests pass, including power-state transitions after activation, expiry, a win and a false claim.
- English/Hindi resource check passes for 860 resources.
- Native coverage includes English/Hindi, portrait/landscape at 200% text, six-ticket selection, cost, one-tap play, pending recovery, result shares/replay, a 50-player friends room, power activation/discard, wallet rewards/help and connection recovery.
- The initial run found a test resource-provider issue for Hindi dialogs; fixtures now explicitly provide localized `LocalResources`. Secondary checks exposed a real portrait recovery overflow, fixed by showing compact recovery content.
- Combined native run: 26 tests pass (`native-final-tests.txt`). After the last guard to avoid a disconnected message during a live pending operation, all five affected recovery/50-player checks pass on the final APK (`native-recovery-final.txt`).

Final local debug APK: `app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `f5185166ecc0b8758c6cfb534c413bc6a5ccd76e73277d4fece6b9987b736471`. The owned emulator was shut down after validation; its AVD remains available for future checks.

See `native-final-tests.txt` for the earlier instrumentation result and the PNGs for emulator screenshots. These do not establish physical-device behavior or public-release acceptance. The APK is a local debug candidate, not a replacement published release. Advertising activation and store publication remain outside this change.

## Final follow-up verification

The current 18-case lobby run passed 17 cases and exposed a landscape friends-room Leave control below the viewport (`native-current.txt`). Leave now lives with the roster/Start controls, and the affected 50-player case passes on the rebuilt APK (`native-waiting-final.txt`). All four English/Hindi portrait/landscape no-scroll cases at 200% text passed in the current run. Build and lint pass after the fix.

Latest debug APK SHA-256: `91c43e1d06b4d1e715299cac5b2d337655acaa32e37404172c165313d28c0af9`. This supersedes the earlier candidate hash above. The dedicated emulator was stopped after verification.
