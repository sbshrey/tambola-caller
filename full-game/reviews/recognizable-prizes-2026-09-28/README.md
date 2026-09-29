# Recognizable prizes and competitor comparison

28 September 2026. Public publisher/store material was inspected this turn. These are documented feature observations, not claims that a competitor app was installed or played. The earlier native competitor download boundary remains in `../competitor-download-2026-09-27/README.md`.

## Evidence and decisions

| Reference | Documented feature | Decision for Tambola Together |
| --- | --- | --- |
| [Octro Tambola](https://tambola.octro.com/) | Standard 1–90 play, faster 1–50 Blitz and private Party mode | Keep a clear Play/Friends entry. Preserve the accepted ten-second, 90-number game rather than introducing another mode in the compact lobby. |
| [nLife Online Tambola Friends Housie](https://play.google.com/store/apps/details?id=com.herokuapp.tambolah) | Invite links/codes, direct marking, a number board, automatic claim verification and contextual chat | Existing invite/board/verification flows match these needs. Improve prize recognition at the point of claiming; contextual friend reactions are a future candidate requiring server rate limits and native validation. |
| [Bingo Blitz power guide](https://www.bingoblitz.com/support/power-ups/) | Distinct powers, per-round limits and explicit unavailable-power messaging | Keep the accepted free power rules. The preceding local change explains active, armed, used, discarded and missing powers in the picker. Do not add a purchase prompt or paid random powers. |

The pattern illustrations below are our design inference: lightweight visual rules can help players learn during play without another tutorial paragraph. No competitor code, screenshots, names or artwork are copied into the product.

## Implementation

- All six prizes in current coin rooms appear in a non-scrolling grid.
- Small original dot diagrams identify the required row, corners, early-five/ten or full-house shape. They are schematics of numbered positions, not actual ticket cells.
- Diagrams never read the ticket, called numbers or marked numbers. They cannot disclose a ready claim, missing number or future call.
- At large font sizes the decorative diagram is omitted to preserve room for complete labels and amounts. Accessible prize descriptions include the existing English/Hindi rule explanation.
- Legacy rooms with more than six standard/custom prizes retain their longer scrollable list, ranked-house locks and complete set of choices. Current coin rooms have six fixed prizes.
- Selection still submits the same ticket/prize command. The server remains authoritative for eligibility, penalties, shared winners and coins.

## Validation boundary

The native matrix uses the owned API30 emulator `tambola_lobby_review`, serial `emulator-5580`, with actual system font scales of 100% and 200%. It checks English/Hindi in both orientations, all six choices visible without scrolling, rendered text geometry, accessible rules, touch targets, legacy ranked-house locks and the exact submitted ticket/prize selection. Logs and screenshots accompany this file.

- `native-100.txt`: 8 tests passed.
- `native-200.txt`: 8 tests passed.
- Debug APK/test APK build and Android debug lint passed. `git diff --check` passed.
- Final local debug APK SHA-256: `1914a066abe04d6007736283079d0b9130b4d5496c05710cfd902328f191356c`.
- Restored the owned emulator to normal font size and shut it down after validation.

The changes are local. No public host, tunnel, ads or store publication changed. Physical-device play and an installed competitor evaluation remain unverified; this focused improvement does not prove completion of the broader enjoyment/usability goal.
