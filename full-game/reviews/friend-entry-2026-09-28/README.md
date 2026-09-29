# Compact friends entry

The current lobby direction is direct entry with minimal explanation. The earlier [reference comparison](../recognizable-prizes-2026-09-28/README.md) identified private parties and invite codes in Octro/nLife; this pass simplifies our existing create/join interaction rather than adding another game mode.

## Behavior

- Create table and Join with code share a non-scrolling dialog.
- Only joining requires the eight-character code. Whitespace is removed and letters normalized; invalid codes cannot be submitted.
- Ticket quantity, coin cost and a short full-refund-before-start note remain visible before confirmation.
- Landscape uses two columns; portrait stacks the same controls.
- With the software keyboard open, the dialog keeps only the code field and Done. The input retains focus, and Done dismisses the keyboard without making a purchase. Cost and the explicit Join action return afterward.
- Removed the long group-play explanation from this entry step. Server membership, wallet and refund rules are unchanged.

## Verification

Native fixtures exercise English/Hindi in both orientations at 200% text on the dedicated API30 emulator. They check controls within viewport bounds, no scroll container, exact create/join callback values, invalid-code disabling, code normalization, retained typing focus, keyboard dismissal, no submission while typing, and closing the dialog.

Initial runs exposed the dialog focus-manager scope, portrait keyboard overflow and stale child content during the keyboard transition. These were corrected, including explicitly passing the editing state into both child sections. The fixture taps the code field and waits for the real keyboard/inset transition before asserting or tapping; Compose's test clock alone does not synchronize it. `native.txt` preserves the initial failure evidence; `native-final.txt` records all four final cases passing.

Build and Android lint pass; the localization checker passes 869 English/Hindi resources. Debug APK SHA-256: `9ba148d2e0afb79714f3aa66f853a0e9401a5e295b71982754a785a0fd3ffb48`.

This verifies local UI and callback behavior, not a new server purchase integration run or physical-device acceptance. No public host, tunnel or published APK was changed.
