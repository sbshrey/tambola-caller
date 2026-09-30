# V42 release validation

Published GitHub release: full-game-alpha42-permanent-server. APK source commit: 499c033. SHA-256 and exact installed APK digest match the GitHub asset digest recorded in published-updater.json.

- 49 client tests passed, including permanent-host validation, no directory request, unchanged invitations and no automatic purchase replay.
- Optimized publicBeta build and release lint completed with Firebase wiring and the previously configured live-ad flag preserved.
- Package, version increase and signing certificate matched the v41 APK.
- Public hostname integration passed registration, daily rewards, friends-room purchase, retry/refund and reward-intent checks; owned API test profiles deleted.
- Signed candidate upgrade and published in-app updater both retained the existing wallet, six-ticket preference and authenticated session. The published updater took 35.242 seconds on the owned emulator and required Android installation confirmation.
- Post-publication v42 purchase/refund and profile cleanup passed in 15.08 seconds. Test profiles were deleted.

The first upgrade driver attempt hit stale-revision conflicts when cancelling a progressively populated public room; profile retention and purchase had succeeded, but refund/cleanup did not pass that attempt. Interrupted owned fixtures were explicitly deleted. The final upgrade test uses a private friends table and handles cancellation conflicts promptly, isolating migration behavior from automatic public-room joins. This release does not claim to fix that pre-existing public-room cancellation conflict.

No physical-phone acceptance, sustained load test or new Windows reboot test was performed for v42. Hosting still requires the PC and Internet connection. The old quick tunnel stays available to older APKs.
