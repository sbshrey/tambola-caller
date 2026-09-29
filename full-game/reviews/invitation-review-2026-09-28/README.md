# Compact shared invitation review

Shared invitation links now open a compact, responsive confirmation dialog with the room code, six ticket choices, price, refund note and Join action. Landscape uses two columns; portrait stacks the same controls. There is no scroll container or introductory gameplay paragraph.

Opening a link remains read-only. Only the labelled Join button purchases tickets. Pending requests, occupied rooms, invalid links, unavailable sessions and insufficient balances retain their guards. Anonymous preview uses the domain beta-balance constant.

Native coverage checks English and Hindi at 200% text in portrait and landscape, normal English landscape, selection and explicit purchase, pending/invalid links, and wallet/busy guards. Bounds assertions verify every essential control fits inside the dialog window. Screenshots are retained alongside this report.

The first run reached the screenshot helper but failed five cases because decimal font-scale names violated its filename validator; the fixture now uses integer percentages. This was a test-artifact naming error. The two guard cases passed in that initial run. See `native-final.txt` for the final combined run.

This is a local debug candidate verified on the dedicated API30 emulator. Physical phones and public deployment are not covered.

Final combined run: all 38 lobby, friend-entry, invitation, connection and reaction cases passed, including all seven invitation cases. Eight prize-picker cases stopped at their setup assertion because the runner omitted `tambolaPickerScale=2.0` while the emulator was at 200%; those are rerun separately in `native-picker-final.txt`.

Current debug APK SHA-256: `2c66069c5c40510f50ceb4ca429b0b817fa5f9178255e3cfe10b2e8668d4eccd`.

The corrected prize-picker run passes all eight cases. Together with the 38 passing cases from the combined invocation, final native coverage is 46 passing cases.
