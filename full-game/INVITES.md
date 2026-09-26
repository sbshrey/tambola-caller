# Private room invitations

An invitation contains the configured service origin and an eight-character room code, for example `https://rooms.example/invite/ABCDEFGH`. The share sheet includes both the link and the code. Codes select an existing private room; they are not credentials. Guests still register, explicitly join an open lobby, and use their own encrypted bearer session. Opening a link never registers, joins, leaves, or retries a pending command.

## Native behavior

- Cold and warm `ACTION_VIEW` launches use the same bounded parser. Only the build's configured service hostname/port is accepted. Standard HTTP links on that host may select the corresponding HTTPS room, but API calls always use the compiled HTTPS origin. The explicit debug fixture uses `http://127.0.0.1:8080`.
- Reject user info, query parameters, fragments, percent-encoded paths, extra path segments, malformed codes, foreign hosts, non-ASCII link text and oversized input. A rejected link shows a generic message; it does not echo the URL. Links cannot choose another API, inject a token, or configure rules.
- Display the room code for review. Before registration, the invitation waits while the player chooses a nickname/avatar. Joining remains a separate explicit action after registration. A closed, expired, locked, full or started room uses the ordinary localized service error and keeps the invitation available to dismiss/retry.
- Preserve the current room and pending operation. Invitations for the current room offer Continue; invitations for another room wait until the player can leave. Existing service rules prohibit ordinary Leave during an active round. Invitations do not end a round. Offline tickets, calls and marks remain saved; moving to Online pauses an offline caller as usual.
- `singleTask` delivers new invitations to the existing main activity. Saved state contains only the normalized room code (or a generic invalid marker); external Intent data/extras are discarded. Recreation retains an outstanding invitation. Dismiss removes it; reopening task history does not reconsume the original link. A force-stop or dismissed task is not guaranteed to preserve an invitation: reopen the link or use the code.
- App Links use HTTP/HTTPS, the configured host, `VIEW`/`DEFAULT`/`BROWSABLE`, `/invite/` and `autoVerify` for a configured HTTPS endpoint. The parser is stricter than Android's manifest path matcher. There is no custom URI scheme.

## Service configuration

| Variable | Value and behavior |
| --- | --- |
| `TAMBOLA_PUBLIC_ORIGIN` | Canonical HTTPS origin, matching the app's `-PtambolaApiUrl`. No path, credentials, query or fragment. Explicit local fixture mode permits HTTP on `127.0.0.1`. Unset means public invite routes are disabled. |
| `TAMBOLA_ANDROID_CERT_SHA256` | Up to eight comma-separated colon-delimited SHA-256 **public signing certificate** fingerprints. Use the certificate that signs the installed APK, including Play App Signing where applicable. Unset means `assetlinks.json` returns 404; it does not assert verification. Never provide a keystore, private key or password here. |
| `TAMBOLA_ANDROID_INSTALL_URL` | Optional HTTPS app listing/download page. Unset means the page tells the recipient to ask their host for the APK. No fabricated store listing or automatic download. |

Public routes are `GET /invite/{code}` (HTML) and `GET /.well-known/assetlinks.json` (JSON). The only supported page query is exact `?install=1`, used for browser fallback. Other query data is rejected. These pages perform no room lookup, expose no host/player information, set no cookie, and do not create a guest. A correctly formed nonexistent/expired code gets the same page; availability is checked by authenticated join. Request `Host` and forwarding headers never determine links or redirects.

The bilingual responsive page includes a selectable room code, Android Open action, onboarding steps and optional install action. It works without JavaScript, external assets or analytics, respects reduced motion and follows light/dark appearance. HTML responses use no-store, no-referrer, noindex/nofollow, nosniff and a restrictive CSP with a hash for fixed CSS. Site requests use the existing fixed `other` monitoring label. Operator/reverse-proxy logs must redact invite paths/codes and must not log bearer headers. Sharing a code permits recipients to attempt to join; lock a lobby when all invited players have arrived.

The Open action uses a user-clicked Chrome `intent:` URL with this app's explicit package and a canonical same-origin encoded fallback URL. Fallback explains installation/manual code entry. Browser support varies; desktop browsers do not run an Android APK. Manual entry remains available. See [Chrome Android intent documentation](https://developer.chrome.com/docs/android/intents).

## Hosted release acceptance (still required)

1. Select the real service domain, TLS host, distribution destination and release signing identity. Build with the same HTTPS origin; provision only public certificate fingerprints in the service configuration.
2. Route both public paths to the service. Serve `/.well-known/assetlinks.json` directly over HTTPS with JSON content type, no authentication and no redirect. Confirm the installed build's package and signing fingerprint match the statement; the local debug certificate is not a production identity.
3. Verify association on supported Android versions, including a fresh install and the production distribution signing certificate. Check Android's domain verification result; a working explicit component launch does not prove domain verification. Follow [Android App Links setup](https://developer.android.com/training/app-links/add-applinks) and [Digital Asset Links configuration](https://developer.android.com/training/app-links/configure-assetlinks).
4. On two physical phones on different networks, share via an actual messaging app, open cold/warm, review/register/join, switch language, rotate, background/restore, and confirm duplicate taps cannot replace membership or pending commands. Cover active/current/different rooms, full/locked/expired/closed rooms and no app installed. Test Chrome's user-clicked Open and fallback, plus manual entry in unsupported browsers.
5. Confirm TLS, privacy headers, redacted edge logs, no third-party requests and real installation instructions. Complete TalkBack/large-text/Hindi editorial checks and add the public privacy/support links when available.

Local HTTP/browser/emulator checks establish implementation behavior, not public-domain association, physical-device networking or store readiness. See the candidate validation report for exact evidence.
