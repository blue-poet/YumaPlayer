package moe.rukamori.archivetune.lyrics.providers

import moe.rukamori.archivetune.innertube.models.YouTubeClient

/** Caption-specific profiles match Mochy's requests without changing audio/video playback clients. */
internal fun youtubeCaptionClients(): List<YouTubeClient> = listOf(
    YouTubeClient(
        clientName = "ANDROID_VR",
        clientVersion = "1.61.48",
        clientId = "28",
        userAgent = "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12; en_US; Oculus Quest 3; Build/SQ3A.220605.009.A1; Cronet/132.0.6808.3)",
    ),
    YouTubeClient(
        clientName = "IOS",
        clientVersion = "21.03.1",
        clientId = "5",
        userAgent = "com.google.ios.youtube/21.03.1 (iPhone16,2; U; CPU iOS 18_2 like Mac OS X;)",
        osVersion = "18.2.22C152",
    ),
    YouTubeClient.WEB_REMIX.copy(
        clientVersion = "1.20260707.12.00",
        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0",
    ),
)
