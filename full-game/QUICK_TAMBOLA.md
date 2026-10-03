# Quick Tambola

The public Tambola button joins an online Quick Tambola match. The server waits
ten seconds for people, then fills remaining seats with visibly labelled computer
players. Existing public matches and friend tables keep the rules they started
with. New Quick Tambola clients do not join older public lobbies.

## Round

- Fifteen players: real players who arrive during the wait, with computer seats
  filling the rest. Computer seats use one ticket each. People may buy one to six.
- Two goals: Early Five and Any Line. The latter means all five numbers in any
  top, middle, or bottom row on one ticket.
- Each goal has five winning places. Same call ties remain eligible until the
  next call.
- Calls arrive every five seconds. The round ends when both goals close, or after
  the claim window for the 60th call. Unawarded prize coins are returned under
  the existing proportional settlement rule.
- Called numbers dab automatically on Quick Tambola tickets. The player claims
  a ready goal. If exactly one goal is ready, the ticket's Claim action submits it
  directly. With two ready goals, the player chooses one. Unready prize actions
  are disabled. Powers are absent from this mode because automatic dabs remove
  their mark based progression.

## Compatibility and integrity

The wire flag `quickTambola` is absent from old requests and saved rooms. Its
server validation binds it to the 60 call, two goal, five place settings. The
server holds the draw order and checks each claim against called numbers.
Round replay validates the call limit; payouts retain durable ledger keys and
the existing final refund calculation. The public room protocol is version 10
only for this mode.

Friend tables remain on their current six goal rules and power configuration.
Historical rounds, receipts, and installed profiles are unchanged.
