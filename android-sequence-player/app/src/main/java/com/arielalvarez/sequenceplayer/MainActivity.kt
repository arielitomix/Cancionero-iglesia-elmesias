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

class MainActivity : Activity() {

    companion object {
        private const val PICK_CLICK = 1001
        private const val PICK_STEM = 1002
    }

    private var clickUri: Uri? = null
    private var stemUri: Uri? = null
    private var mediaPlayer: MediaPlayer? = null
    private var loopEnabled = false
    private var mixReady = false
    private var isBuildingMix = false

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var clickNameView: TextView
    private lateinit var stemNameView: TextView
    private lateinit var currentTimeView: TextView
    private lateinit var durationView: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var playButton: Button
    private lateinit var stopButton: Button
    private lateinit var loopButton: Button
    private lateinit var statusView: TextView

    private val progressUpdater = object : Runnable {
        override fun run() {
            val player = mediaPlayer
            if (player != null && mixReady) {
                val position = player.currentPosition
                currentTimeView.text = formatTime(position)
                if (!seekBar.isPressed && player.duration > 0) {
                    seekBar.progress = position.coerceAtMost(seekBar.max)
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
            setPadding(dp(18), dp(22), dp(18), dp(22))
            setBackgroundColor(Color.rgb(10, 14, 20))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        root.addView(TextView(this).apply {
            text = "SEQUENCE PLAYER · ANDROID"
            setTextColor(Color.rgb(145, 160, 178))
            textSize = 12f
        })

        root.addView(TextView(this).apply {
            text = "Sequence Player"
            setTextColor(Color.WHITE)
            textSize = 30f
            setPadding(0, dp(4), 0, dp(4))
        })

        root.addView(TextView(this).apply {
            text = "Prototipo nativo 0.3 · motor WAV de un solo reloj"
            setTextColor(Color.rgb(145, 160, 178))
            textSize = 14f
            setPadding(0, 0, 0, dp(18))
        })

        val clickButton = Button(this).apply {
            text = "+ ELEGIR CLICK WAV"
            textSize = 15f
            setOnClickListener { openDocumentPicker(PICK_CLICK) }
        }
        root.addView(clickButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(54)
        ))

        clickNameView = TextView(this).apply {
            text = "Click: ninguno"
            setTextColor(Color.rgb(190, 199, 210))
            textSize = 14f
            setPadding(0, dp(10), 0, dp(12))
        }
        root.addView(clickNameView)

        val stemButton = Button(this).apply {
            text = "+ ELEGIR STEM WAV"
            textSize = 15f
            setOnClickListener { openDocumentPicker(PICK_STEM) }
        }
        root.addView(stemButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(54)
        ))

        stemNameView = TextView(this).apply {
            text = "Stem: ninguno"
            setTextColor(Color.rgb(190, 199, 210))
            textSize = 14f
            setPadding(0, dp(10), 0, dp(18))
        }
        root.addView(stemNameView)

        val timeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        currentTimeView = TextView(this).apply {
            text = "0:00"
            setTextColor(Color.WHITE)
            textSize = 40f
        }
        durationView = TextView(this).apply {
            text = "0:00"
            setTextColor(Color.rgb(145, 160, 178))
            textSize = 20f
            gravity = Gravity.END
        }

        timeRow.addView(currentTimeView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        timeRow.addView(durationView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(timeRow)

        seekBar = SeekBar(this).apply {
            max = 1
            progress = 0
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
        root.addView(seekBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(52)
        ))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        stopButton = Button(this).apply {
            text = "■ STOP"
            setOnClickListener { stopPlayback() }
        }
        playButton = Button(this).apply {
            text = "▶ PLAY"
            setOnClickListener { togglePlayback() }
        }
        loopButton = Button(this).apply {
            text = "↻ LOOP"
            setOnClickListener {
                loopEnabled = !loopEnabled
                mediaPlayer?.isLooping = loopEnabled
                text = if (loopEnabled) "↻ LOOP ✓" else "↻ LOOP"
            }
        }

        val buttonParams = LinearLayout.LayoutParams(0, dp(60), 1f).apply {
            marginEnd = dp(6)
        }
        controls.addView(stopButton, buttonParams)
        controls.addView(playButton, buttonParams)
        controls.addView(loopButton, LinearLayout.LayoutParams(0, dp(60), 1f))
        root.addView(controls)

        statusView = TextView(this).apply {
            text = "Carga dos WAV PCM 16-bit exportados desde el mismo inicio."
            setTextColor(Color.rgb(145, 160, 178))
            textSize = 13f
            setPadding(0, dp(20), 0, 0)
        }
        root.addView(statusView)

        root.addView(TextView(this).apply {
            text = "Salida de prueba: Click = L · Stem = R. Los dos comparten exactamente el mismo reloj de audio."
            setTextColor(Color.rgb(110, 124, 142))
            textSize = 12f
            setPadding(0, dp(10), 0, 0)
        })

        return root
    }

    private fun openDocumentPicker(requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/wav"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/wav", "audio/x-wav", "audio/wave"))
        }
        startActivityForResult(intent, requestCode)
    }

