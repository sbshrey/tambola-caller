# AdMob console callback correction — 28 September 2026

The console's Verify URL request reached the public PC host but returned HTTP 400. Safe diagnostics showed that Google's signature, reward amount/item, custom data and timestamp passed; only the ad-unit and transaction identifiers were rejected. AdMob's signed console probe uses the sample pair `ad_unit=1234567890`, `transaction_id=123456789`, rather than a completed impression's identifiers.

Server candidate `6f5bea1d1a37faeddffb37d276e6594c4f0d9248` recognizes only that exact pair after Google signature verification. The configured 1,000-coin reward settings and fresh timestamp are still checked. A console probe returns successfully without opening the reward transaction, consuming an intent, creating a receipt, or changing a wallet. Real callbacks retain the configured unit, issued-intent, transaction uniqueness and expiry checks.

## Verification

- Seven `RewardedAdsTest` cases passed against isolated PostgreSQL: valid signatures and exact-once credits, concurrency/restart, incorrect signatures and fields, daily limits, profile deletion, safe diagnostics, and signed console-probe handling. Zero failures, errors or skipped tests. Build: `:server:test --tests '*RewardedAdsTest' :server:installDist -PserverOnly=true`.
- Probe regression coverage verifies repeated signed probes and optional custom data cannot grant coins or consume the real intent; a subsequent real-format signed callback can still grant exactly once. Tampered, wrong-reward, future-dated and wrong-unit requests remain rejected.
- The committed server was installed through `upgrade-windows-host.ps1`, after an idle-room preflight and paired primary/journal database backups. The new server process passed readiness checks.
- The existing public tunnel remained running at `https://intelligent-queue-clause-chose.trycloudflare.com`. `/health/ready` returned HTTP 200, protocol 6. An unsigned request containing the sample identifiers still returned HTTP 400.
- At **08:19:49 UTC**, the running server recorded `AdMob signed console verification accepted; no reward granted` for the user's fresh console attempt. This proves that the real Google signature and exact sample identifiers passed the deployed handler; raw callbacks, signatures and user identifiers were not retained in logs.
- The owned probe wallet subsequently remained `confirmed=False`, `coinsAdded=0`, as required for a console test.
- The user independently confirmed **Verification succeeded** in AdMob. The temporary QA profile and encrypted local probe record were then deleted through the owned-profile cleanup helper.

The Windows supervisor now permits live log reading with fixed, rate-limited rejection labels. Before this change it held an exclusive log handle. Its installed script was backed up and updated. The PC's previous LAN address was no longer assigned after restart; its backed-up local TLS configuration was corrected to its current Ethernet address, and both backend and TLS readiness recovered. The public callback URL did not change.

## Scope

The console test proves public delivery and signature verification. It does **not** prove an actual rewarded impression or its wallet credit. Earlier setup instructions incorrectly expected the console test itself to award 1,000 coins; `ADMOB_SETUP.md` and the probe helper now make this distinction explicit.

The alpha39 APK is unchanged and still has live advertising disabled. A completed-ad callback, account/app readiness and consent configuration remain separate activation checks. The user must finish the console's **Use verified URL / Save** step; saving was not inspected through browser automation.
