package ai.havencore.companion.wakeword

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Streams 16-bit mono PCM samples into a WAV file. Patches the RIFF/data
 * sizes on [close] so the writer is safe to interrupt mid-capture — the
 * already-written samples remain valid if close() is called from a finally.
 *
 * Used by [WakeCaptureSession] to land post-wake utterances on disk before
 * handing the file to STT.
 */
class PcmWavWriter(
    private val file: File,
    private val sampleRate: Int = 16000,
    private val channels: Int = 1,
) : AutoCloseable {

    private val raf: RandomAccessFile = RandomAccessFile(file, "rw").apply {
        setLength(0)
        // 44-byte placeholder header; patched on close().
        write(ByteArray(HEADER_SIZE))
    }
    private var dataBytes: Int = 0

    fun writeFrame(samples: ShortArray, count: Int = samples.size) {
        val buf = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) buf.putShort(samples[i])
        raf.write(buf.array(), 0, buf.position())
        dataBytes += buf.position()
    }

    override fun close() {
        runCatching { patchHeader() }
        runCatching { raf.close() }
    }

    val outputFile: File get() = file
    val bytesWritten: Int get() = dataBytes

    private fun patchHeader() {
        val byteRate = sampleRate * channels * 2
        val blockAlign = (channels * 2).toShort()
        val totalSize = HEADER_SIZE + dataBytes - 8
        val buf = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(totalSize)
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16)
        buf.putShort(1) // PCM
        buf.putShort(channels.toShort())
        buf.putInt(sampleRate)
        buf.putInt(byteRate)
        buf.putShort(blockAlign)
        buf.putShort(16) // bitsPerSample
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(dataBytes)
        raf.seek(0)
        raf.write(buf.array())
    }

    companion object {
        const val HEADER_SIZE = 44
    }
}
