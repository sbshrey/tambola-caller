# Presentation and asset direction

The visual direction is a warm game-night table: ink, ivory paper, amber calling balls and jade marks. Screen colors use explicit Material 3 roles in both themes. Ticket paper and printed number-ball artwork use a fixed ink/paper palette so their state is consistent between themes.

## Implemented appearance

- Settings offers System, Light and Dark. The preference is stored in DataStore; old or unknown values fall back to System. Changing it affects offline/online screens, full-screen editors, sheets, buttons and system-bar icon contrast without replacing the current round.
- Light uses cream page backgrounds, warm neutral cards and deep amber/jade text. Dark uses navy surfaces and brighter amber/jade accents. Standard controls inherit the same palette instead of mixing fixed dark fills with light text.
- The home illustration is an original native Canvas composition of ticket geometry, calling balls and table rings. It scales with layout width, needs no bitmap download and is omitted from accessibility traversal. It is decorative, not a ticket that can be played.
- A committed number is exposed immediately. Its ball settles with a 420 ms translation/rotation/scale animation. Reduced motion snaps to the final appearance; Compose also applies Android's animation duration setting. The animation does not determine, delay or repeat a call, and does not loop.
- Existing badge reveals stay still with reduced motion. Ticket text, ownership, called outlines and marking semantics remain native UI.
- Recent calls wrap and their cells, along with number-board cells, grow with system font scale. Singular counts and the default player's ticket title use Android string/plural resources; full Hindi localization remains in progress.

## Asset inventory and provenance

| Item | Source | Runtime dependency |
| --- | --- | --- |
| App icon | Repository native vector `app/src/main/res/drawable/ic_tambola.xml` | None |
| Home/table illustration | Original code in `ui/GameArtwork.kt`, authored for this app | None |
| Number ball, ticket paper, badge geometry | Native Compose code in the app | None |
| 270 number recordings | Existing repository AI voice assets; build verifies manifests and SHA-256 values | Packaged offline |
| Music, short effects and winner chimes | Original additive synthesis and score in `tools/compose-sounds.mjs`; manifest hashes verified at build | Packaged offline; listening/device acceptance pending |
| Avatar set, additional table decorations and celebrations | Still to be completed as one consistent native/vector or optimized raster set | Must not replace readable controls |

No paid generation or new external media was used for the appearance milestone. No developer API key is packaged. Any future generated/licensed material must add its source, prompt/model or license, date, output hashes and listening/visual QA before production distribution. Internal alpha assets may be supplied for that acceptance with their remaining checks explicitly recorded. Native vector/code is preferred where it keeps artwork crisp and small.

## Remaining presentation acceptance

The audio implementation now includes independent volumes, call ducking and interruption handling; see [audio design and provenance](AUDIO.md). Remaining work includes listening/device acceptance; finite dismissible winner celebrations; English/Hindi interface resources; avatar selection; non-color call/mark cues throughout; and a full TalkBack walk-through. Measure the resulting frame/audio behavior on physical phones. Emulator theme/motion checks are not a physical-device smoothness, pronunciation or assistive-technology sign-off.

## Implementation references

- [Material 3 color roles](https://developer.android.com/develop/ui/compose/designsystems/material3) guide screen/foreground pairing.
- [Compose value-based animations](https://developer.android.com/develop/ui/compose/animation/value-based) describe the `Animatable` state used by the number reveal.
- [Android SystemBarStyle](https://developer.android.com/reference/androidx/activity/SystemBarStyle) provides light/dark system-bar styling.

Exact candidate builds, executed checks and screenshots are recorded in the alpha validation report, separately from this design specification.
