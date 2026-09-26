# Original-pixel verification

Source APK: `49344972eaf57381bfbd02a445882b71f3104259bed0ea4f638cc21c1a0cc891`, dedicated API30 emulator, 1080x1920 at density 420.

`arena-hi-light-6.png` is the original screenshot. The small `number-29.png`, `numbers-80-82.png` and `numbers-51-89.png` files are lossless crops made using Windows System.Drawing. They show complete digits where a full-screen preview appeared incomplete. The source is opaque 24-bit RGB; there are no transparent dark-ink pixels.

The two `*-glyphs.txt` files were measured inside the Android test from the saved screenshots using the actual number-node bounds. For example, digit 2 has 509 ink pixels, 5 has 518 and 8 has 612, including the supposedly missing leading glyphs. The full 12-layout test passes the new pixel checks for all four six-card captures. No application rendering fix was warranted by this evidence.

The check compares each glyph with other copies of that digit in the complete strip and rejects loss above 30%, allowing small rasterization/rounding differences across device sizes. It is intended to catch visibly missing strokes while preserving native-font flexibility; it does not validate the semantic identity of a drawn glyph or replace visual inspection.

A negative control erases only the leading 2 of 29 in a copy of the crop: its ink count changes from 509 to zero while the trailing 9 remains 533. The stored control image and JSON demonstrate that this substantial real loss fails the 70% comparison threshold. Original screenshots are unchanged.
