# Version 1 custom prize rules

Custom prizes extend the standard prize enum without assigning guessed meanings to regional names. A host defines the executable pattern, title and points. Alpha02 exposes this through the Android setup editor, including an agreed-rule explanation, interactive sample tickets and positive/incomplete examples. The first alpha01 APK remains unchanged.

Each prize has a stable `custom_…` ID, version 1, a title of up to 40 characters, 1–1,000 points, and a ticket pattern. Up to 12 custom prizes are allowed. IDs must be unique within the room.

A pattern is an OR of at most four groups. Each group is an AND of one to eight conditions, with at most 16 total conditions. This flat format is deliberately bounded; it cannot execute code or recursively nest expressions.

Each condition selects numbers using one of:

- `all`: the ticket's 15 populated numbers.
- `row`: top/middle/bottom, indexed 0–2.
- `column`: standard ticket column, indexed 0–8.
- `range`: inclusive numeric range within 1–90.
- `positions`: specific populated positions indexed 0–14 in row-major order (five per row). These are not the 27 grid-cell indices. Four corners are positions 0, 4, 10 and 14.

`minimumCalled: null` requires every selected number. A count of 1–15 requires at least that many. An empty selection **never matches**, even when all numbers are called; a threshold larger than a ticket's selected count cannot match that ticket. The editor rejects counts exceeding that selector's theoretical capacity, and the sample preview identifies cases that cannot match the current illustrative tickets. Columns/ranges can still differ between actual tickets.

`minimumTickets` requires one to six matching owned tickets. Optional `ticketOrdinals` limits evaluation to specified owned tickets, numbered 1–6 in deal order; an empty list considers all owned tickets. Configuration rejects requirements exceeding the room's ticket allowance. Every qualifying ticket is recorded; points are awarded once per player per prize. All players qualifying on the same call share the award and receive full points.

Example: at least two numbers in the top row AND all four corners, on at least one ticket:

```json
{
  "id": "custom_family_pattern",
  "title": "Family pattern",
  "points": 25,
  "version": 1,
  "minimumTickets": 1,
  "ticketOrdinals": [],
  "pattern": {
    "alternatives": [[
      {"selection": {"type": "row", "index": 0}, "minimumCalled": 2},
      {"selection": {"type": "positions", "positions": [0, 4, 10, 14]}, "minimumCalled": null}
    ]]
  }
}
```

Eligibility uses called numbers, independent of marks. The selected terminal house still ends a normal game; a custom prize can remain unawarded when that happens. `playAllNumbers` continues to 90 calls. Practice undo removes custom awards created by the undone call. Save validation replays both standard and custom awards and rejects discrepancies.

Round format version 2 adds `customPrizes`/`customAwards`. The decoder accepts version 1 saves that lack those fields, validates their original calls/marks/awards, and upgrades the in-memory version. The checked-in `round-v1.json` fixture protects this migration. See the alpha validation reports for actual APK update evidence; fixture coverage alone is not an installation/update test.
