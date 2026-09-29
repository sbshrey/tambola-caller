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

These are deterministic state/persistence and emulator UI checks. They do not
prove end-to-end latency, disconnect recovery or process death during a live
request. A controlled delayed/dropped-response run against the test server is
still required before release. No public server or installed APK changed.
