package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.protocol.RoomInvites
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import kotlinx.serialization.json.*

/** Public invite pages contain only a room code. No profile or room lookup takes place here. */
class InviteSite private constructor(val origin: String, private val fingerprints: List<String>, private val installUrl: String?) {
    companion object {
        const val PACKAGE = "io.github.sbshrey.tambola.game"
        fun configured(origin: String?, certificates: String?, installUrl: String?, localFixture: Boolean = false): InviteSite? {
            if (origin.isNullOrBlank()) {
                require(certificates.isNullOrBlank() && installUrl.isNullOrBlank()) { "Invite metadata requires TAMBOLA_PUBLIC_ORIGIN" }
                return null
            }
            val canonical = requireNotNull(RoomInvites.origin(origin, localFixture)) { "TAMBOLA_PUBLIC_ORIGIN must be a HTTPS origin (explicit loopback fixtures may use HTTP)" }
            val fingerprints = certificates?.takeIf { it.isNotBlank() }?.split(',')?.map { it.trim().uppercase(Locale.ROOT) }.orEmpty()
            require(fingerprints.size <= 8 && fingerprints.all { it.matches(Regex("(?:[0-9A-F]{2}:){31}[0-9A-F]{2}")) }) { "Invalid Android signing certificate fingerprints" }
            val install = installUrl?.takeIf { it.isNotBlank() }?.also {
                require(it.length <= 2048 && it.all { c -> c.code in 33..126 } && runCatching {
                    val uri = URI(it)
                    uri.scheme == "https" && uri.host != null && uri.rawUserInfo == null && uri.rawFragment == null && (uri.port == -1 || uri.port in 1..65535)
                }.getOrDefault(false)) { "The Android install URL must be a HTTPS link" }
            }
            return InviteSite(canonical, fingerprints.distinct(), install)
        }
        private val css = """
            :root{color-scheme:light;font-family:system-ui,-apple-system,Segoe UI,sans-serif;color:#182b3c;background:#fff9ef}*{box-sizing:border-box}body{margin:0}main{max-width:680px;margin:auto;padding:32px 20px 48px}.brand{font-size:14px;font-weight:750;letter-spacing:.12em;text-transform:uppercase}.hero{margin:38px 0 24px}.eyebrow{color:#466557;font-size:13px;font-weight:750;letter-spacing:.12em;text-transform:uppercase}h1{font-size:clamp(30px,7vw,46px);letter-spacing:-.035em;line-height:1.12;margin:14px 0}p{line-height:1.65}.card{background:#fff;border:1px solid #d6dfd5;border-radius:24px;padding:24px;box-shadow:0 12px 32px #233b3910}.code{display:block;overflow-wrap:anywhere;font-size:clamp(24px,7vw,38px);font-weight:800;letter-spacing:.1em;margin:8px 0 18px;color:#174f43}.button{display:inline-block;padding:14px 22px;background:#174f43;color:white;text-decoration:none;border-radius:14px;font-weight:700;line-height:1.5}.button:focus-visible,a:focus-visible{outline:3px solid #aa6b00;outline-offset:4px}a{color:#174f43}.note{font-size:14px;color:#475968}.steps{padding-left:22px;line-height:1.7}.steps li{padding:4px 0}hr{border:0;border-top:1px solid #e0e6de;margin:24px 0}footer{margin-top:28px;font-size:13px;color:#475968}.ball{display:inline-grid;place-items:center;background:#f4d389;color:#473911;border-radius:50%;width:44px;height:44px;font-weight:800;margin-right:9px}.brandline{display:flex;align-items:center}.hindi{margin-top:20px}h2{font-size:20px;line-height:1.4}@media(prefers-color-scheme:dark){:root{color-scheme:dark;background:#121d2b;color:#edf4ed}.card{background:#1c2b3a;border-color:#425748}.code,a{color:#b6e0ca}.eyebrow,.note,footer{color:#c1d0c7}.button{background:#b6e0ca;color:#17362d}hr{border-color:#425748}}@media(prefers-reduced-motion:no-preference){.card{animation:arrive .35s ease-out}@keyframes arrive{from{opacity:0;transform:translateY(8px)}to{opacity:1;transform:translateY(0)}}}
        """.trimIndent()
        private val cssHash = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(css.toByteArray(Charsets.UTF_8)))
        private fun escape(value: String) = value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;")
    }

    fun routes(route: Route) = with(route) {
        get("/.well-known/assetlinks.json") {
            val json = associations() ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respondText(json, ContentType.Application.Json)
        }
        get("/invite/{code}") {
            // Validate the raw path as well as decoded route parameters. Encoded paths and extra query data cannot become invitations.
            val request = call.request.uri
            val fallback = request.endsWith("?install=1")
            val path = if (fallback) request.removeSuffix("?install=1") else request
            val code = RoomInvites.parse(origin + path, origin, origin.startsWith("http://"))
                ?: return@get call.respond(HttpStatusCode.NotFound)
            call.response.headers.append("Content-Security-Policy", "default-src 'none'; style-src 'sha256-$cssHash'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'")
            call.response.headers.append("Referrer-Policy", "no-referrer")
            call.response.headers.append("X-Robots-Tag", "noindex, nofollow")
            call.respondText(page(code, fallback), ContentType.Text.Html)
        }
    }

    internal fun associations(): String? = if (fingerprints.isEmpty()) null else buildJsonArray {
        add(buildJsonObject {
            putJsonArray("relation") { add("delegate_permission/common.handle_all_urls") }
            putJsonObject("target") {
                put("namespace", "android_app"); put("package_name", PACKAGE)
                putJsonArray("sha256_cert_fingerprints") { fingerprints.forEach { add(it) } }
            }
        })
    }.toString()

    internal fun page(code: String, fallback: Boolean): String {
        val link = RoomInvites.link(origin, code, origin.startsWith("http://"))
        val uri = URI(link)
        val encodedFallback = URLEncoder.encode("$link?install=1", Charsets.UTF_8).replace("+", "%20")
        val intent = "intent://${uri.rawAuthority}${uri.rawPath}#Intent;scheme=${uri.scheme};package=$PACKAGE;S.browser_fallback_url=$encodedFallback;end"
        val install = installUrl?.let { "<p><a class=\"button\" href=\"${escape(it)}\" rel=\"noreferrer\">Get the Android app · ऐप लें</a></p>" }
            ?: "<p class=\"note\">Ask your host for the Android app, then enter this room code.<br><span lang=\"hi\">होस्ट से Android ऐप लें, फिर यह रूम कोड दर्ज करें।</span></p>"
        return """<!doctype html>
            <html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="referrer" content="no-referrer"><meta name="robots" content="noindex,nofollow"><title>Your room invitation · Tambola Together</title><style>$css</style></head>
            <body><main><div class="brandline"><span class="ball" aria-hidden="true">90</span><span class="brand">Tambola Together</span></div>
            <header class="hero"><p class="eyebrow">A seat at the table</p><h1>Your next game<br>starts together.</h1><p>You have a private room invitation. Bring your lucky number.</p></header>
            <section class="card" aria-labelledby="invite-heading"><h2 id="invite-heading">${if (fallback) "Get ready to join" else "Join your friends"}</h2><p class="note">Room code · <span lang="hi">रूम कोड</span></p><code class="code">$code</code>
            <a class="button" href="${escape(intent)}">Open Android app · <span lang="hi">ऐप खोलें</span></a>
            <p class="note">Already have the app? You can also select and copy the code above, then use Play online → Join room.</p>
            <hr><h2>${if (fallback) "App did not open?" else "New to Tambola Together?"}</h2>$install
            <ol class="steps"><li>Choose a nickname and avatar in the app.</li><li>Review the invitation and tap Join invitation.</li><li>Get ready in the lobby. Your host starts the game.</li></ol>
            <p class="note">Opening this page does not join a room or create a profile. The app checks whether the room is still open when you join. Your nickname and avatar are visible to room members.</p>
            <div class="hindi" lang="hi"><h2>दोस्तों के साथ तम्बोला</h2><p>ऐप में अपना नाम और अवतार चुनें। निमंत्रण देखकर “निमंत्रण से जुड़ें” दबाएँ। फिर लॉबी में तैयार हों; होस्ट खेल शुरू करेगा।</p><p class="note">यह पेज खोलने से आप रूम में नहीं जुड़ते और प्रोफ़ाइल नहीं बनती। जुड़ते समय ऐप रूम की उपलब्धता जाँचता है। आपका नाम और अवतार रूम के खिलाड़ियों को दिखेगा।</p></div>
            </section><footer>Free social play · Points, badges and good company<br><span lang="hi">मुफ़्त खेल · अंक, बैज और दोस्तों का साथ</span></footer></main></body></html>
        """.trimIndent()
    }
}