    @Deprecated("Deprecated in Android API, kept for this dependency-free prototype")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        if (requestCode != PICK_CLICK && requestCode != PICK_STEM) return

        val uri = data?.data ?: return

        try {
            contentResolver.takePersistableUriPermission(
                uri,
                data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            )
        } catch (_: SecurityException) {
        }

        val name = getDisplayName(uri) ?: "Archivo WAV"
        if (!name.lowercase().endsWith(".wav")) {
            Toast.makeText(this, "Esta versión solo acepta archivos WAV", Toast.LENGTH_LONG).show()
            return
        }

        if (requestCode == PICK_CLICK) {
            clickUri = uri
            clickNameView.text = "Click: $name"
        } else {
            stemUri = uri
            stemNameView.text = "Stem: $name"
        }

        mixReady = false
        releasePlayer()
        updateControls()

        if (clickUri != null && stemUri != null) {
            buildSynchronizedMix()
        } else {
            statusView.text = "Un WAV cargado. Falta seleccionar el otro."
        }
    }

    private fun buildSynchronizedMix() {
        if (isBuildingMix) return
        val click = clickUri ?: return
        val stem = stemUri ?: return

        isBuildingMix = true
        mixReady = false
        updateControls()
        statusView.text = "Preparando mezcla sincronizada…"

        Thread {
            try {
                val outputFile = File(cacheDir, "sequence_sync_03.wav")
                createStereoSyncWav(click, stem, outputFile)

                runOnUiThread {
                    preparePlayer(outputFile)
                    isBuildingMix = false
                }
            } catch (e: Exception) {
                runOnUiThread {
                    isBuildingMix = false
                    mixReady = false
                    updateControls()
                    statusView.text = "No se pudo preparar: ${e.message ?: "WAV incompatible"}"
                    Toast.makeText(this, statusView.text, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun preparePlayer(file: File) {
        releasePlayer()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            isLooping = loopEnabled
            setOnPreparedListener { player ->
                seekBar.max = player.duration.coerceAtLeast(1)
                seekBar.progress = 0
                durationView.text = formatTime(player.duration)
                currentTimeView.text = "0:00"
                mixReady = true
                updateControls()
                statusView.text = "Listo: Click L + Stem R, sincronizados muestra por muestra."
            }
            setOnCompletionListener {
                if (!loopEnabled) {
                    playButton.text = "▶ PLAY"
                    currentTimeView.text = formatTime(it.duration)
                }
            }
            setOnErrorListener { _, _, _ ->
                mixReady = false
                updateControls()
                statusView.text = "Error al reproducir la mezcla sincronizada."
                true
            }
            prepareAsync()
        }
    }

    private fun togglePlayback() {
        val player = mediaPlayer ?: return
        if (!mixReady) return

        if (player.isPlaying) {
            player.pause()
            playButton.text = "▶ PLAY"
            statusView.text = "Pausado."
        } else {
            player.start()
            playButton.text = "❚❚ PAUSA"
            statusView.text = "Reproduciendo con un solo reloj de audio."
        }
    }

    private fun stopPlayback() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) player.pause()
        player.seekTo(0)
        seekBar.progress = 0
        currentTimeView.text = "0:00"
        playButton.text = "▶ PLAY"
        statusView.text = "Detenido."
    }

    private fun updateControls() {
        val enabled = mixReady && !isBuildingMix
        playButton.isEnabled = enabled
        stopButton.isEnabled = enabled
        loopButton.isEnabled = enabled
        seekBar.isEnabled = enabled
    }

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
            readFully(input, frameBuffer, 0, frameBuffer.size)
            var sum = 0
            var offset = 0
            repeat(channels) {
                val lo = frameBuffer[offset].toInt() and 0xff
                val hi = frameBuffer[offset + 1].toInt()
                val sample = (hi shl 8) or lo
                sum += sample.toShort().toInt()
                offset += 2
            }
            framesRead++
            return (sum / channels).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun createStereoSyncWav(clickUri: Uri, stemUri: Uri, outputFile: File) {
        val clickInput = BufferedInputStream(contentResolver.openInputStream(clickUri)
            ?: error("No se pudo abrir el click"), 64 * 1024)
        val stemInput = BufferedInputStream(contentResolver.openInputStream(stemUri)
            ?: error("No se pudo abrir el stem"), 64 * 1024)

        clickInput.use { clickStream ->
            stemInput.use { stemStream ->
                val click = parsePcm16Wav(clickStream)
                val stem = parsePcm16Wav(stemStream)

                if (click.sampleRate != stem.sampleRate) {
                    error("Los WAV deben tener el mismo sample rate (${click.sampleRate} vs ${stem.sampleRate} Hz)")
                }

                val totalFrames = max(click.frames, stem.frames)
                val dataBytes = totalFrames * 4L
                if (dataBytes > 0x7fffffffL) error("Los archivos son demasiado largos para esta prueba")

                BufferedOutputStream(outputFile.outputStream(), 128 * 1024).use { out ->
                    writeWavHeader(out, click.sampleRate, dataBytes.toInt())

                    val framesPerBlock = 2048
                    val block = ByteArray(framesPerBlock * 4)
                    var writtenFrames = 0L

                    while (writtenFrames < totalFrames) {
                        val count = minOf(framesPerBlock.toLong(), totalFrames - writtenFrames).toInt()
                        var p = 0
                        repeat(count) {
                            val left = click.readMonoSample().toInt()
                            val right = stem.readMonoSample().toInt()
                            block[p++] = (left and 0xff).toByte()
                            block[p++] = ((left ushr 8) and 0xff).toByte()
                            block[p++] = (right and 0xff).toByte()
                            block[p++] = ((right ushr 8) and 0xff).toByte()
                        }
                        out.write(block, 0, count * 4)
                        writtenFrames += count
                    }
                }
            }
        }
    }

    private fun parsePcm16Wav(input: BufferedInputStream): WavReader {
        if (readFourCc(input) != "RIFF") error("El archivo no es WAV RIFF")
        readLeInt(input)
        if (readFourCc(input) != "WAVE") error("Formato WAV no válido")

        var audioFormat = -1
        var channels = -1
        var sampleRate = -1
        var bitsPerSample = -1

        while (true) {
            val chunkId = readFourCc(input)
            val chunkSize = readLeInt(input)
            if (chunkSize < 0) error("Chunk WAV inválido")

            when (chunkId) {
                "fmt " -> {
                    if (chunkSize < 16) error("Chunk fmt inválido")
                    audioFormat = readLeShort(input)
                    channels = readLeShort(input)
                    sampleRate = readLeInt(input)
                    readLeInt(input)
                    readLeShort(input)
                    bitsPerSample = readLeShort(input)
                    skipFully(input, chunkSize - 16)
                    if ((chunkSize and 1) == 1) skipFully(input, 1)
                }
                "data" -> {
                    if (audioFormat != 1) error("Solo WAV PCM sin compresión")
                    if (bitsPerSample != 16) error("Solo WAV PCM de 16 bits")
                    if (channels < 1) error("Número de canales inválido")
                    if (sampleRate <= 0) error("Sample rate inválido")
                    val bytesPerFrame = channels * 2
                    val frames = chunkSize.toLong() / bytesPerFrame
                    return WavReader(input, sampleRate, channels, frames)
                }
                else -> {
                    skipFully(input, chunkSize)
                    if ((chunkSize and 1) == 1) skipFully(input, 1)
                }
            }
        }
    }

    private fun writeWavHeader(out: OutputStream, sampleRate: Int, dataSize: Int) {
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        writeLeInt(out, 36 + dataSize)
        out.write("WAVE".toByteArray(Charsets.US_ASCII))
        out.write("fmt ".toByteArray(Charsets.US_ASCII))
        writeLeInt(out, 16)
        writeLeShort(out, 1)
        writeLeShort(out, 2)
        writeLeInt(out, sampleRate)
        writeLeInt(out, sampleRate * 4)
        writeLeShort(out, 4)
        writeLeShort(out, 16)
        out.write("data".toByteArray(Charsets.US_ASCII))
        writeLeInt(out, dataSize)
    }

    private fun readFourCc(input: InputStream): String {
        val bytes = ByteArray(4)
        readFully(input, bytes, 0, 4)
        return String(bytes, Charsets.US_ASCII)
    }

    private fun readLeInt(input: InputStream): Int {
        val b0 = input.read()
        val b1 = input.read()
        val b2 = input.read()
        val b3 = input.read()
        if (b3 < 0) throw EOFException()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    private fun readLeShort(input: InputStream): Int {
        val b0 = input.read()
        val b1 = input.read()
        if (b1 < 0) throw EOFException()
        return b0 or (b1 shl 8)
    }

    private fun writeLeInt(out: OutputStream, value: Int) {
        out.write(value and 0xff)
        out.write((value ushr 8) and 0xff)
        out.write((value ushr 16) and 0xff)
        out.write((value ushr 24) and 0xff)
    }

    private fun writeLeShort(out: OutputStream, value: Int) {
        out.write(value and 0xff)
        out.write((value ushr 8) and 0xff)
    }

    private fun skipFully(input: InputStream, bytes: Int) {
        var remaining = bytes
        while (remaining > 0) {
            val skipped = input.skip(remaining.toLong()).toInt()
            if (skipped > 0) {
                remaining -= skipped
            } else {
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
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    private fun releasePlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
        mixReady = false
    }

    override fun onDestroy() {
        handler.removeCallbacks(progressUpdater)
        releasePlayer()
        super.onDestroy()
    }
}
