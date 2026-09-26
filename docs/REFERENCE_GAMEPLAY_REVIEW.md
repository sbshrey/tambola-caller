# Reference gameplay and next interaction revision

26 September 2026. User-supplied local reference: `WhatsApp Video 2026-09-26 at 21.25.08.mp4` (13.76 seconds, 496 × 368). Reviewed frames throughout the clip, including half-second contact sheets. The private recording stays outside source control; no competitor artwork is imported.

## What is visible

- A landscape phone with two tickets stacked in the main play area. Both tickets remain readable while numbers arrive.
- Recent numbers form a horizontal row. At about 1.5 seconds, 22 enters with a large ring; at about 7.5 seconds, 1 enters. Older balls move along the row.
- A narrow left rail contains player portraits and a player count. Small ticket and claim counters sit at the top right.
- The player directly taps numbers on the tickets; marked numbers have conspicuous stamp-like decoration.
- At about 12 seconds, the first ticket's Claim control opens six prize choices and temporarily replaces the second ticket. The clip does not demonstrate successful claim verification, ties, reconnect, or bot fairness.
- Up/down arrows below the tickets suggest navigation, but the clip does not establish how many owned tickets are available or what that navigation does.

## Decisions for our app

The new user instructions supersede the earlier assisted-marking / batch-Dab / automatic-award interaction in `GAMEPLAY_REDESIGN.md`. Until the new implementation is enabled and validated, alpha14 retains that earlier behavior.

1. **Manual play by default.** Assume automatic five-second calls and individual manual number marking, pending the optional clarification already asked. Quick play requires no settings form. Assisted marking can remain an explicit option. The owned hand still contains 1–6 disjoint tickets.
2. **Landscape game table.** Put recent balls and small round/ticket/remaining-prize counters in one top bar. Reserve a narrow left rail for prize progress and labelled players. Use the remaining area for every owned ticket, with one prominent Claim action below it. Home, settings and the full number board are secondary controls. Avoid text paragraphs in active play.
3. **One real Claim action.** Check every owned ticket against every remaining enabled scheme. Validate called numbers and the player's submitted marks. Award all valid schemes in one action, report a short reason when nothing matches, and keep the tickets visible. Do not automatically award human prizes during a draw and then pretend that Claim did the work.
4. **Fair call windows.** Successful claims made on the same called number share a prize; receipt order must not decide the winner. Close that window when advancing the call. Retain a final claim window after number 90 and after a terminal-house claim. Online commands must identify the round and call they claim against; an unrelated room revision must not invalidate a current-call claim. A lost response must reuse its idempotency key.
5. **Honest computer players.** Practice and online rounds may contain clearly labelled computers. Human-only and mixed private rooms remain possible. Computer marks/claims use only revealed numbers and the same rules. Give them bounded reaction delays; no hidden advantage, fabricated human identity, or inspection of a future draw.
6. **Visible, short motion.** Animate a new ball entering the recent-call row, a stamp on each manual dab, and a compact claim celebration beside the winning ticket/left prize rail. Keep the hand geometry fixed. Respect reduced motion; no continuous flashing, win modal or network dependency for effects.
7. **Usable complete hands.** Prefer two columns for four to six landscape tickets if this produces larger numbers; evaluate the actual phone dimensions. Never fix density by hiding tickets or introducing a carousel. Compact six-ticket cells cannot honestly be described as 48dp targets on every phone: provide an optional large manual action for the current number and accessible actions for missed called numbers. The main Claim and navigation controls remain at least 48dp.

## Implementation slices and acceptance

1. Add deterministic manual-claim rules, replayable claim evidence, same-call ties, final-call closure and legacy-save compatibility. Test ownership, forged/uncalled marks, repeated claims, multiple schemes, ranked houses, custom schemes and restoration before enabling this mode.
2. Add the authenticated online claim command and server-owned computer seats/reaction scheduling. Preserve the existing allow-list projection: return only the requester's tickets. Test stale unrelated revisions, late claims, replay after lost replies, races, rematch and bot/human combinations.
3. Replace the live arena with the landscape hierarchy and directly tappable numbers. Connect the single Claim action to the actual engine, with concise English/Hindi feedback. Update Quick play, setup, onboarding and result descriptions consistently.
4. Inspect native captures with 1/2/3/6 tickets, portrait fallback, Hindi, large text and reduced motion. Exercise a full manual solo round and a mixed online round, including reconnect and invalid/valid claims. Measure the resulting candidate, not an earlier binary.

The completed alpha14 nine-game Android endurance run is baseline evidence only. Its assisted-marking and automatic-award workload cannot validate these new interactions. Production hosting, signing and physical-device release gates remain recorded separately in the release ledger.

## First implementation checkpoint

- The domain has an explicit manual-claims mode, version-four replayable proof history, one-action evaluation of standard/custom schemes, order-independent same-call ties, ranked-house exclusion, computer settlement and a final claim window. Existing version-one/two/three saves retain automatic awards. New mode activation, native controls and online commands are still pending.
- All **42 domain tests pass**, including **13 new manual-claim cases**. Initial test-authoring mistakes (a parenthesis and a fixture assuming two random tickets shared a number) were corrected before acceptance; production logic was not weakened to make those fixtures pass.
- `design/landscape-play-preview.html` is an original interactive layout sketch, with manual taps, recent balls, a left prize/player rail, a single Claim button, bounded motion, reduced motion and 1–6 owned tickets. All six illustrative tickets obey the ticket rules and partition 1–90. Computer opponents and network behavior are not simulated in this sketch.
- Actual browser checks cover uncalled-number rejection, invalid/valid claims, two simultaneous valid schemes, 2/6-ticket geometry, dark/light appearance, narrow-width overflow and script errors. The sketch does not establish native phone touch-target or frame-time acceptance.
- The old alpha14 endurance run has now completed successfully; see the release ledger. It remains baseline evidence, not validation of this new mode.
