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

class MainActivity : Activity() {

    companion object {
        private const val PICK_AUDIO = 1001
    }

    private var mediaPlayer: MediaPlayer? = null
    private var selectedUri: Uri? = null
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var fileNameView: TextView
    private lateinit var currentTimeView: TextView
    private lateinit var durationView: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var playButton: Button
    private lateinit var stopButton: Button
    private lateinit var loopButton: Button

    private val progressUpdater = object : Runnable {
        override fun run() {
            val player = mediaPlayer
            if (player != null) {
                currentTimeView.text = formatTime(player.currentPosition)
                if (!seekBar.isPressed && player.duration > 0) {
                    seekBar.progress = player.currentPosition
                }
                handler.postDelayed(this, 250)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        setControlsEnabled(false)
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
            text = "Prototipo nativo 0.1 · selector real de documentos"
            setTextColor(Color.rgb(145, 160, 178))
            textSize = 14f
            setPadding(0, 0, 0, dp(22))
        })

        val pickButton = Button(this).apply {
            text = "+ ELEGIR ARCHIVO DE AUDIO"
            textSize = 16f
            setOnClickListener { openDocumentPicker() }
        }
        root.addView(pickButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(58)
        ))

        fileNameView = TextView(this).apply {
            text = "Ningún archivo cargado"
            setTextColor(Color.rgb(190, 199, 210))
            textSize = 15f
            setPadding(0, dp(16), 0, dp(24))
        }
        root.addView(fileNameView)

        val timeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        currentTimeView = TextView(this).apply {
            text = "0:00"
            setTextColor(Color.WHITE)
            textSize = 42f
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
                mediaPlayer?.let {
                    it.isLooping = !it.isLooping
                    text = if (it.isLooping) "↻ LOOP ✓" else "↻ LOOP"
                }
            }
        }

        val buttonParams = LinearLayout.LayoutParams(0, dp(60), 1f).apply {
            marginEnd = dp(6)
        }
        controls.addView(stopButton, buttonParams)
        controls.addView(playButton, buttonParams)
        controls.addView(loopButton, LinearLayout.LayoutParams(0, dp(60), 1f))
        root.addView(controls)

        root.addView(TextView(this).apply {
            text = "Esta versión usa el selector de documentos de Android, no el selector de fotos/video del navegador."
            setTextColor(Color.rgb(145, 160, 178))
            textSize = 13f
            setPadding(0, dp(24), 0, 0)
        })

        return root
    }

    private fun openDocumentPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "audio/wav",
                "audio/x-wav",
                "audio/mpeg",
                "audio/mp4",
                "audio/aac",
                "audio/ogg",
                "audio/flac"
            ))
        }
        startActivityForResult(intent, PICK_AUDIO)
    }

    @Deprecated("Deprecated in Android API, kept for this dependency-free prototype")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_AUDIO || resultCode != RESULT_OK) return

        val uri = data?.data ?: return
        selectedUri = uri

        try {
            contentResolver.takePersistableUriPermission(
                uri,
                data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            )
        } catch (_: SecurityException) {
            // Some providers do not offer persistable permission; playback still works for this session.
        }

        fileNameView.text = getDisplayName(uri) ?: "Archivo de audio"
        loadAudio(uri)
    }

    private fun loadAudio(uri: Uri) {
        releasePlayer()
        setControlsEnabled(false)

        mediaPlayer = MediaPlayer().apply {
            setDataSource(this@MainActivity, uri)
            setOnPreparedListener { player ->
                seekBar.max = player.duration.coerceAtLeast(1)
                durationView.text = formatTime(player.duration)
                currentTimeView.text = "0:00"
                setControlsEnabled(true)
                handler.post(progressUpdater)
                Toast.makeText(this@MainActivity, "Audio listo", Toast.LENGTH_SHORT).show()
            }
            setOnCompletionListener {
                if (!it.isLooping) {
                    playButton.text = "▶ PLAY"
                    currentTimeView.text = formatTime(it.duration)
                }
            }
            setOnErrorListener { _, _, _ ->
                Toast.makeText(this@MainActivity, "No se pudo reproducir este archivo", Toast.LENGTH_LONG).show()
                setControlsEnabled(false)
                true
            }
            prepareAsync()
        }
    }

    private fun togglePlayback() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            playButton.text = "▶ PLAY"
        } else {
            player.start()
            playButton.text = "❚❚ PAUSA"
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

    private fun setControlsEnabled(enabled: Boolean) {
        playButton.isEnabled = enabled
        stopButton.isEnabled = enabled
        loopButton.isEnabled = enabled
        seekBar.isEnabled = enabled
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
        handler.removeCallbacks(progressUpdater)
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }
}
