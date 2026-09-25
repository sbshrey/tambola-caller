# Alpha 07 validation: original music and game audio

Date: 26 September 2026. Branch: `shrey/full-tambola-game`. This is a debug-signed internal alpha. The full production goal remains active.

## Delivered behavior

- An original 24-second instrumental loop and four original effects, packaged offline with verified manifests. No external samples, runtime generation, new paid generation or developer API key is used. Music defaults off. The existing 270 AI number voices remain available in English, Hindi and Hinglish.
- Separate saved voice, music and effects volumes; automatic speech and explicit Hear again have clearly explained behavior. The native controls are scrollable and expose labelled slider semantics.
- One Application-owned mixer and audio-focus owner, with bounded music/voice/effect players and owner-scoped cancellation. Music softens while numbers play; new verified awards have a single short chime after the voice. Deal/mark/call/win cues follow committed game changes; undo, restored games and online catch-up stay silent.
- Foreground-only playback, resource release, audio-focus interruption handling and an explicit resume action after a route disconnect. Transient focus gain resumes only music. Muting music during a transient interruption abandons the request and exposes Resume sound instead of waiting indefinitely for a callback that can no longer arrive.

[Audio controls and provenance](AUDIO.md) document exact event/interrupt rules, deterministic asset generation and remaining listening acceptance.

## Candidate and build evidence

Candidate `Tambola-Together-0.7.0-alpha07.apk`: **41,717,335 bytes**, SHA-256 `1827e222fcf92750a59601d5c334df3996bbc801b3ce3f2313753666f1b84f06`. Package `io.github.sbshrey.tambola.game`, version code 7, minimum API 26, target API 36. Installed APK readback matches. Signature Scheme v2 verifies with the prior Android debug certificate, SHA-256 `55546680e8d8f41fb68a37c6f3c494ac7c0da7dec62e19f4cc215b8ba1200d6c`.

Final debug APK/instrumentation/JVM/lint build passed in **1 minute 28 seconds**. The five existing app JVM tests pass. Debug lint has zero errors and one existing unsuppressed KAPT-to-KSP warning. The Application-owned mixer removes the static-context warning from the initial implementation. Normal dependency verification remains enabled.

Final optimized release APK/AAB and release lint build passed in **2 minutes 17 seconds**, with zero lint errors and the same one KAPT-to-KSP warning. The release build uses `https://rooms.example` solely to validate packaging/R8; it is unsigned and is not distributed as production or hosted multiplayer. An earlier overlapping release build was cancelled after resource contention slowed the emulator; that incomplete attempt is excluded from release acceptance.

`node tools/compose-sounds.mjs --check` reproduces all five PCM files and the manifest byte for byte. Build imports verify their lengths/hashes and all 270 voices. The APK's five sound files match their manifest and remain uncompressed for asset descriptors. The generated loop has a boundary delta of 0.001595, peak 0.17726 and RMS 0.038403 of full scale. These are numeric/reproducibility checks, not listening approval. A packaged-file scan found no matching OpenAI-key/private-key patterns; it is a bounded pattern check, not a general security audit.

## Device acceptance

The exact final candidate passed **22/22 tests in 275.634 seconds** on the dedicated Android 11/API 30 emulator against real local Netty/PostgreSQL and the existing response-loss fixture. It includes the existing 17 gameplay/UI/storage cases and five new audio cases.

The new cases use actual MediaPlayer preparation, playing/completion state, applied gains and real AudioManager focus requests. They cover voice ducking and queued win completion; cancelling asynchronous preparation and cancelling another owner; zero/muted channels and explicit replay; a competing transient focus owner; mute-during-interruption recovery; background/recreation release without replay; and saved independent native slider controls. An offline integration journey observes committed deal, ordinary call, custom-prize win and manual-mark cues, plus silent invalid marks, undo and restoration.

The noisy-route policy is tested by invoking the same callback used by `ACTION_AUDIO_BECOMING_NOISY`. That is **not** a physical headphone or Bluetooth disconnect test. Player state and gain assertions do not establish what a listener hears or prove gapless/low-latency output.

The final saved-controls journey also passed at **360dp / 200% text in 7.493 seconds**. All three labelled sliders and music/effects switches remain reachable; independent values survive recreation. Four normal/enlarged native screenshots were visually reviewed. Original font, density, night mode and animation settings were restored and verified. Installed APK readback still matches after acceptance. Owned local service/proxy sessions were stopped and their ADB mappings removed; the original emulator and isolated PostgreSQL cluster remain available. Exact settings, installed readback, final passing logs, screenshots, manifest/signature reports and checksums accompany the APK.

A prior 22/22 run in 380.258 seconds used the candidate before the final mute-during-interruption fix. It is retained as development evidence and excluded from final-candidate acceptance. The initial four-case audio pass likewise predates Application ownership and the fifth integration case. No test failures are hidden by combining results from different candidates.

## Repeatable checks

From `full-game/`, with JDK 17 and Android SDK 36:

```powershell
node tools/compose-sounds.mjs --check
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --console=plain
node tools/android-smoke.mjs --online --fault-proxy --label alpha07-full-final
node tools/android-smoke.mjs --class io.github.sbshrey.tambola.game.GameAudioTest#savedIndependentSoundControlsRemainUsableAfterRecreation --label alpha07-audio-large
.\gradlew.bat :app:assembleRelease :app:bundleRelease :app:lintRelease -PtambolaApiUrl=https://rooms.example --no-daemon --console=plain
```

The full suite requires the documented dedicated emulator and local service/proxy mappings. Large-text testing additionally sets 360dp width and 200% text, then restores the captured settings. Run release optimization after device tests on a resource-constrained host.

## Evidence boundaries

The service, protocol, ticket/rule engine and encrypted-persistence implementation are unchanged. Their previous JVM/service and cold-process/two-native-client results remain separately scoped historical evidence. This milestone's full Android run exercises the changed game integration against the actual local service; it does not repeat every backend fixture.

Human listening, pronunciation/mix balance, loop seam/gap audition, phone calls, real Bluetooth/headphone routes, API 26/35/36 and physical-OEM acceptance remain open. Finite visual celebrations, avatars, Hindi UI, full TalkBack/non-color/performance acceptance, larger deletion/backup cases, hosted TLS/operations and production signing remain in the [full plan](../docs/FULL_GAME_PLAN.md). No remote CI, cloud deployment or public release is claimed.
