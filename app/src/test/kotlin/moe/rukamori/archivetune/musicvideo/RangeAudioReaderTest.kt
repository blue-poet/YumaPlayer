package moe.rukamori.archivetune.musicvideo

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import moe.rukamori.archivetune.musicvideo.data.RangeAudioReader
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class RangeAudioReaderTest {
    private val client = mockk<OkHttpClient>()
    private val call = mockk<Call>()
    private val url = "https://example.com/audio"

    private fun respond(code: Int = 206, range: String = "bytes 0-3/4") {
        every { client.newCall(any()) } returns call
        every { call.execute() } answers {
            Response.Builder().request(Request.Builder().url(url).build())
                .protocol(Protocol.HTTP_1_1).code(code).message("test")
                .header("Content-Range", range)
                .body(byteArrayOf(1, 2, 3, 4).toResponseBody()).build()
        }
    }

    @Test fun cachedReadsPreserveTheDeclaredLengthAndDoNotRefetchTheChunk() {
        respond()
        val reader = RangeAudioReader(client, url, 4L, EmptyCoroutineContext)
        val buffer = ByteArray(2)
        assertEquals(4L, reader.getSize())
        assertEquals(2, reader.readAt(0L, buffer, 0, 2))
        assertArrayEquals(byteArrayOf(1, 2), buffer)
        assertEquals(2, reader.readAt(2L, buffer, 0, 2))
        assertArrayEquals(byteArrayOf(3, 4), buffer)
        assertEquals(-1, reader.readAt(4L, buffer, 0, 2))
        verify(exactly = 1) { client.newCall(any()) }
        reader.close()
    }

    @Test fun inconsistentRangeEndOrTotalIsRejectedInsteadOfCachingWrongBytes() {
        respond(range = "bytes 0-99/999")
        val reader = RangeAudioReader(client, url, 4L, EmptyCoroutineContext)
        assertThrows(IOException::class.java) { reader.readAt(0, ByteArray(1), 0, 1) }
    }

    @Test fun anIgnoredRangeCannotBeUsedAsDataFromTheMiddleOfTheFile() {
        respond(code = 200)
        val reader = RangeAudioReader(client, url, 70_000L, EmptyCoroutineContext)
        assertThrows(IOException::class.java) { reader.readAt(65_536L, ByteArray(1), 0, 1) }
    }

    @Test fun cancellationPreventsTheNetworkRequest() {
        val job = Job().also { it.cancel() }
        val reader = RangeAudioReader(client, url, 4L, job)
        assertThrows(CancellationException::class.java) { reader.readAt(0, ByteArray(1), 0, 1) }
        verify(exactly = 0) { client.newCall(any()) }
    }

    @Test fun invalidBuffersAndClosedReadersCannotFetchMoreBytes() {
        val reader = RangeAudioReader(client, url, 4L, EmptyCoroutineContext)
        assertThrows(IndexOutOfBoundsException::class.java) { reader.readAt(0, ByteArray(1), 1, 1) }
        reader.close()
        assertThrows(IOException::class.java) { reader.readAt(0, ByteArray(1), 0, 1) }
        verify(exactly = 0) { client.newCall(any()) }
    }
}
