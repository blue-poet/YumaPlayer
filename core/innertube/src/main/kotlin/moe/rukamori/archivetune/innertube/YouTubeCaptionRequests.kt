package moe.rukamori.archivetune.innertube

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import moe.rukamori.archivetune.innertube.models.YouTubeClient

/** Caption links are fetched using the same transport/proxy session as their player response. */
suspend fun YouTube.captionText(
    baseUrl: String,
    client: YouTubeClient,
    token: String? = null,
): String {
    val transport = innerTube.http
    return transport.withRetry {
        transport.client.get(baseUrl) {
            url.parameters["fmt"] = "json3"
            if (token != null) {
                url.parameters["pot"] = token
                url.parameters["potc"] = "1"
                url.parameters["c"] = client.clientName
            }
            header("User-Agent", client.userAgent)
            header("Accept-Language", "en-US,en;q=0.9")
            header("Cache-Control", "no-cache")
        }.bodyAsText()
    }
}
