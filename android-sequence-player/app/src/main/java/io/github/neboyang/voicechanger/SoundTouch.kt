package io.github.neboyang.voicechanger

class SoundTouch(sampleRate: Int, private val channels: Int) : AutoCloseable {
    private var handle: Long

    init {
        require(sampleRate in 8000..192000)
        require(channels == 1 || channels == 2)
        handle = nativeNew()
        nativeSetSampleRate(handle, sampleRate)
        nativeSetChannels(handle, channels)
    }

    fun setPitchSemiTones(semiTones: Float) {
        require(semiTones in -24f..24f)
        nativeSetPitchSemiTones(checkedHandle(), semiTones)
    }

    fun setTempo(tempo: Float) {
        require(tempo > 0.1f && tempo <= 10f)
        nativeSetTempo(checkedHandle(), tempo)
    }

    fun setRate(rate: Float) {
        require(rate > 0.1f && rate <= 10f)
        nativeSetRate(checkedHandle(), rate)
    }

    fun putSamples(samples: ShortArray, frames: Int = samples.size / channels) {
        require(frames >= 0 && frames * channels <= samples.size)
        nativePutSamples(checkedHandle(), samples, frames)
    }

    fun receiveSamples(buffer: ShortArray): Int =
        nativeReceiveSamples(checkedHandle(), buffer, buffer.size / channels)

    fun flush() = nativeFlush(checkedHandle())
    fun availableFrames(): Int = nativeNumSamples(checkedHandle())

    override fun close() {
        if (handle != 0L) {
            nativeDelete(handle)
            handle = 0L
        }
    }

    private fun checkedHandle(): Long {
        check(handle != 0L)
        return handle
    }

    companion object {
        init { System.loadLibrary("soundtouch") }

        @JvmStatic val version: String get() = nativeGetVersion()
        @JvmStatic private external fun nativeNew(): Long
        @JvmStatic private external fun nativeDelete(handle: Long)
        @JvmStatic private external fun nativeSetSampleRate(handle: Long, sampleRate: Int)
        @JvmStatic private external fun nativeSetChannels(handle: Long, channels: Int)
        @JvmStatic private external fun nativeSetTempo(handle: Long, tempo: Float)
        @JvmStatic private external fun nativeSetRate(handle: Long, rate: Float)
        @JvmStatic private external fun nativeSetPitchSemiTones(handle: Long, semiTones: Float)
        @JvmStatic private external fun nativePutSamples(handle: Long, samples: ShortArray, numFrames: Int)
        @JvmStatic private external fun nativeReceiveSamples(handle: Long, out: ShortArray, maxFrames: Int): Int
        @JvmStatic private external fun nativeFlush(handle: Long)
        @JvmStatic private external fun nativeNumSamples(handle: Long): Int
        @JvmStatic private external fun nativeGetVersion(): String
    }
}
