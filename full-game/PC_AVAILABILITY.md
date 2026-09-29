# PC-hosted availability

The user selected continued PC hosting on 2026-09-29. This provides automatic recovery while the current Windows account is signed in, the PC is awake, and power and internet are available. It is not an uptime guarantee or a boot-before-login service. Current AC sleep and hibernation timers are already disabled; no battery or power settings were changed.

## Recovery layers

- The existing backend watcher restarts its worker and the worker restores PostgreSQL and the game server after failures. Backend readiness is checked every five seconds; three failures trigger recovery. Daily backups remain enabled.
- The public watcher restarts its worker after exit. The worker checks local and public readiness every loop (15-second pause, bounded network probes). Three consecutive public failures with a healthy backend recreate the tunnel. A backend outage resets that counter and keeps the tunnel address instead of rotating it unnecessarily.
- GitHub directory publication retries after one minute without dropping a healthy tunnel, including on initial publication failure. Status reports `directory_pending`, `backend_unavailable`, or `public_unavailable` rather than claiming every running process is online.
- The Windows task **Tambola Jalsa Host Watchdog** runs at login and every two minutes under the current account. It checks exclusive watcher locks and starts missing watchers. It respects each host's `stop.request`; an explicit Start clears that marker. Task execution is hidden, bounded to one minute, and duplicate task instances are suppressed.
- Existing login shortcuts remain. Supervisor locks prevent duplicate workers. Locking the desktop is compatible with this setup; signing out is not.

## Install and inspect

`public-host.ps1 -Action Install` copies `public-health.mjs` and `host-watchdog.ps1` with the supervisor. Run the installed `host-watchdog.ps1 -Action Install` to register the current-user task; `-Action Check` performs one check, and `-Action Remove` removes the task. Installation does not require storing a Windows password or changing game credentials.

Inspect `%LOCALAPPDATA%/TambolaTogetherHost/state.json`, `%LOCALAPPDATA%/TambolaTogetherPublicHost/status.json`, and `Get-ScheduledTaskInfo -TaskName 'Tambola Jalsa Host Watchdog'`. Check both the APK's directory and `/health/ready` on the published origin before declaring the service reachable. Quick-tunnel replacement changes its address; GitHub raw-file and app caches can delay discovery for several minutes.

## Validation

`node --test tools/test-public-health.mjs tools/test-public-gateway.mjs` covers protocols 4–9, malformed/failed responses, consecutive public failures, backend-outage suppression, recovery reset, bounded gateway ingress and WebSocket forwarding. `tools/test-host-watchdog.ps1` uses isolated fake hosts to verify lock-based liveness, recovery after a lock is released, and intentional-stop handling. No real game process is killed by these tests.

The scheduled task was installed and manually triggered on this PC with result 0. Live readiness and deployed source equality are checked after deployment. Actual reboot/sign-out, power failure, ISP outage, and physical-phone reconnection have not been simulated. These checks establish the recovery logic and current service health, not 24/7 infrastructure availability.
