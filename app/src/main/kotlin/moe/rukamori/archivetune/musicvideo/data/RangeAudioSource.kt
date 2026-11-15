package moe.rukamori.archivetune.musicvideo.data

import android.media.MediaDataSource
import java.io.EOFException
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.math.min
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request

internal class RangeAudioSource(
    client: OkHttpClient,
    url: String,
    contentLength: Long,
    jobContext: CoroutineContext,
) : MediaDataSource() {
    private val reader = RangeAudioReader(client, url, contentLength, jobContext)
    override fun getSize(): Long = reader.getSize()
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int =
        reader.readAt(position, buffer, offset, size)
    override fun close() = reader.close()
}

/** Random access for MediaExtractor, with bounded reads rather than downloading a complete song. */
internal class RangeAudioReader(
    private val client: OkHttpClient,
    private val url: String,
    private val contentLength: Long,
    private val jobContext: CoroutineContext,
) : java.io.Closeable {
    private val chunks = LinkedHashMap<Long, ByteArray>(16, 0.75f, true)
    private var downloaded = 0L
    private var closed = false

    fun getSize(): Long = contentLength

    @Synchronized
    fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        jobContext.ensureActive()
        if (offset < 0 || size < 0 || offset > buffer.size - size) {
            throw IndexOutOfBoundsException("Invalid audio sample buffer range")
        }
        if (closed) throw IOException("Audio analysis source is closed")
        if (size == 0) return 0
        if (position < 0 || position >= contentLength) return -1
        val chunkStart = position / CHUNK_SIZE * CHUNK_SIZE
        val bytes = chunks[chunkStart] ?: fetch(chunkStart).also {
            chunks[chunkStart] = it
            if (chunks.size > 16) chunks.remove(chunks.keys.first())
        }
        val start = (position - chunkStart).toInt()
        val count = min(size, bytes.size - start)
        if (count <= 0) return -1
        bytes.copyInto(buffer, offset, start, start + count)
        return count
    }

    private fun fetch(position: Long): ByteArray {
        val count = min(CHUNK_SIZE, contentLength - position).toInt()
        if (downloaded + count > MAX_BYTES) throw IOException("Audio alignment download limit reached")
        val request = Request.Builder().url(url)
            .header("Range", "bytes=$position-${position + count - 1}").build()
        return client.newCall(request).execute().use { response ->
            if (response.code != 206 && !(response.code == 200 && position == 0L)) {
                throw IOException("Audio sample range refused: ${response.code}")
            }
            // Never treat bytes from a different range as the requested part of the recording.
            val expectedRange = "bytes $position-${position + count - 1}/$contentLength"
            if (response.code == 206 &&
                !response.header("Content-Range").orEmpty().equals(expectedRange, ignoreCase = true)
            ) {
                throw IOException("Audio sample returned an unexpected range")
            }
            val result = ByteArray(count)
            val source = response.body.byteStream()
            var read = 0
            while (read < count) {
                jobContext.ensureActive()
                val next = source.read(result, read, count - read)
                if (next < 0) throw EOFException("Truncated audio sample")
                read += next
            }
            downloaded += read
            result
        }
    }

    @Synchronized
    override fun close() {
        closed = true
        chunks.clear()
    }

    private companion object {
        const val CHUNK_SIZE = 65_536L
        const val MAX_BYTES = 4L * 1024 * 1024
    }
}

