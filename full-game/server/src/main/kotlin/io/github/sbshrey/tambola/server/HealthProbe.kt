package io.github.sbshrey.tambola.server

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.system.exitProcess

/** Container probe uses the packaged JRE; no shell, credentials, curl dependency or response logging. */
object HealthProbe {
    @JvmStatic fun main(args: Array<String>) {
        val healthy = runCatching {
            require(args.size <= 1 && args.firstOrNull() in setOf(null, "live", "ready"))
            val port = (System.getenv("PORT") ?: "8080").toInt().also { require(it in 1..65535) }
            val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/health/${args.firstOrNull() ?: "live"}"))
                .timeout(Duration.ofSeconds(2)).GET().build()
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
                .send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200
        }.getOrDefault(false)
        exitProcess(if (healthy) 0 else 1)
    }
}
