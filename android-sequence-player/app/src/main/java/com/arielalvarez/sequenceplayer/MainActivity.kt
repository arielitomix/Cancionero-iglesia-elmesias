package com.arielalvarez.sequenceplayer

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

class MainActivity : Activity() {

    companion object {
        private const val PICK_CLICK = 1001
        private const val PICK_DRUMS = 1002
        private const val PICK_BASS = 1003
    }

    private data class LoadedStem(
        val name: String,
        val sampleRate: Int,
        val samples: ShortArray
    )

    private var clickStem: LoadedStem? = null
    private var drumsStem: LoadedStem? = null
    private var bassStem: LoadedStem? = null

    @Volatile private var clickVolume = 100
    @Volatile private var drumsVolume = 100
    @Volatile private var bassVolume = 100

    @Volatile private var clickMuted = false
    @Volatile private var drumsMuted = false
    @Volatile private var bassMuted = false

    @Volatile private var clickSolo = false
    @Volatile private var drumsSolo = false
    @Volatile private var bassSolo = false

    @Volatile private var playing = false
    @Volatile private var loopEnabled = false
    @Volatile private var currentFrame = 0
    @Volatile private var generation = 0

    private var sampleRate = 48000
    private var totalFrames = 0
    private var audioTrack: AudioTrack? = null
    private var audioThread: Thread? = null

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var clickNameView: TextView
    private lateinit var drumsNameView: TextView
    private lateinit var bassNameView: TextView
    private lateinit var currentTimeView: TextView
    private lateinit var durationView: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var playButton: Button
    private lateinit var stopButton: Button
    private lateinit var loopButton: Button
    private lateinit var statusView: TextView
    private lateinit var clickMuteButton: Button
    private lateinit var drumsMuteButton: Button
    private lateinit var bassMuteButton: Button
    private lateinit var clickSoloButton: Button
    private lateinit var drumsSoloButton: Button
    private lateinit var bassSoloButton: Button

