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
import kotlin.math.abs

class MainActivity : Activity() {

    companion object {
        private const val PICK_CLICK = 1001
        private const val PICK_STEM = 1002
        private const val SYNC_TOLERANCE_MS = 45
    }

    private var clickPlayer: MediaPlayer? = null
    private var stemPlayer: MediaPlayer? = null
    private var clickPrepared = false
    private var stemPrepared = false
    private var loopEnabled = false

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
            val click = clickPlayer
            val stem = stemPlayer
            val reference = stem ?: click

            if (reference != null) {
                val position = reference.currentPosition
                currentTimeView.text = formatTime(position)
                if (!seekBar.isPressed && reference.duration > 0) {
                    seekBar.progress = position.coerceAtMost(seekBar.max)
                }

                if (click != null && stem != null && click.isPlaying && stem.isPlaying) {
                    val drift = click.currentPosition - stem.currentPosition
                    if (abs(drift) > SYNC_TOLERANCE_MS) {
                        click.seekTo(stem.currentPosition)
                    }
                }
            }

            handler.postDelayed(this, 250)
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
            text = "Prototipo nativo 0.2 · dos stems sincronizados"
            setTextColor(Color.rgb(145, 160, 178))
            textSize = 14f
            setPadding(0, 0, 0, dp(18))
        })

        val clickButton = Button(this).apply {
            text = "+ ELEGIR CLICK"
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
            text = "+ ELEGIR STEM / INSTRUMENTO"
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
                    val position = seekBar?.progress ?: 0
                    clickPlayer?.seekTo(position)
                    stemPlayer?.seekTo(position)
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
                clickPlayer?.isLooping = loopEnabled
                stemPlayer?.isLooping = loopEnabled
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
            text = "Carga Click + Stem para probar sincronía."
            setTextColor(Color.rgb(145, 160, 178))
            textSize = 13f
            setPadding(0, dp(20), 0, 0)
        }
        root.addView(statusView)

        return root
    }

    private fun openDocumentPicker(requestCode: Int) {
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

        val name = getDisplayName(uri) ?: "Archivo de audio"
        if (requestCode == PICK_CLICK) {
            clickNameView.text = "Click: $name"
            loadPlayer(uri, true)
        } else {
            stemNameView.text = "Stem: $name"
            loadPlayer(uri, false)
        }
    }

    private fun loadPlayer(uri: Uri, isClick: Boolean) {
        if (isClick) {
            clickPlayer?.release()
            clickPlayer = null
            clickPrepared = false
        } else {
            stemPlayer?.release()
            stemPlayer = null
            stemPrepared = false
        }
        updateControls()
        statusView.text = "Preparando audio…"

        val player = MediaPlayer().apply {
            setDataSource(this@MainActivity, uri)
            isLooping = loopEnabled
            setOnPreparedListener { prepared ->
                if (isClick) clickPrepared = true else stemPrepared = true
                val maxDuration = listOfNotNull(
                    clickPlayer?.takeIf { clickPrepared }?.duration,
                    stemPlayer?.takeIf { stemPrepared }?.duration
                ).maxOrNull() ?: prepared.duration
                seekBar.max = maxDuration.coerceAtLeast(1)
                durationView.text = formatTime(maxDuration)
                updateControls()
                statusView.text = if (clickPrepared && stemPrepared) {
                    "Click + Stem listos. Dale PLAY."
                } else {
                    "Un archivo listo. Falta cargar el otro."
                }
            }
            setOnCompletionListener {
                if (!loopEnabled && !isAnyPlaying()) {
                    playButton.text = "▶ PLAY"
                }
            }
            setOnErrorListener { _, _, _ ->
                Toast.makeText(this@MainActivity, "No se pudo reproducir este archivo", Toast.LENGTH_LONG).show()
                if (isClick) clickPrepared = false else stemPrepared = false
                updateControls()
                true
            }
            prepareAsync()
        }

        if (isClick) clickPlayer = player else stemPlayer = player
    }

    private fun togglePlayback() {
        if (!clickPrepared || !stemPrepared) return

        if (isAnyPlaying()) {
            clickPlayer?.pause()
            stemPlayer?.pause()
            playButton.text = "▶ PLAY"
            statusView.text = "Pausado."
        } else {
            val position = maxOf(clickPlayer?.currentPosition ?: 0, stemPlayer?.currentPosition ?: 0)
            clickPlayer?.seekTo(position)
            stemPlayer?.seekTo(position)
            stemPlayer?.start()
            clickPlayer?.start()
            playButton.text = "❚❚ PAUSA"
            statusView.text = "Reproduciendo Click + Stem sincronizados."
        }
    }

    private fun stopPlayback() {
        clickPlayer?.pause()
        stemPlayer?.pause()
        clickPlayer?.seekTo(0)
        stemPlayer?.seekTo(0)
        seekBar.progress = 0
        currentTimeView.text = "0:00"
        playButton.text = "▶ PLAY"
        statusView.text = "Detenido."
    }

    private fun isAnyPlaying(): Boolean {
        return clickPlayer?.isPlaying == true || stemPlayer?.isPlaying == true
    }

    private fun updateControls() {
        val ready = clickPrepared && stemPrepared
        playButton.isEnabled = ready
        stopButton.isEnabled = ready
        loopButton.isEnabled = ready
        seekBar.isEnabled = ready
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

    private fun releasePlayers() {
        handler.removeCallbacks(progressUpdater)
        clickPlayer?.release()
        stemPlayer?.release()
        clickPlayer = null
        stemPlayer = null
    }

    override fun onDestroy() {
        releasePlayers()
        super.onDestroy()
    }
}
