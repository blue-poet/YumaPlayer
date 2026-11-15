package moe.rukamori.archivetune.lyrics

import moe.rukamori.archivetune.innertube.models.response.PlayerResponse.Captions.CaptionTrack
import moe.rukamori.archivetune.lyrics.providers.youtubeCaptionClients
import moe.rukamori.archivetune.lyrics.providers.captionTracksForLyrics
import moe.rukamori.archivetune.lyrics.providers.watchCaptionData
import kotlinx.coroutines.test.runTest
import moe.rukamori.archivetune.constants.DefaultLyricsProviderOrder
import moe.rukamori.archivetune.constants.PreferredLyricsProvider
import moe.rukamori.archivetune.constants.deserializeLyricsProviderOrder
import moe.rukamori.archivetune.lyrics.parsing.LrcParser
import moe.rukamori.archivetune.lyrics.providers.YouTubeAutoCaptionsProvider
import moe.rukamori.archivetune.lyrics.providers.autoCaptionsToLrc
import org.junit.Assert.*
import org.junit.Test

class YouTubeAutoCaptionsTest {
    @Test fun captionsRetainTheirVideoTimestampsAndJoinWordSegments() {
        val lyrics = autoCaptionsToLrc("""
            {"events":[
              {"tStartMs":1234,"segs":[{"utf8":"Hello"},{"utf8":" world","tOffsetMs":200}]},
              {"tStartMs":61234,"segs":[{"utf8":"Next line"}]}
            ]}
        """)
        assertEquals("[00:01.234]Hello world\n[01:01.234]Next line", lyrics)
        assertTrue(LrcParser.isLineSyncedLrc(lyrics))
        assertEquals(listOf(1234L, 61234L), LrcParser.parseLyrics(lyrics).map { it.time })
    }

    @Test fun soundLabelsAndRepeatedRollingLinesAreRemoved() {
        val lyrics = autoCaptionsToLrc("""
            {"events":[
              {"tStartMs":0,"segs":[{"utf8":"[Music] ♪"}]},
              {"tStartMs":1000,"segs":[{"utf8":" Sing   along\n"}]},
              {"tStartMs":1200,"segs":[{"utf8":"Sing along"}]},
              {"tStartMs":3000,"segs":[{"utf8":"[Applause] More ♪"}]}
            ]}
        """)
        assertEquals("[00:01.000]Sing along\n[00:03.000]More", lyrics)
    }

    @Test fun emptyAndWindowEventsDoNotBecomeLyrics() {
        assertEquals("", autoCaptionsToLrc("""{"events":[{"wWinId":1},{"tStartMs":0,"segs":[{"utf8":"\n"}]}]}"""))
        assertEquals("", autoCaptionsToLrc("{}"))
        assertEquals("", autoCaptionsToLrc("""{"events":[{"tStartMs":-100,"segs":[{"utf8":"invalid"}]}]}"""))
    }

    @Test fun eventsAreOrderedWithoutLosingUnicode() {
        val lyrics = autoCaptionsToLrc("""
            {"events":[
              {"tStartMs":2000,"segs":[{"utf8":"星空"}]},
              {"tStartMs":1000,"segs":[{"utf8":"你好"}]}
            ]}
        """)
        assertEquals("[00:01.000]你好\n[00:02.000]星空", lyrics)
    }

    @Test fun invalidAndLocalIdsFailWithoutLookingUpAnotherSong() = runTest {
        assertTrue(YouTubeAutoCaptionsProvider.getLyrics("local:123", "Song", "Artist", null, 120).isFailure)
    }

    @Test fun uploadedCaptionsInTheSungLanguageArePreferredOverAsrAndTranslations() {
        val translated = CaptionTrack("https://example.com/ar", "ar")
        val uploaded = CaptionTrack("https://example.com/en", "en")
        val auto = CaptionTrack("https://example.com/asr", "en", "asr")
        assertEquals(listOf(uploaded, auto), captionTracksForLyrics(listOf(translated, uploaded, auto), "ar"))
        assertEquals(listOf(uploaded), captionTracksForLyrics(listOf(translated, uploaded), "en-US"))
    }

    @Test fun watchFallbackReadsOnlyTheRequestedVideoAndHandlesBracesInsideStrings() {
        val html = """
            <script>var ytInitialPlayerResponse = {
              "videoDetails":{"videoId":"KYV7X0Nccp4","title":"A } brace"},
              "responseContext":{"visitorData":"test-visitor"},
              "captions":{"playerCaptionsTracklistRenderer":{"captionTracks":[
                {"baseUrl":"https://example.com/caption","languageCode":"en","kind":"asr","extra":true}
              ]}}
            }; otherJavaScript();</script>
        """
        val watch = watchCaptionData(html, "KYV7X0Nccp4")!!
        assertEquals("test-visitor", watch.visitorData)
        assertEquals("asr", watch.tracks.single().kind)
        assertNull(watchCaptionData(html, "abcdefghijk"))
        assertNull(watchCaptionData("<html>No player response</html>", "KYV7X0Nccp4"))
    }

    @Test fun failedAutoCaptionChecksCanBeRetriedWhenMenuReopens() {
        val id = "caption-retry-test"
        LyricsCandidatesManager.updateCache(
            id, PreferredLyricsProvider.YOUTUBE_AUTO_CAPTIONS,
            CandidateState(isChecking = false),
        )
        assertFalse(LyricsCandidatesManager.getCached(id)!!.containsKey(PreferredLyricsProvider.YOUTUBE_AUTO_CAPTIONS))
        LyricsCandidatesManager.updateCache(
            id, PreferredLyricsProvider.YOUTUBE_AUTO_CAPTIONS,
            CandidateState(isChecking = false, lyrics = "[00:01.000]Hello", syncKind = LyricsSyncKind.LINE),
        )
        assertTrue(LyricsCandidatesManager.getCached(id)!!.containsKey(PreferredLyricsProvider.YOUTUBE_AUTO_CAPTIONS))
    }

    @Test fun captionRequestsUseTheMochyClientVersions() {
        val clients = youtubeCaptionClients()
        assertEquals(listOf("ANDROID_VR", "IOS", "WEB_REMIX"), clients.map { it.clientName })
        assertEquals("21.03.1", clients[1].clientVersion)
        assertEquals("18.2.22C152", clients[1].osVersion)
        assertEquals("1.20260707.12.00", clients[2].clientVersion)
    }

    @Test fun savedProviderOrderCannotPromoteSpeechRecognitionAboveCuratedLyrics() {
        val order = deserializeLyricsProviderOrder("YOUTUBE_AUTO_CAPTIONS,UNISON,KUGOU")
        assertEquals(PreferredLyricsProvider.UNISON, order.first())
        assertEquals(PreferredLyricsProvider.YOUTUBE_AUTO_CAPTIONS, order.last())
    }

    @Test fun newProviderIsLastForNewAndExistingProviderOrders() {
        assertEquals(PreferredLyricsProvider.YOUTUBE_AUTO_CAPTIONS, DefaultLyricsProviderOrder.last())
        assertEquals(
            PreferredLyricsProvider.YOUTUBE_AUTO_CAPTIONS,
            deserializeLyricsProviderOrder("UNISON,KUGOU,LRCLIB").last(),
        )
        assertSame(
            YouTubeAutoCaptionsProvider,
            LyricsCandidatesManager.resolveProvider(PreferredLyricsProvider.YOUTUBE_AUTO_CAPTIONS),
        )
    }
}