    private val progressUpdater = object : Runnable {
        override fun run() {
            val ms = if (sampleRate > 0) ((currentFrame.toLong() * 1000L) / sampleRate).toInt() else 0
            currentTimeView.text = formatTime(ms)
            if (!seekBar.isPressed) seekBar.progress = ms.coerceAtMost(seekBar.max)
            handler.postDelayed(this, 100)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setVolumeControlStream(AudioManager.STREAM_MUSIC)
        setContentView(buildUi())
        updateControls()
        handler.post(progressUpdater)
    }

    private fun buildUi(): LinearLayout {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(18))
            setBackgroundColor(Color.rgb(10, 14, 20))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        root.addView(TextView(this).apply {
            text = "SEQUENCE PLAYER · ANDROID"
            setTextColor(Color.rgb(145, 160, 178))
            textSize = 12f
        })
        root.addView(TextView(this).apply {
            text = "Sequence Player"
            setTextColor(Color.WHITE)
            textSize = 28f
        })
        root.addView(TextView(this).apply {
            text = "Prototipo nativo 0.6 · mixer en tiempo real"
            setTextColor(Color.rgb(145, 160, 178))
            textSize = 13f
            setPadding(0, 0, 0, dp(12))
        })

        root.addView(Button(this).apply {
            text = "+ CLICK WAV"
            setOnClickListener { openDocumentPicker(PICK_CLICK) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))
        clickNameView = fileLabel("Click: ninguno", ::dp)
        root.addView(clickNameView)
        root.addView(stemControls("CLICK", ::dp,
            volume = { clickVolume },
            setVolume = { clickVolume = it },
            toggleMute = { clickMuted = !clickMuted; updateStemButtons() },
            toggleSolo = { clickSolo = !clickSolo; updateStemButtons() },
            assign = { m, s -> clickMuteButton = m; clickSoloButton = s }
        ))

        root.addView(Button(this).apply {
            text = "+ DRUMS WAV"
            setOnClickListener { openDocumentPicker(PICK_DRUMS) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))
        drumsNameView = fileLabel("Drums: ninguno", ::dp)
        root.addView(drumsNameView)
        root.addView(stemControls("DRUMS", ::dp,
            volume = { drumsVolume },
            setVolume = { drumsVolume = it },
            toggleMute = { drumsMuted = !drumsMuted; updateStemButtons() },
            toggleSolo = { drumsSolo = !drumsSolo; updateStemButtons() },
            assign = { m, s -> drumsMuteButton = m; drumsSoloButton = s }
        ))

        root.addView(Button(this).apply {
            text = "+ BASS WAV"
            setOnClickListener { openDocumentPicker(PICK_BASS) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))
        bassNameView = fileLabel("Bass: ninguno", ::dp)
        root.addView(bassNameView)
        root.addView(stemControls("BASS", ::dp,
            volume = { bassVolume },
            setVolume = { bassVolume = it },
            toggleMute = { bassMuted = !bassMuted; updateStemButtons() },
            toggleSolo = { bassSolo = !bassSolo; updateStemButtons() },
            assign = { m, s -> bassMuteButton = m; bassSoloButton = s }
        ))

        val timeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        currentTimeView = TextView(this).apply {
            text = "0:00"
            setTextColor(Color.WHITE)
            textSize = 34f
        }
        durationView = TextView(this).apply {
            text = "0:00"
            setTextColor(Color.rgb(145, 160, 178))
            textSize = 18f
            gravity = Gravity.END
        }
        timeRow.addView(currentTimeView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        timeRow.addView(durationView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(timeRow)

        seekBar = SeekBar(this).apply {
            max = 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) currentTimeView.text = formatTime(progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val ms = seekBar?.progress ?: 0
                    seekToMs(ms)
                }
            })
        }
        root.addView(seekBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)))

        val transport = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        stopButton = Button(this).apply { text = "■ STOP"; setOnClickListener { stopPlayback() } }
        playButton = Button(this).apply { text = "▶ PLAY"; setOnClickListener { togglePlayback() } }
        loopButton = Button(this).apply {
            text = "↻ LOOP"
            setOnClickListener {
                loopEnabled = !loopEnabled
                text = if (loopEnabled) "↻ LOOP ✓" else "↻ LOOP"
            }
        }
        transport.addView(stopButton, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(5) })
        transport.addView(playButton, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(5) })
        transport.addView(loopButton, LinearLayout.LayoutParams(0, dp(52), 1f))
        root.addView(transport)

        statusView = TextView(this).apply {
            text = "Carga Click + Drums + Bass en WAV PCM 16-bit."
            setTextColor(Color.rgb(145, 160, 178))
            textSize = 12f
            setPadding(0, dp(10), 0, 0)
        }
        root.addView(statusView)
        root.addView(TextView(this).apply {
            text = "0.6: volumen, Mute y Solo cambian durante la reproducción sin reconstruir la mezcla."
            setTextColor(Color.rgb(110, 124, 142))
            textSize = 11f
        })

        updateStemButtons()
        return root
    }

    private fun fileLabel(textValue: String, dp: (Int) -> Int) = TextView(this).apply {
        text = textValue
        setTextColor(Color.rgb(190, 199, 210))
        textSize = 12f
        setPadding(0, dp(4), 0, dp(3))
    }

    private fun stemControls(
        label: String,
        dp: (Int) -> Int,
        volume: () -> Int,
        setVolume: (Int) -> Unit,
        toggleMute: () -> Unit,
        toggleSolo: () -> Unit,
        assign: (Button, Button) -> Unit
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(4))
        }
        val volumeText = TextView(this).apply {
            text = "$label ${volume()}%"
            setTextColor(Color.LTGRAY)
            textSize = 11f
        }
        val slider = SeekBar(this).apply {
            max = 100
            progress = volume()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    volumeText.text = "$label $progress%"
                    if (fromUser) setVolume(progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        val mute = Button(this).apply { text = "M"; setOnClickListener { toggleMute() } }
        val solo = Button(this).apply { text = "S"; setOnClickListener { toggleSolo() } }
        assign(mute, solo)

        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(volumeText)
            addView(slider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)))
        }
        row.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(mute, LinearLayout.LayoutParams(dp(46), dp(42)).apply { marginStart = dp(4) })
        row.addView(solo, LinearLayout.LayoutParams(dp(46), dp(42)).apply { marginStart = dp(4) })
        return row
    }

    private fun updateStemButtons() {
        if (::clickMuteButton.isInitialized) clickMuteButton.text = if (clickMuted) "M ✓" else "M"
        if (::drumsMuteButton.isInitialized) drumsMuteButton.text = if (drumsMuted) "M ✓" else "M"
        if (::bassMuteButton.isInitialized) bassMuteButton.text = if (bassMuted) "M ✓" else "M"
        if (::clickSoloButton.isInitialized) clickSoloButton.text = if (clickSolo) "S ✓" else "S"
        if (::drumsSoloButton.isInitialized) drumsSoloButton.text = if (drumsSolo) "S ✓" else "S"
        if (::bassSoloButton.isInitialized) bassSoloButton.text = if (bassSolo) "S ✓" else "S"
    }

    private fun openDocumentPicker(requestCode: Int) {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/wav"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/wav", "audio/x-wav", "audio/wave"))
        }, requestCode)
    }

    @Deprecated("Kept for this prototype")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || requestCode !in listOf(PICK_CLICK, PICK_DRUMS, PICK_BASS)) return
        val uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) { }

        val name = getDisplayName(uri) ?: "Archivo WAV"
        if (!name.lowercase().endsWith(".wav")) {
            Toast.makeText(this, "Esta versión solo acepta WAV", Toast.LENGTH_LONG).show()
            return
        }

        statusView.text = "Cargando $name…"
        Thread {
            try {
                val stem = loadPcm16Wav(uri, name)
                runOnUiThread {
                    when (requestCode) {
                        PICK_CLICK -> { clickStem = stem; clickNameView.text = "Click: $name" }
                        PICK_DRUMS -> { drumsStem = stem; drumsNameView.text = "Drums: $name" }
                        PICK_BASS -> { bassStem = stem; bassNameView.text = "Bass: $name" }
                    }
                    validateLoadedStems()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusView.text = "No se pudo cargar: ${e.message ?: "WAV incompatible"}"
                    Toast.makeText(this, statusView.text, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun validateLoadedStems() {
        val stems = listOfNotNull(clickStem, drumsStem, bassStem)
        if (stems.isEmpty()) {
            updateControls()
            return
        }
        val rates = stems.map { it.sampleRate }.toSet()
        if (rates.size > 1) {
            statusView.text = "Los WAV deben usar el mismo sample rate."
            updateControls()
            return
        }
        sampleRate = stems.first().sampleRate
        totalFrames = stems.maxOf { it.samples.size }
        val durationMs = ((totalFrames.toLong() * 1000L) / sampleRate).toInt()
        seekBar.max = durationMs.coerceAtLeast(1)
        durationView.text = formatTime(durationMs)

        if (allLoaded()) {
            currentFrame = 0
            statusView.text = "3 WAV listos. Mixer en tiempo real preparado."
        } else {
            statusView.text = "${stems.size} de 3 WAV cargados."
        }
        updateControls()
    }

    private fun allLoaded() = clickStem != null && drumsStem != null && bassStem != null &&
        setOf(clickStem!!.sampleRate, drumsStem!!.sampleRate, bassStem!!.sampleRate).size == 1

    private fun togglePlayback() {
        if (!allLoaded()) return
        if (playing) pausePlayback() else startPlayback()
    }

    private fun startPlayback() {
        if (playing || !allLoaded()) return
        playing = true
        playButton.text = "❚❚ PAUSA"
        statusView.text = "Reproduciendo · mixer en tiempo real."
        startAudioEngine()
    }

    private fun pausePlayback() {
        playing = false
        generation++
        audioTrack?.pause()
        audioTrack?.flush()
        audioTrack?.release()
        audioTrack = null
        playButton.text = "▶ PLAY"
        statusView.text = "Pausado."
    }

    private fun stopPlayback() {
        playing = false
        generation++
        audioTrack?.pause()
        audioTrack?.flush()
        audioTrack?.release()
        audioTrack = null
        currentFrame = 0
        seekBar.progress = 0
        currentTimeView.text = "0:00"
        playButton.text = "▶ PLAY"
        statusView.text = "Detenido."
    }

    private fun seekToMs(ms: Int) {
        val newFrame = ((ms.toLong() * sampleRate) / 1000L).toInt().coerceIn(0, totalFrames)
        val wasPlaying = playing
        generation++
        audioTrack?.pause()
        audioTrack?.flush()
        audioTrack?.release()
        audioTrack = null
        currentFrame = newFrame
        if (wasPlaying) startAudioEngine()
    }

    private fun startAudioEngine() {
        val click = clickStem ?: return
        val drums = drumsStem ?: return
        val bass = bassStem ?: return
        val localGeneration = ++generation

        val minBufferBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(minBufferBytes * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack = track
        track.play()

        audioThread = Thread {
            val framesPerBlock = 1024
            val out = ShortArray(framesPerBlock * 2)

            while (playing && localGeneration == generation) {
                var frame = currentFrame
                if (frame >= totalFrames) {
                    if (loopEnabled) {
                        currentFrame = 0
                        frame = 0
                    } else {
                        playing = false
                        handler.post {
                            playButton.text = "▶ PLAY"
                            statusView.text = "Reproducción terminada."
                        }
                        break
                    }
                }

                val frames = minOf(framesPerBlock, totalFrames - frame)
                val anySolo = clickSolo || drumsSolo || bassSolo
                val clickGain = effectiveGain(clickVolume, clickMuted, clickSolo, anySolo)
                val drumsGain = effectiveGain(drumsVolume, drumsMuted, drumsSolo, anySolo)
                val bassGain = effectiveGain(bassVolume, bassMuted, bassSolo, anySolo)

                var p = 0
                for (i in 0 until frames) {
                    val idx = frame + i
                    val clickSample = if (idx < click.samples.size) click.samples[idx].toInt() else 0
                    val drumsSample = if (idx < drums.samples.size) drums.samples[idx].toInt() else 0
                    val bassSample = if (idx < bass.samples.size) bass.samples[idx].toInt() else 0

                    val left = (clickSample * clickGain).roundToInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    val right = ((drumsSample * drumsGain) + (bassSample * bassGain))
                        .roundToInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

                    out[p++] = left.toShort()
                    out[p++] = right.toShort()
                }

                val written = track.write(out, 0, frames * 2, AudioTrack.WRITE_BLOCKING)
                if (written < 0) break
                currentFrame += frames
            }

            try { track.stop() } catch (_: Exception) { }
            try { track.release() } catch (_: Exception) { }
            if (audioTrack === track) audioTrack = null
        }.apply {
            name = "SequencePlayerAudio"
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun effectiveGain(volume: Int, muted: Boolean, solo: Boolean, anySolo: Boolean): Float {
        if (muted) return 0f
        if (anySolo && !solo) return 0f
        return volume.coerceIn(0, 100) / 100f
    }

    private fun updateControls() {
        val ready = allLoaded()
        playButton.isEnabled = ready
        stopButton.isEnabled = ready
        loopButton.isEnabled = ready
        seekBar.isEnabled = ready
    }

    private fun loadPcm16Wav(uri: Uri, name: String): LoadedStem {
        val input = BufferedInputStream(contentResolver.openInputStream(uri) ?: error("No se pudo abrir el archivo"), 128 * 1024)
        input.use { stream ->
            if (readFourCc(stream) != "RIFF") error("El archivo no es WAV RIFF")
            readLeInt(stream)
            if (readFourCc(stream) != "WAVE") error("Formato WAV no válido")

            var audioFormat = -1
            var channels = -1
            var rate = -1
            var bits = -1

            while (true) {
                val chunkId = readFourCc(stream)
                val chunkSize = readLeInt(stream)
                when (chunkId) {
                    "fmt " -> {
                        if (chunkSize < 16) error("Chunk fmt inválido")
                        audioFormat = readLeShort(stream)
                        channels = readLeShort(stream)
                        rate = readLeInt(stream)
                        readLeInt(stream)
                        readLeShort(stream)
                        bits = readLeShort(stream)
                        skipFully(stream, chunkSize - 16)
                        if ((chunkSize and 1) == 1) skipFully(stream, 1)
                    }
                    "data" -> {
                        if (audioFormat != 1) error("Solo WAV PCM sin compresión")
                        if (bits != 16) error("Solo WAV PCM de 16 bits")
                        if (channels < 1) error("Canales inválidos")
                        if (rate <= 0) error("Sample rate inválido")

                        val bytesPerFrame = channels * 2
                        val frameCount = chunkSize / bytesPerFrame
                        val samples = ShortArray(frameCount)
                        val frameBuffer = ByteArray(bytesPerFrame)

                        for (frame in 0 until frameCount) {
                            readFully(stream, frameBuffer, 0, bytesPerFrame)
                            var sum = 0
                            var off = 0
                            repeat(channels) {
                                val lo = frameBuffer[off].toInt() and 0xff
                                val hi = frameBuffer[off + 1].toInt()
                                val sample = ((hi shl 8) or lo).toShort().toInt()
                                sum += sample
                                off += 2
                            }
                            samples[frame] = (sum / channels)
                                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                                .toShort()
                        }
                        return LoadedStem(name, rate, samples)
                    }
                    else -> {
                        skipFully(stream, chunkSize)
                        if ((chunkSize and 1) == 1) skipFully(stream, 1)
                    }
                }
            }
        }
    }

    private fun readFourCc(input: InputStream): String {
        val bytes = ByteArray(4)
        readFully(input, bytes, 0, 4)
        return String(bytes, Charsets.US_ASCII)
    }

    private fun readLeInt(input: InputStream): Int {
        val b0 = input.read(); val b1 = input.read(); val b2 = input.read(); val b3 = input.read()
        if (b0 < 0 || b1 < 0 || b2 < 0 || b3 < 0) throw EOFException()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    private fun readLeShort(input: InputStream): Int {
        val b0 = input.read(); val b1 = input.read()
        if (b0 < 0 || b1 < 0) throw EOFException()
        return b0 or (b1 shl 8)
    }

    private fun skipFully(input: InputStream, bytes: Int) {
        var remaining = bytes
        while (remaining > 0) {
            val skipped = input.skip(remaining.toLong()).toInt()
            if (skipped > 0) remaining -= skipped else {
                if (input.read() < 0) throw EOFException()
                remaining--
            }
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray, offset: Int, length: Int) {
        var total = 0
        while (total < length) {
            val read = input.read(buffer, offset + total, length - total)
            if (read < 0) throw EOFException()
            total += read
        }
    }

    private fun getDisplayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index)
        }
        return uri.lastPathSegment
    }

    private fun formatTime(ms: Int): String {
        val sec = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(sec / 60, sec % 60)
    }

    override fun onDestroy() {
        playing = false
        generation++
        handler.removeCallbacks(progressUpdater)
        try { audioTrack?.pause() } catch (_: Exception) { }
        try { audioTrack?.flush() } catch (_: Exception) { }
        try { audioTrack?.release() } catch (_: Exception) { }
        audioTrack = null
        super.onDestroy()
    }
}
