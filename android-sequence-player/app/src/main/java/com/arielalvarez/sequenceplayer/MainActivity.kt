package com.arielalvarez.sequenceplayer

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.MediaPlayer
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
import java.io.BufferedOutputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.roundToInt

class MainActivity : Activity() {

    companion object {
        private const val PICK_CLICK = 1001
        private const val PICK_DRUMS = 1002
        private const val PICK_BASS = 1003
    }

    private var clickUri: Uri? = null
    private var drumsUri: Uri? = null
    private var bassUri: Uri? = null

    private var clickVolume = 100
    private var drumsVolume = 100
    private var bassVolume = 100

    private var clickMuted = false
    private var drumsMuted = false
    private var bassMuted = false

    private var clickSolo = false
    private var drumsSolo = false
    private var bassSolo = false

    private var mediaPlayer: MediaPlayer? = null
    private var loopEnabled = false
    private var mixReady = false
    private var isBuildingMix = false
    private var rebuildRequest = 0

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
            mediaPlayer?.takeIf { mixReady }?.let { player ->
                currentTimeView.text = formatTime(player.currentPosition)
                if (!seekBar.isPressed && player.duration > 0) {
                    seekBar.progress = player.currentPosition.coerceAtMost(seekBar.max)
                }
            }
            handler.postDelayed(this, 100)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            text = "Prototipo nativo 0.5 · volumen + mute + solo"
            setTextColor(Color.rgb(145, 160, 178))
            textSize = 13f
            setPadding(0, 0, 0, dp(12))
        })

        val clickPick = Button(this).apply {
            text = "+ CLICK WAV"
            setOnClickListener { openDocumentPicker(PICK_CLICK) }
        }
        root.addView(clickPick, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)))
        clickNameView = createFileLabel("Click: ninguno", ::dp)
        root.addView(clickNameView)
        root.addView(createStemControls("CLICK", ::dp,
            onVolume = { clickVolume = it; rebuildFromControls() },
            onMute = { clickMuted = !clickMuted; updateStemButtons(); rebuildFromControls() },
            onSolo = { clickSolo = !clickSolo; updateStemButtons(); rebuildFromControls() },
            assignButtons = { m, s -> clickMuteButton = m; clickSoloButton = s }
        ))

        val drumsPick = Button(this).apply {
            text = "+ DRUMS WAV"
            setOnClickListener { openDocumentPicker(PICK_DRUMS) }
        }
        root.addView(drumsPick, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)))
        drumsNameView = createFileLabel("Drums: ninguno", ::dp)
        root.addView(drumsNameView)
        root.addView(createStemControls("DRUMS", ::dp,
            onVolume = { drumsVolume = it; rebuildFromControls() },
            onMute = { drumsMuted = !drumsMuted; updateStemButtons(); rebuildFromControls() },
            onSolo = { drumsSolo = !drumsSolo; updateStemButtons(); rebuildFromControls() },
            assignButtons = { m, s -> drumsMuteButton = m; drumsSoloButton = s }
        ))

        val bassPick = Button(this).apply {
            text = "+ BASS WAV"
            setOnClickListener { openDocumentPicker(PICK_BASS) }
        }
        root.addView(bassPick, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)))
        bassNameView = createFileLabel("Bass: ninguno", ::dp)
        root.addView(bassNameView)
        root.addView(createStemControls("BASS", ::dp,
            onVolume = { bassVolume = it; rebuildFromControls() },
            onMute = { bassMuted = !bassMuted; updateStemButtons(); rebuildFromControls() },
            onSolo = { bassSolo = !bassSolo; updateStemButtons(); rebuildFromControls() },
            assignButtons = { m, s -> bassMuteButton = m; bassSoloButton = s }
        ))

        val timeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, 0)
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
                    mediaPlayer?.seekTo(seekBar?.progress ?: 0)
                }
            })
        }
        root.addView(seekBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        stopButton = Button(this).apply { text = "■ STOP"; setOnClickListener { stopPlayback() } }
        playButton = Button(this).apply { text = "▶ PLAY"; setOnClickListener { togglePlayback() } }
        loopButton = Button(this).apply {
            text = "↻ LOOP"
            setOnClickListener {
                loopEnabled = !loopEnabled
                mediaPlayer?.isLooping = loopEnabled
                text = if (loopEnabled) "↻ LOOP ✓" else "↻ LOOP"
            }
        }
        controls.addView(stopButton, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginEnd = dp(5) })
        controls.addView(playButton, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginEnd = dp(5) })
        controls.addView(loopButton, LinearLayout.LayoutParams(0, dp(54), 1f))
        root.addView(controls)

        statusView = TextView(this).apply {
            text = "Carga Click + Drums + Bass en WAV PCM 16-bit."
            setTextColor(Color.rgb(145, 160, 178))
            textSize = 12f
            setPadding(0, dp(12), 0, 0)
        }
        root.addView(statusView)
        root.addView(TextView(this).apply {
            text = "Salida: Click = L · Drums + Bass = R · mismo reloj de audio."
            setTextColor(Color.rgb(110, 124, 142))
            textSize = 11f
        })

        updateStemButtons()
        return root
    }

    private fun createFileLabel(textValue: String, dp: (Int) -> Int): TextView = TextView(this).apply {
        text = textValue
        setTextColor(Color.rgb(190, 199, 210))
        textSize = 12f
        setPadding(0, dp(5), 0, dp(4))
    }

    private fun createStemControls(
        label: String,
        dp: (Int) -> Int,
        onVolume: (Int) -> Unit,
        onMute: () -> Unit,
        onSolo: () -> Unit,
        assignButtons: (Button, Button) -> Unit
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(5))
        }
        val volumeLabel = TextView(this).apply {
            text = "$label 100%"
            setTextColor(Color.LTGRAY)
            textSize = 11f
        }
        val volume = SeekBar(this).apply {
            max = 100
            progress = 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    volumeLabel.text = "$label $progress%"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) { onVolume(seekBar?.progress ?: 100) }
            })
        }
        val mute = Button(this).apply { text = "M"; setOnClickListener { onMute() } }
        val solo = Button(this).apply { text = "S"; setOnClickListener { onSolo() } }
        assignButtons(mute, solo)

        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(volumeLabel)
            addView(volume, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)))
        }
        row.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(mute, LinearLayout.LayoutParams(dp(48), dp(44)).apply { marginStart = dp(4) })
        row.addView(solo, LinearLayout.LayoutParams(dp(48), dp(44)).apply { marginStart = dp(4) })
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
        when (requestCode) {
            PICK_CLICK -> { clickUri = uri; clickNameView.text = "Click: $name" }
            PICK_DRUMS -> { drumsUri = uri; drumsNameView.text = "Drums: $name" }
            PICK_BASS -> { bassUri = uri; bassNameView.text = "Bass: $name" }
        }
        if (allLoaded()) buildSynchronizedMix(0, false) else {
            statusView.text = "${listOf(clickUri, drumsUri, bassUri).count { it != null }} de 3 WAV cargados."
        }
    }

    private fun allLoaded() = clickUri != null && drumsUri != null && bassUri != null

    private fun rebuildFromControls() {
        if (!allLoaded() || isBuildingMix) return
        val old = mediaPlayer
        val position = old?.currentPosition ?: 0
        val wasPlaying = old?.isPlaying == true
        buildSynchronizedMix(position, wasPlaying)
    }

    private fun buildSynchronizedMix(resumePosition: Int, resumePlaying: Boolean) {
        val click = clickUri ?: return
        val drums = drumsUri ?: return
        val bass = bassUri ?: return
        val request = ++rebuildRequest

        isBuildingMix = true
        mixReady = false
        updateControls()
        mediaPlayer?.pause()
        statusView.text = "Preparando mezcla…"

        val snapshot = MixerState(clickVolume, drumsVolume, bassVolume, clickMuted, drumsMuted, bassMuted, clickSolo, drumsSolo, bassSolo)
        Thread {
            try {
                val outputFile = File(cacheDir, "sequence_sync_05_$request.wav")
                createStereoSyncWav(click, drums, bass, outputFile, snapshot)
                runOnUiThread {
                    if (request != rebuildRequest) return@runOnUiThread
                    preparePlayer(outputFile, resumePosition, resumePlaying)
                    isBuildingMix = false
                }
            } catch (e: Exception) {
                runOnUiThread {
                    isBuildingMix = false
                    mixReady = false
                    updateControls()
                    statusView.text = "No se pudo preparar: ${e.message ?: "WAV incompatible"}"
                }
            }
        }.start()
    }

    private fun preparePlayer(file: File, resumePosition: Int, resumePlaying: Boolean) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            isLooping = loopEnabled
            setOnPreparedListener { player ->
                seekBar.max = player.duration.coerceAtLeast(1)
                durationView.text = formatTime(player.duration)
                val pos = resumePosition.coerceIn(0, player.duration.coerceAtLeast(0))
                player.seekTo(pos)
                seekBar.progress = pos
                currentTimeView.text = formatTime(pos)
                mixReady = true
                updateControls()
                if (resumePlaying) {
                    player.start()
                    playButton.text = "❚❚ PAUSA"
                } else {
                    playButton.text = "▶ PLAY"
                }
                statusView.text = "Mezcla lista · controles aplicados."
            }
            setOnCompletionListener { if (!loopEnabled) playButton.text = "▶ PLAY" }
            setOnErrorListener { _, _, _ -> mixReady = false; updateControls(); true }
            prepareAsync()
        }
    }

    private fun togglePlayback() {
        val player = mediaPlayer ?: return
        if (!mixReady) return
        if (player.isPlaying) {
            player.pause(); playButton.text = "▶ PLAY"; statusView.text = "Pausado."
        } else {
            player.start(); playButton.text = "❚❚ PAUSA"; statusView.text = "Reproduciendo."
        }
    }

    private fun stopPlayback() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) player.pause()
        player.seekTo(0)
        seekBar.progress = 0
        currentTimeView.text = "0:00"
        playButton.text = "▶ PLAY"
    }

    private fun updateControls() {
        val enabled = mixReady && !isBuildingMix
        if (::playButton.isInitialized) playButton.isEnabled = enabled
        if (::stopButton.isInitialized) stopButton.isEnabled = enabled
        if (::loopButton.isInitialized) loopButton.isEnabled = enabled
        if (::seekBar.isInitialized) seekBar.isEnabled = enabled
    }

    private data class MixerState(
        val clickVolume: Int, val drumsVolume: Int, val bassVolume: Int,
        val clickMuted: Boolean, val drumsMuted: Boolean, val bassMuted: Boolean,
        val clickSolo: Boolean, val drumsSolo: Boolean, val bassSolo: Boolean
    )

    private data class WavReader(
        val input: BufferedInputStream,
        val sampleRate: Int,
        val channels: Int,
        val frames: Long,
        var framesRead: Long = 0
    ) {
        private val frameBuffer = ByteArray(channels * 2)
        fun readMonoSample(): Short {
            if (framesRead >= frames) return 0
            var total = 0
            while (total < frameBuffer.size) {
                val read = input.read(frameBuffer, total, frameBuffer.size - total)
                if (read < 0) throw EOFException()
                total += read
            }
            var sum = 0
            var offset = 0
            repeat(channels) {
                val lo = frameBuffer[offset].toInt() and 0xff
                val hi = frameBuffer[offset + 1].toInt()
                sum += (((hi shl 8) or lo).toShort()).toInt()
                offset += 2
            }
            framesRead++
            return (sum / channels).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun createStereoSyncWav(clickUri: Uri, drumsUri: Uri, bassUri: Uri, outputFile: File, state: MixerState) {
        val clickInput = BufferedInputStream(contentResolver.openInputStream(clickUri) ?: error("No se pudo abrir click"), 64 * 1024)
        val drumsInput = BufferedInputStream(contentResolver.openInputStream(drumsUri) ?: error("No se pudo abrir drums"), 64 * 1024)
        val bassInput = BufferedInputStream(contentResolver.openInputStream(bassUri) ?: error("No se pudo abrir bass"), 64 * 1024)

        clickInput.use { cs -> drumsInput.use { ds -> bassInput.use { bs ->
            val click = parsePcm16Wav(cs)
            val drums = parsePcm16Wav(ds)
            val bass = parsePcm16Wav(bs)
            if (setOf(click.sampleRate, drums.sampleRate, bass.sampleRate).size != 1) error("Los WAV deben tener el mismo sample rate")

            val totalFrames = max(click.frames, max(drums.frames, bass.frames))
            val dataBytes = totalFrames * 4L
            if (dataBytes > 0x7fffffffL) error("Archivos demasiado largos")

            val anySolo = state.clickSolo || state.drumsSolo || state.bassSolo
            fun gain(volume: Int, muted: Boolean, solo: Boolean): Double {
                val audible = !muted && (!anySolo || solo)
                return if (audible) volume.coerceIn(0, 100) / 100.0 else 0.0
            }
            val cg = gain(state.clickVolume, state.clickMuted, state.clickSolo)
            val dg = gain(state.drumsVolume, state.drumsMuted, state.drumsSolo)
            val bg = gain(state.bassVolume, state.bassMuted, state.bassSolo)

            BufferedOutputStream(outputFile.outputStream(), 128 * 1024).use { out ->
                writeWavHeader(out, click.sampleRate, dataBytes.toInt())
                val block = ByteArray(2048 * 4)
                var written = 0L
                while (written < totalFrames) {
                    val count = minOf(2048L, totalFrames - written).toInt()
                    var p = 0
                    repeat(count) {
                        val left = (click.readMonoSample().toInt() * cg).roundToInt().coerceIn(-32768, 32767)
                        val d = (drums.readMonoSample().toInt() * dg).roundToInt()
                        val b = (bass.readMonoSample().toInt() * bg).roundToInt()
                        val right = ((d + b) / 2).coerceIn(-32768, 32767)
                        block[p++] = (left and 0xff).toByte(); block[p++] = ((left ushr 8) and 0xff).toByte()
                        block[p++] = (right and 0xff).toByte(); block[p++] = ((right ushr 8) and 0xff).toByte()
                    }
                    out.write(block, 0, count * 4)
                    written += count
                }
            }
        }}}
    }

    private fun parsePcm16Wav(input: BufferedInputStream): WavReader {
        if (readFourCc(input) != "RIFF") error("No es WAV RIFF")
        readLeInt(input)
        if (readFourCc(input) != "WAVE") error("WAV inválido")
        var format = -1; var channels = -1; var sampleRate = -1; var bits = -1
        while (true) {
            val id = readFourCc(input)
            val size = readLeInt(input)
            if (size < 0) error("Chunk WAV inválido")
            when (id) {
                "fmt " -> {
                    format = readLeShort(input); channels = readLeShort(input); sampleRate = readLeInt(input)
                    readLeInt(input); readLeShort(input); bits = readLeShort(input)
                    skipFully(input, size - 16); if ((size and 1) == 1) skipFully(input, 1)
                }
                "data" -> {
                    if (format != 1) error("Solo WAV PCM")
                    if (bits != 16) error("Solo WAV 16-bit")
                    val bytesPerFrame = channels * 2
                    return WavReader(input, sampleRate, channels, size.toLong() / bytesPerFrame)
                }
                else -> { skipFully(input, size); if ((size and 1) == 1) skipFully(input, 1) }
            }
        }
    }

    private fun writeWavHeader(out: OutputStream, sampleRate: Int, dataSize: Int) {
        out.write("RIFF".toByteArray(Charsets.US_ASCII)); writeLeInt(out, 36 + dataSize)
        out.write("WAVEfmt ".toByteArray(Charsets.US_ASCII)); writeLeInt(out, 16)
        writeLeShort(out, 1); writeLeShort(out, 2); writeLeInt(out, sampleRate); writeLeInt(out, sampleRate * 4)
        writeLeShort(out, 4); writeLeShort(out, 16); out.write("data".toByteArray(Charsets.US_ASCII)); writeLeInt(out, dataSize)
    }

    private fun readFourCc(input: InputStream): String {
        val b = ByteArray(4); readFully(input, b, 0, 4); return String(b, Charsets.US_ASCII)
    }
    private fun readLeInt(input: InputStream): Int {
        val a = IntArray(4) { input.read() }; if (a.any { it < 0 }) throw EOFException()
        return a[0] or (a[1] shl 8) or (a[2] shl 16) or (a[3] shl 24)
    }
    private fun readLeShort(input: InputStream): Int {
        val a = input.read(); val b = input.read(); if (a < 0 || b < 0) throw EOFException(); return a or (b shl 8)
    }
    private fun writeLeInt(out: OutputStream, value: Int) {
        out.write(value and 0xff); out.write((value ushr 8) and 0xff); out.write((value ushr 16) and 0xff); out.write((value ushr 24) and 0xff)
    }
    private fun writeLeShort(out: OutputStream, value: Int) { out.write(value and 0xff); out.write((value ushr 8) and 0xff) }
    private fun skipFully(input: InputStream, bytes: Int) {
        var remaining = bytes
        while (remaining > 0) {
            val skipped = input.skip(remaining.toLong()).toInt()
            if (skipped > 0) remaining -= skipped else { if (input.read() < 0) throw EOFException(); remaining-- }
        }
    }
    private fun readFully(input: InputStream, buffer: ByteArray, offset: Int, length: Int) {
        var total = 0
        while (total < length) {
            val read = input.read(buffer, offset + total, length - total); if (read < 0) throw EOFException(); total += read
        }
    }
    private fun getDisplayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (i >= 0 && c.moveToFirst()) return c.getString(i)
        }
        return uri.lastPathSegment
    }
    private fun formatTime(ms: Int): String {
        val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60)
    }

    override fun onDestroy() {
        handler.removeCallbacks(progressUpdater)
        mediaPlayer?.release()
        super.onDestroy()
    }
}
