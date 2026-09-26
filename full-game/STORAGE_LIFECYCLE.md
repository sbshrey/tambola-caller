# Offline database lifetime

`TambolaApplication` owns one lazy Room database for the process. Activity-scoped `GameViewModel` instances use it but never close it. The database contains the same version-1 `rounds` table and version-3 payloads as alpha09; this change requires no migration. Separately opened database instances used by explicit test fixtures remain owned and closed by those fixtures.

This follows [Android's single-process Room guidance](https://developer.android.com/training/data-storage/room). More importantly, the previous ownership failed under a real Activity lifecycle test: `onStop` started an asynchronous pause save, then `onCleared` closed that ViewModel's database while the cancelled operation was still releasing its connection. Rapid reopening also constructed a second independent pool against the same file.

## Reproduction

The dedicated API 35 emulator reproduced the failure on iteration 39 (the 40th launch) of `StorageLifecycleTest`. The test creates a round, closes the Activity, reopens and verifies its saved identity, deletes all rounds, and creates the next round. It checks live state errors and the history observer, not just whether an Activity opens.

The diagnostic application SHA-256 is `0352fe1dd6e46eaf3f4e96f9917ce0af6af1e239da39940f0c1f3239f7f4e0b6`; its test APK is `f787199db54aa2610da2c90f5b7ad717f173c99ebf2a2d6285b4632216b476d2`. This is alpha09 behavior with bounded debug diagnostics, not the immutable original alpha09 release. The test failed in 65.121 seconds while awaiting deletion, with the new ViewModel reporting `error_load_history`.

The outgoing save logged `android.database.SQLException` from `SupportSQLiteConnection.prepare` line 37; the next history observation logged `SQLiteDatabaseLockedException`. Inspection of the exact resolved `sqlite-framework-android:2.6.2` binary maps line 37 to its closed-connection branch. Earlier loop iterations also showed `JobCancellationException` during Activity shutdown. The original alpha09 API 35 suite's save/deletion failures remain recorded separately in [the matrix report](DEVICE_MATRIX_VALIDATION.md); the diagnostics do not retroactively add missing exception evidence to that earlier run.

An initial stress-fixture attempt moved the Activity to CREATED and then closed it. It stalled inside AndroidX Test's `InstrumentationActivityInvoker.startEmptyActivitySync`, was explicitly stopped, and is not an application-test pass. The final fixture closes the resumed Activity directly, naturally invoking `onStop` and destruction.

## Error handling and regression

Restore, create, save and delete operations rethrow coroutine cancellation. Normal ViewModel teardown therefore cannot convert cancellation into a saved-game error. Genuine failures still show localized errors. Debug-only diagnostics record bounded exception types and code locations; exception messages, SQL, player names, payloads and credentials are omitted. Release builds do not emit this diagnostic.

The lifecycle fixture is opt-in, requires a debug emulator and is excluded from ordinary smoke runs. Run it only on a dedicated `tambola_full_game_*` AVD with fictional data and the matching APKs installed:

```powershell
adb -s <serial> shell am instrument -w -e tambolaStorageLifecycle true -e class io.github.sbshrey.tambola.game.StorageLifecycleTest io.github.sbshrey.tambola.game.test/androidx.test.runner.AndroidJUnitRunner
```

Check the JUnit result: `am instrument` can return shell status zero when tests fail. The fixture intentionally replaces local rounds and settings. It does not establish physical-device or power-loss durability. Fixed-candidate acceptance is recorded separately in its release validation report.
