# Tambola Jalsa quick Bingo

New online Bingo tables use a short, server-authoritative 75-ball format. The
10-second lobby still admits real players before clearly identified computer
players fill open seats. Computer players receive one card each; a person can
choose one to six. The two available claims are One Line and Four Corners.
Each has five winning places, with same-call ties still accepted. The prize
pool is split evenly between the two goals. Unawarded coins follow the
existing per-card return policy.

Calls arrive every five seconds. A round ends after both goals fill or after
the claim window on call 45. The final call remains markable and claimable
until the following tick. Draw order is still a committed permutation of all
75 balls; only the called prefix is playable. The client shows the two goals
beside the card and enables a direct claim when the selected card is ready.
Numbered card tabs display missed marks on each owned card. The results view
keeps the player's rank, marks, goals and coins visible above the standings.
Quick-round dabs are permanent, so an accidental second tap cannot undo a
correct mark. Version 1 cards retain their original toggle behavior.

`BingoMatchRequest.quickPlay` separates new quick tables from older four-prize
tables. Existing lobby and active rounds retain version 1 rules, eight-second
calls, two winning places per pattern and their original payouts. Older app
versions continue to request version 1. Friends using mixed versions receive
an update-required response before a purchase. The client validates both
server projections and rejects invented patterns, prizes or extra quick calls.

Tambola's Classic lobby choice is removed for new matches. New tables earn
Auto-Dab or Prize Boost after five correct manual marks; Auto-Dab lasts 35
seconds. Older Classic and Shield rounds remain readable to completion. See
[Power rooms](POWER_ROOMS.md).

The purpose of these changes is to make the first reachable goal clear and to
bound the time to results. A seeded 500-table model with 40 one-card computer
opponents placed the fifth first-goal completion near call 29; with the
45-call limit, round duration is bounded at about four minutes after the
lobby. That model does not measure real player retention or win rates. Those
need monitored beta data and player feedback.
