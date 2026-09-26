# Alpha14 sustained-service evidence

The fresh default run `832f4957afc84935` started at 2026-09-26 13:30:44 UTC. It uses ten rooms, 32 players per room, six tickets each, nine full 90-call games and five-second automatic scheduling. **The run is in progress; this directory does not establish a passed endurance gate.**

The application/service source is `cfd809ed1f290c7f65576afe80fe999adaefa6ab`. `runtime-identity.json` records the canonical identity used by both Kotlin and Node. All 51 JAR filenames and hashes equal the original migration/alpha14 package record. The original PowerShell manifest sorted `HikariCP` differently, so its manifest checksum differs despite identical runtime bytes. Original evidence/package files are preserved.

The stopped prior run `84c186ac5f98432a` remains incomplete after five games. Its original interruption record and subsequent manual cleanup record are retained here. Before removing its two exact generated databases, inspection confirmed the recorded service process was absent and both databases had zero active connections. SQL then confirmed both databases were absent. Cleanup does not convert partial gameplay into acceptance.

Active-run logs and status remain under the ignored `.test-workspace/` directory until a terminal result can be reviewed and archived. The new run uses a hidden owned launcher with a separate terminal-status file, so its execution is not tied to a tool polling session. No competing emulator or heavy build/scanner workload runs during measurement.
