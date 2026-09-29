# Power-room marking queue

Previously every mark used the general pending-operation UI: the whole hand
disabled and the clock switched to recovery controls for each round trip.
The client now persists a bounded queue of unique valid mark intentions.
A single worker promotes the head into the existing durable command slot,
persists its command ID/revision, sends it, accepts the authoritative receipt,
and only then promotes the next mark. Ambiguous failures retain the same
command ID for explicit retry. Queued marks confirmed by an incoming snapshot,
or invalidated by a discarded ticket/changed or finished round, are pruned.

While that worker is sending, other ticket numbers and the call clock remain
usable. Each pending number has an amber outline and an accessible sending
state. Pending intentions never enter confirmed marks, claim validation,
power progress or rewards. Claims/power use remain disabled until pending
marks resolve. A failed request exposes the existing exact-retry control;
its ticket page and geometry stay fixed. The queue survives local persistence
and supports all 90 numbers across a six-ticket strip.

Validated 2026-09-29: the client suite passed, including three queue scenarios
covering a 90-mark burst, serialization/promotion/receipt identity, duplicate
taps, confirmed/discarded/changed-round pruning, foreign/uncalled numbers,
Classic rooms and unrelated pending operations. App unit tests and both debug
APK builds passed. Six existing native layout/recovery tests passed; the new
pending-mark UI test passed after correcting its online-round fixture to
include two players. Localization parity passed for 898 resources.

## Local network and process-restart acceptance

On 2026-09-29, `PowerMarkOnlineTest` passed through the actual Android
`OnlineViewModel`, encrypted `OnlineStore`, HTTP/WebSocket client, local fault
proxy and protocol-8 PostgreSQL-backed service. This used the debug runtime
from source commit `ba4a117` on the owned emulator `emulator-5582`.

The test creates a private Power round with six owned tickets, waits for five
real timed calls, then submits five marks as a burst. The proxy delays command
responses by 1.5 seconds and discards successful command responses after the
server has committed. Before retry, the server has exactly one correct mark,
while the encrypted client store retains that exact pending command and four
queued intentions. Retry drains all five, grants exactly the previously shown
power once, and leaves the wallet unchanged. Replaying the original receipt
returns its one-mark snapshot; the live room still has five marks.

The complete live recovery scenario passed in 71.927 seconds. A separate seed
stage passed in 61.606 seconds and retained the encrypted failure state. The
instrumented process ended, `adb shell am force-stop` enforced a stopped app,
and a fresh instrumentation process ran the recovery stage. That passed in
12.664 seconds, with exactly five marks, one power, unchanged balance, an empty
queue and no pending request. The proxy recorded eight discarded responses
across the two scenarios, including transport retries, without extra progress.

Opt-in instrumentation arguments: `tambolaMarkFaults=true`; run
`PowerMarkOnlineTest#burstSurvivesDelayedCommittedResponseLossWithoutDuplicatePowerProgress`
for the complete scenario. For the restart scenario, run that method with
`tambolaMarkStage=seed`, stop the app, then run
`PowerMarkOnlineTest#recoverEncryptedQueueInNewProcess` with
`tambolaMarkStage=recover`. Both stages require the same isolated server state.
The fixture peer's credentials use a separate encrypted store and are deleted
by the recovery stage. The test refuses non-debug, non-loopback or non-emulator
execution. Reverse emulator ports 8080/8082 to the fixture proxy/control, with
the isolated service on 8081 and an explicitly named `tambola_test` database.

This proves controlled local delayed-response and process-restart behavior;
it does not establish real-phone/public-network performance. No public server
or installed beta APK changed. The local fixture services are stopped after
the run.
