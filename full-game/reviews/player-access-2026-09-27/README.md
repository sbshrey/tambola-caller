# Direct player access and large-text prize layout

The table sidebar showed only two players when eight prizes were active, and the complete player list was hidden inside prize details. The existing player block now opens a dedicated roster; the table menu provides the same access. The owner appears first. Computer labels stay on a separate line, including beside a long name. Closing the roster preserves the selected ticket page and manual marks.

Visual review at **150% Android system font size** also found that inherited text line heights clipped the last two prize rows. Explicit compact line heights keep all eight rows inside the prize rail without shrinking the tickets. [Before](large-text-prizes-before.png), [after](table-hi-large.png), [English table](table-en.png), [English roster](roster-en.png), [Hindi large-text roster](roster-hi-large.png).

The debug emulator passed three manual-table interaction tests and a separate Hindi test with the actual system font scale set to 1.5. Assertions cover all eight player names/computer labels, a minimum 48dp roster target, all eight prize-row bounds, retained ticket geometry/marks, per-ticket claim selection and menu access. System font and animation settings were restored. Fifteen Android JVM tests passed; lint has zero errors and 87 warnings. [Validation and source/APK hashes](validation.json), [interaction results](interactions.txt), [actual large-text results](hindi-system-large-text.txt).

An initial test build failed on unsupported DpRect width/height access and was corrected to edge differences. Initial screenshots revealed that composition-local font scaling did not affect the separate Android dialog window; the final dedicated run uses the real system setting. Screenshot capture now waits for the dismissed dialog to leave the semantics tree and settle. Retained failure/build logs document those corrections.

This is a fictional-round debug UI check on the API30 emulator. It does not establish optimized APK, network, physical-phone or frame-performance acceptance. The installed Wi-Fi service was unchanged. Raw artifact bytes are listed in `artifact-hashes.json`.
