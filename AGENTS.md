# Full-game identity and delivery

- The complete native digital-ticket game is **Tambola Jalsa**. Use this name consistently in new UI, release titles, shared invitations, previews and current documentation. Hindi display text may use **तंबोला जलसा**.
- All full-game work must be committed and pushed to **`shrey/tambola-jalsa`**. This includes `full-game/`, related designs, full-game documentation and its CI configuration. Continue on this branch for future updates; do not create a new feature/release branch for each change unless the user explicitly requests it.
- The branch begins with the complete history through `a4e7cc5` from `codex/internet-beta`. The older branches and historical release records are retained; they are not the destination for new full-game work.
- Preserve the older physical-ticket caller as a separate product. Do not rebrand its root web app or keyboard companion as part of full-game changes.
- Preserve Android application IDs, signing identity, installed data, GitHub repository identity and the `tambola-beta-v<versionCode>.apk` update-asset contract. The display name is not a package migration.
- Follow `full-game/APP_UPDATES.md` for APK updates. Validate changes, commit and push to the canonical branch, and publish validated release APKs to GitHub. Do not describe source/design progress as a deployed server or installed APK update.
