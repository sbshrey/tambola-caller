# Joining the host's game mode

The native reaction integration exposed unnecessary friction: a player with Classic selected could not join a Power invitation, or vice versa. The user had to guess the host's selection, return to the lobby and retry.

## Change

- A direct code/link join follows the host's mode after the server definitively returns `409 power_room_mismatch`.
- This rejection occurs before any debit, membership change or purchase receipt. The client persists a replacement request with a new ID and the opposite mode before sending it. There is at most one mode retry per operation execution.
- Transport failures, timeouts, rate limits, authorization failures and other server errors cannot change the purchase request. The exact durable request remains retryable after an unconfirmed response.
- New table creation, quick play, legacy rules, paid legacy power options and same-friends replay do not use this recovery.
- No protocol or server runtime changes are needed. Existing server validation and idempotency remain authoritative.
- The waiting room displays the actual Classic/Power mode. Invite and Copy use equal-width buttons; player counts and start deadlines are short in English/Hindi. Refund information remains in the entry flow and Cancel & refund action.

## Evidence

- 43 client tests pass, including both recovery directions, request serialization and failure/scope exclusions.
- Six focused PostgreSQL server cases pass. The new case proves both mismatch directions leave the wallet unchanged, add no member or receipt, then accept one corrected debit and replay that receipt exactly after service reconstruction.
- 21 Android unit tests pass. Android build/lint and 869-resource localization parity pass.
- Native tests use one emulator and an authenticated API host on a separate loopback service/database. Both mismatch directions go through the actual code-entry UI, preserve three tickets and a single 300-coin debit through activity recreation, and refund the full amount on leaving. These run at actual system font scale 200%.
- Initial screenshots exposed a squeezed Copy label and overly long waiting-room text. Equal button widths and shorter labels correct that; final checks also require the expiry, invitation actions, cancellation and ticket/pool summary to fit the viewport.
- `native-final.txt`: seven tests pass, including both live joins, the 50-player lobby and four English/Hindi connection-recovery fixtures. The fixture database contains zero remaining guest profiles after cleanup; the owned service, database and emulator were stopped.

The final spacing pass gives the mode/deadline explicit line heights and makes the ticket/pool summary compact. All five affected lobby/connection cases pass again (`native-spacing-final.txt`), and build/lint pass. `friend-waiting-fifty-final.png` shows the complete waiting screen at 200% system text size, including that summary with space below it.

Final debug APK SHA-256: `a7f184a87ec76d9a960a404794062b3dc87a66c143246905abc3526e62547adc`.

This is local emulator/API-peer evidence, not two-device or physical-phone acceptance. No public service, tunnel or published APK changed.
