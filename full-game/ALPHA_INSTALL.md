# Tambola Together 0.14.0-alpha14

Internal development APK for Android 8.0 or newer. It uses an Android debug certificate. Solo and same-device family play work offline; this build's private rooms require the local development service. It is not a public production release. See [candidate validation](ALPHA14_VALIDATION.md) for exact checks and remaining release requirements.

## Install or update

1. Open `releases/0.14.0-alpha14/Tambola-Together-0.14.0-alpha14.apk` on your Android phone.
2. Allow APK installation from that file manager if Android asks.
3. Install over the existing full-game alpha to keep saved games. Do not uninstall first. Alpha14 uses the same package and debug signing identity; an actual alpha13-to-alpha14 update retained a marked round, language and caller settings.

Tambola Keyboard is a separate app; this game does not require enabling a keyboard. Saves remain format 3. Older saved hands retain their original cards. A future production certificate may require a separate installation or uninstall, so this alpha is not a promise of permanent data migration.

## Start playing

- **Quick play** starts with three tickets, two labelled computer opponents, assisted marking, five-second calls and six familiar prizes: Early Five, Corners, Top/Middle/Bottom Line and Full House. An unfinished round offers **Resume round** instead of silently replacing it.
- **Custom game** lets you choose one to six tickets and change marking help. Expand **Avatars & prizes** for additional choices before dealing.
- **Pass & play** creates a family game on one phone. During play, open the three-dot **Game options** menu and choose **Pass the phone**. Calling pauses and the old hand is hidden until the next player opens theirs.

All your tickets stay together on the game screen without scrolling or a carousel. Newly dealt cards never repeat a number within your hand; six cards contain every number 1–90 once. Opponents' ticket grids are hidden.

In manual mode, **Dab · N** marks confirmed called numbers across your hand; repeated taps do not erase marks. **Next** and automatic calling controls stay below the cards. Tap the current ball to hear the call again. The three-dot menu contains the number board, prize details and Settings. Wins are verified automatically; same-call ties receive full points. Celebrations do not move the tickets.

After the round, view results or **Play another round**. Rematches retain the chosen setup and deal new cards. Sharing opens a preview; player names are excluded unless selected. **Your rounds**, **Your badges** and **How to play** are available from Home. The tutorial uses a separate sample and cannot replace or score your real round.

## Language, sound and saved games

Settings includes English/Hindi interface choice, System/Light/Dark appearance, separate English/Hindi/Hinglish caller choice, voice/music/effects volumes, haptics, reduced motion and calling pace. Music defaults off. Changing interface language retains the current game and unsaved prize setup and pauses local calling. Entered player/prize names stay as entered.

Leaving the app pauses offline play. Reopen and choose **Resume round**. Voice clips, original music/effects and artwork are bundled; no OpenAI key or runtime AI connection is needed. Number recordings are disclosed as AI-generated. Offline history and its badges remain on this device; deleting completed rounds removes their associated offline badges. There are no purchases or cash prizes.

For custom rules, open the editor under **Avatars & prizes** before dealing. It supports row/column/range/position conditions, multiple-ticket conditions and grouped alternatives, with an isolated winning example. Rules are fixed once the round is dealt. A regional prize name alone does not define a pattern; configure its exact rule. See [custom prizes](domain/CUSTOM_RULES.md).

## Private rooms in this internal build

The debug endpoint is `http://127.0.0.1:8080`. A normal phone installation does not reach a hosted server automatically. Local testing requires the current service build, database setup and a dedicated emulator or attached development phone with `adb reverse tcp:8080 tcp:8080`. Use fictional profiles. See [service setup](server/README.md) and [native client configuration](client/README.md). No public endpoint was deployed for this package.

With the configured service reachable, choose **With friends**, review the game-data disclosure, create a display-name profile and create or join a room. Invitation links and eight-character room codes require explicit joining and preserve an existing room or pending action. Public browser invitations and Android domain verification need the actual domain and production certificate; see [invitation setup](INVITES.md).

Players ready after agreeing to the rules; changing rules resets readiness. The host starts, pauses, ends and rematches the round. Calls and awards are server-confirmed. Reconnection catches up without replaying old announcements. **Retry pending action** rechecks the same request rather than creating a duplicate. Each player sees only their own tickets. Protocol remains version 2.

## Your game data

Settings and online registration link to the English/Hindi game-data explanation. Online sessions and cached rooms are encrypted on the device. **Delete online profile** requests service-side deletion/redaction and clears local online data after confirmation; a lost reply can be retried after restarting the app. Offline games remain. Signing out or resetting local online data does not delete the server profile.

Shared game records, other players' downloaded copies and backups have separate retention. A minimal recovery record prevents restored backups from restoring deleted access; it currently has no automatic expiry. Original deletion requests can confirm their result for 30 days. There is no account-recovery service in this alpha. See [exact deletion behavior and remaining retention work](server/PROFILE_DELETION.md).

Public hosting, production signing, physical-device/accessibility/audio checks, sustained load and operator/privacy/support/store acceptance remain release gates. Local emulator results do not establish mobile-network latency or market superiority.
