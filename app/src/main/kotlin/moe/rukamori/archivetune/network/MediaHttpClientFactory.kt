package moe.rukamori.archivetune.network

import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.utils.StreamClientUtils
import okhttp3.Credentials
import okhttp3.OkHttpClient

private const val CanvasPlaybackUserAgent =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"

internal fun isYouTubeMediaHost(host: String?): Boolean {
    if (host.isNullOrBlank()) return false
    val normalizedHost = host.lowercase(Locale.ROOT)
    return normalizedHost == "googlevideo.com" || normalizedHost.endsWith(".googlevideo.com") ||
        normalizedHost == "googleusercontent.com" || normalizedHost.endsWith(".googleusercontent.com") ||
        normalizedHost == "youtube.com" || normalizedHost.endsWith(".youtube.com") ||
        normalizedHost == "youtube-nocookie.com" || normalizedHost.endsWith(".youtube-nocookie.com") ||
        normalizedHost == "ytimg.com" || normalizedHost.endsWith(".ytimg.com")
}

internal fun createMediaHttpClient(): OkHttpClient =
    OkHttpClient
        .Builder()
        .dns(YouTube.dns)
        .proxy(null)
        .proxySelector(
            object : ProxySelector() {
                override fun select(uri: URI?): List<Proxy> {
                    val isYouTube = isYouTubeMediaHost(uri?.host)
                    val targetProxy = if (isYouTube) YouTube.streamOkHttpProxy else Proxy.NO_PROXY
                    return listOf(targetProxy)
                }

                override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
                    timber.log.Timber.tag("MediaHttpClient").w("Proxy connection failed for host=%s", uri?.host)
                }
            },
        )
        .proxyAuthenticator { route, response ->
            if (response.request.header("Proxy-Authorization") != null) return@proxyAuthenticator null
            val host = route?.address?.url?.host ?: response.request.url.host
            if (!isYouTubeMediaHost(host)) {
                return@proxyAuthenticator null
            }
            val username = YouTube.proxyUsername
            val password = YouTube.proxyPassword
            if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
                val credential = Credentials.basic(username, password)
                response.request
                    .newBuilder()
                    .header("Proxy-Authorization", credential)
                    .build()
            } else {
                null
            }
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            if (!isYouTubeMediaHost(request.url.host)) {
                return@addInterceptor chain.proceed(
                    request
                        .newBuilder()
                        .header("User-Agent", CanvasPlaybackUserAgent)
                        .build(),
                )
            }

            val requestProfile = StreamClientUtils.resolveRequestProfile(request.url)
            chain.proceed(
                StreamClientUtils
                    .applyRequestProfile(
                        request.newBuilder(),
                        requestProfile,
                    ).build(),
            )
        }.build()


