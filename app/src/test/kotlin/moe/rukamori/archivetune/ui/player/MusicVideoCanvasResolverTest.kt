package moe.rukamori.archivetune.ui.player

import moe.rukamori.archivetune.innertube.models.Artist
import moe.rukamori.archivetune.innertube.models.SongItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import moe.rukamori.archivetune.innertube.models.WatchEndpoint

class MusicVideoCanvasResolverTest {
    private fun video(title: String, artist: String = "Artist") =
        SongItem(id = "abcdefghijk", title = title, artists = listOf(Artist(artist, null)), thumbnail = "")

    @Test fun manualChoiceOverridesSearchEvenWhenNotAnAutomaticMatch() {
        val searchResults = listOf(video("My Song (Official Video)"))
        val selected = musicVideoCandidates(searchResults, "My Song", "Artist", "manual12345")
        org.junit.Assert.assertEquals(listOf("manual12345"), selected.map { it.id })
    }

    @Test fun unavailableManualChoiceIsNotSilentlyReplacedWithAutomaticCandidate() {
        val selected = musicVideoCandidates(emptyList(), "My Song", "Artist", "manual12345")
        org.junit.Assert.assertEquals(listOf("manual12345"), selected.map { it.id })
    }

    @Test fun clearingManualChoiceRestoresOriginalAutomaticSearchOrderAndLimit() {
        val videos = (1..4).map { index -> video("My Song (Official Video)").copy(id = "result0000$index") }
        org.junit.Assert.assertEquals(
            videos.take(3),
            musicVideoCandidates(videos, "My Song", "Artist", ""),
        )
        org.junit.Assert.assertEquals(
            videos.take(3),
            musicVideoCandidates(videos, "My Song", "Artist", "invalid"),
        )
    }

    @Test fun acceptsMatchingOfficialVideo() {
        assertTrue(matchesMusicVideoCanvas(video("Artist - My Song (Official Music Video)"), "My Song", "Artist"))
    }

    @Test fun rejectsUnrelatedTitleOrArtist() {
        assertFalse(matchesMusicVideoCanvas(video("Another Song (Official Video)"), "My Song", "Artist"))
        assertFalse(matchesMusicVideoCanvas(video("My Song (Official Video)", "Someone Else"), "My Song", "Artist"))
    }

    @Test fun rejectsAudioUploadsAndUnlabelledVideos() {
        assertFalse(matchesMusicVideoCanvas(video("My Song (Official Audio)"), "My Song", "Artist"))
        assertFalse(matchesMusicVideoCanvas(video("My Song"), "My Song", "Artist"))
    }

    @Test fun acceptsOfficialVideoWithoutVideoLabel() {
        val official = video("Lost The Breakup", "Maisie Peters").copy(
            endpoint = WatchEndpoint(
                watchEndpointMusicSupportedConfigs = WatchEndpoint.WatchEndpointMusicSupportedConfigs(
                    WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig("MUSIC_VIDEO_TYPE_OMV"),
                ),
            ),
        )
        assertTrue(matchesMusicVideoCanvas(official, "Lost The Breakup", "Maisie Peters"))
    }

    @Test fun ignoresAudioAndFeaturedArtistSuffixesWhenMatching() {
        assertTrue(matchesMusicVideoCanvas(
            video("Maisie Peters - Lost The Breakup [Official Video]", "Maisie Peters"),
            "Lost The Breakup (Official Audio)",
            "Maisie Peters",
        ))
        assertTrue(matchesMusicVideoCanvas(video("My Song (Official Video)"), "My Song (feat. Guest)", "Artist"))
    }

    @Test fun matchesUnicodeTitlesAndVisualizers() {
        assertTrue(matchesMusicVideoCanvas(video("星空 (Visualizer)", "歌手"), "星空", "歌手"))
    }
}
