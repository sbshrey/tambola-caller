# Offline game audio

Tambola Jalsa packages its number voices, an original instrumental loop and four short effects. Playback never calls an AI service, needs no microphone and carries no developer API key. Android's media volume still applies on top of the three app volumes.

## Controls and event rules

- Music defaults off at 45% app volume. It plays on menus, setup, tutorial, lobby and a playing table; paused/finished tables and result/history/badge pages stop the loop. Leaving the foreground releases all players and audio focus.
- Automatic number speech defaults on at 100%. The existing 270 English/Hindi/Hinglish recordings remain available offline. Turning automatic speech off does not disable an explicit Hear again; voice volume zero silences both.
- Effects default on at 60%. A committed offline deal plays a short arpeggio, an eligible manual mark/unmark plays a pluck, and a call without automatic speech plays a short bell. A newly verified prize plays one winning phrase, after the number voice when enabled. Multiple awards on the same call produce one phrase. Marks cannot interrupt a voice or a winning phrase.
- Undo, restoring saved games, opening results, and online reconnect/catch-up do not play announcements or wins. Online cues use the existing accepted-live-call decision; duplicate/stale receipts cannot trigger another cue. Online marking plays only after encrypted persistence succeeds. Online dealing currently has no separate sound.
- All settings are stored in DataStore. Volumes are bounded integers, and sliders commit on release. Old settings acquire the defaults above. The app identifies number voices as AI generated and the music/effects as original synthesis.

## Playback and interruptions

`GameAudio` is one application-context mixer confined to Android's main thread. It has at most three asynchronous MediaPlayers: music, voice, and one effect. Table-specific `CallAudio` handles cancel only their own one-shots. Completion, prepare, error and focus callbacks check their current slot/request identity; obsolete callbacks cannot start a replaced sound.

The mixer owns a single audio-focus request, requesting sustained focus for ambient music and transient may-duck focus for isolated cues. Music drops to 15% of its selected gain while a voice is preparing/playing, or 50% during a winning phrase; it returns to the selected gain afterward. It does not queue old calls. Audio failure pauses sound and leaves game state intact.

A transient external focus interruption releases one-shots and suspends ambient music. Only music resumes on focus gain. If music is muted while waiting, the mixer abandons focus and exposes Resume sound; it cannot remain stuck waiting for a callback from an abandoned request. Permanent loss or denied focus requires Resume sound/Hear again, or an ordinary return to the foreground. A noisy-route notification stops all sound and requires explicit Resume sound/Hear again even after backgrounding; it cannot automatically fall back to the phone speaker. The Activity enables playback only while resumed. There is no background playback service or notification.

These rules follow the platform [audio-focus guidance](https://developer.android.com/media/optimize/audio-focus) and [MediaPlayer state/resource guidance](https://developer.android.com/media/platform/mediaplayer/state-resources). Tests must distinguish real focus/player behavior from an injected route callback and from actual hardware routing.

## Original assets and reproducibility

The original score **A Little Game Night** is a 24-second, eight-bar instrumental at 80 BPM. `tools/compose-sounds.mjs` synthesizes the pad, bass/plucked notes and bell motif directly, with no third-party samples, borrowed melody, generated-model audio or paid generation. It also creates `mark`, `call`, `deal` and `win` cues. Masters are 44.1 kHz, mono, 16-bit PCM WAV, kept uncompressed in the APK for asset file descriptors. The manifest records provenance, duration, byte count, peak, RMS, loop boundary and SHA-256 for every file.

```powershell
node tools/compose-sounds.mjs --check  # Reproduce in memory and compare every byte, including the manifest.
node tools/compose-sounds.mjs          # Deliberately regenerate the named sound directory.
```

Gradle's `prepareSounds` checks the exact required clip set, file lengths and hashes. CI also runs the reproducibility check. The loop wraps note tails across its boundary; its boundary delta is 0.001595 of full scale, peak 0.17726 and RMS 0.038403. These numerical checks establish deterministic, unclipped PCM data; they do not establish pleasant listening or a gapless device implementation. Source/asset changes require a new manifest and listening acceptance.

## Acceptance still required

The device tests observe actual MediaPlayer preparation, playback, completion, gains, focus ownership, owner cancellation, lifecycle release and saved UI controls. Invoking the noisy-route callback tests the policy, not a real headphone/Bluetooth disconnect. Final acceptance still needs listening on headphones and phone speakers, speech/music balance and pronunciation, loop seam/gap audition, latency, calls and Bluetooth/device routing, and interruption behavior on the target API/OEM matrix. Exact executed results belong in the milestone validation report.
