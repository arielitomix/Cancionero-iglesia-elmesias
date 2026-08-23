package com.arielalvarez.sequenceplayer

import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivityV17 : MainActivityV16() {
    private val metroHandler = Handler(Looper.getMainLooper())
    private var toneGenerator: ToneGenerator? = null
    private var metronomeEnabled = false
    private var bpm = 100
    private var metroVolume = 80
    private var countBars = 1
    private var metroBeat = 0
    private var nextBeatAt = 0L
    private var countInRunning = false

    private var basePlayButton: Button? = null
    private lateinit var metroToggle: Button
    private lateinit var bpmInput: EditText
    private lateinit var volumeLabel: TextView
    private lateinit var count0: Button
    private lateinit var count1: Button
    private lateinit var count2: Button

    private val metronomeTick = object : Runnable {
        override fun run() {
            if (!metronomeEnabled || !isBasePlaying()) return
            playBeat(metroBeat % 4 == 0)
            metroBeat = (metroBeat + 1) % 4
            val interval = beatIntervalMs()
            nextBeatAt += interval
            val delay = (nextBeatAt - SystemClock.uptimeMillis()).coerceAtLeast(1L)
            metroHandler.postDelayed(this, delay)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installMetronomePanel()
        hookTransport()
    }

    private fun installMetronomePanel() {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val contentRoot = findViewById<ViewGroup>(android.R.id.content)
        if (contentRoot.childCount == 0) return
        val previousView = contentRoot.getChildAt(0)
        contentRoot.removeView(previousView)

        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(10, 14, 20))
        }
        wrapper.addView(previousView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(7), dp(12), dp(8))
            setBackgroundColor(Color.rgb(16, 22, 29))
        }
        panel.addView(TextView(this).apply {
            text = "SEQUENCE PLAYER · 0.17 · METRÓNOMO INTERNO"
            setTextColor(Color.rgb(145, 160, 178))
            textSize = 11f
        })

        val mainRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        bpmInput = EditText(this).apply {
            setText(bpm.toString())
            hint = "BPM"
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(130, 140, 150))
            gravity = Gravity.CENTER
        }
        mainRow.addView(bpmInput, LinearLayout.LayoutParams(dp(88), dp(46)))
        metroToggle = Button(this).apply {
            text = "METRÓNOMO: OFF"
            setOnClickListener {
                applyBpmFromInput()
                metronomeEnabled = !metronomeEnabled
                text = if (metronomeEnabled) "METRÓNOMO: ON" else "METRÓNOMO: OFF"
                if (metronomeEnabled && isBasePlaying()) startMetronome() else stopMetronomeTicks()
            }
        }
        mainRow.addView(metroToggle, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(6) })
        panel.addView(mainRow)

        val volRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        volumeLabel = TextView(this).apply {
            text = "Click $metroVolume%"
            setTextColor(Color.LTGRAY)
            textSize = 12f
        }
        volRow.addView(volumeLabel, LinearLayout.LayoutParams(dp(76), dp(38)))
        val vol = SeekBar(this).apply {
            max = 100
            progress = metroVolume
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    metroVolume = progress
                    volumeLabel.text = "Click $metroVolume%"
                    recreateToneGenerator()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        volRow.addView(vol, LinearLayout.LayoutParams(0, dp(38), 1f))
        panel.addView(volRow)

        val countRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        count0 = countButton("SIN CUENTA", 0, dp(4))
        count1 = countButton("1 COMPÁS", 1, dp(4))
        count2 = countButton("2 COMPASES", 2, 0)
        countRow.addView(count0, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(4) })
        countRow.addView(count1, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(4) })
        countRow.addView(count2, LinearLayout.LayoutParams(0, dp(42), 1f))
        panel.addView(countRow)
        updateCountButtons()

        wrapper.addView(panel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(145)))
        contentRoot.addView(wrapper, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        recreateToneGenerator()
    }

    private fun countButton(label: String, bars: Int, margin: Int): Button = Button(this).apply {
        text = label
        setOnClickListener {
            countBars = bars
            updateCountButtons()
            Toast.makeText(this@MainActivityV17, if (bars == 0) "Cuenta desactivada" else "Cuenta: $bars compás${if (bars > 1) "es" else ""}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateCountButtons() {
        if (!::count0.isInitialized) return
        count0.text = if (countBars == 0) "✓ SIN CUENTA" else "SIN CUENTA"
        count1.text = if (countBars == 1) "✓ 1 COMPÁS" else "1 COMPÁS"
        count2.text = if (countBars == 2) "✓ 2 COMPASES" else "2 COMPASES"
    }

    private fun hookTransport() {
        val buttons = mutableListOf<Button>()
        collectButtons(findViewById(android.R.id.content), buttons)
        basePlayButton = buttons.firstOrNull { it.text.toString().contains("PLAY") || it.text.toString().contains("PAUSA") }
        val stopButton = buttons.firstOrNull { it.text.toString().contains("STOP") }

        basePlayButton?.setOnClickListener {
            if (countInRunning) return@setOnClickListener
            applyBpmFromInput()
            if (isBasePlaying()) {
                invokeBaseMethod("pausePlayback")
                stopMetronomeTicks()
                basePlayButton?.text = "▶ PLAY"
            } else {
                if (countBars > 0) startCountIn() else startBaseWithMetronome()
            }
        }

        stopButton?.setOnClickListener {
            cancelCountIn()
            stopMetronomeTicks()
            invokeBaseMethod("stopPlayback")
            basePlayButton?.text = "▶ PLAY"
        }
    }

    private fun startCountIn() {
        countInRunning = true
        stopMetronomeTicks()
        val totalBeats = countBars * 4
        var beat = 0
        basePlayButton?.isEnabled = false
        basePlayButton?.text = "CUENTA ${countBars}…"

        fun scheduleBeat() {
            if (!countInRunning) return
            if (beat >= totalBeats) {
                countInRunning = false
                basePlayButton?.isEnabled = true
                startBaseWithMetronome()
                return
            }
            playBeat(beat % 4 == 0)
            beat++
            metroHandler.postDelayed({ scheduleBeat() }, beatIntervalMs())
        }
        scheduleBeat()
    }

    private fun startBaseWithMetronome() {
        invokeBaseMethod("startPlayback")
        basePlayButton?.text = "❚❚ PAUSA"
        if (metronomeEnabled) startMetronome()
    }

    private fun startMetronome() {
        stopMetronomeTicks()
        metroBeat = 0
        nextBeatAt = SystemClock.uptimeMillis()
        metroHandler.post(metronomeTick)
    }

    private fun stopMetronomeTicks() {
        metroHandler.removeCallbacks(metronomeTick)
        metroBeat = 0
    }

    private fun cancelCountIn() {
        countInRunning = false
        metroHandler.removeCallbacksAndMessages(null)
        basePlayButton?.isEnabled = true
    }

    private fun beatIntervalMs(): Long = (60000.0 / bpm.coerceIn(30, 300)).toLong().coerceAtLeast(100L)

    private fun applyBpmFromInput() {
        val parsed = bpmInput.text.toString().trim().toIntOrNull()
        bpm = (parsed ?: bpm).coerceIn(30, 300)
        bpmInput.setText(bpm.toString())
    }

    private fun recreateToneGenerator() {
        try { toneGenerator?.release() } catch (_: Exception) {}
        toneGenerator = try { ToneGenerator(AudioManager.STREAM_MUSIC, metroVolume.coerceIn(0, 100)) } catch (_: Exception) { null }
    }

    private fun playBeat(accent: Boolean) {
        if (metroVolume <= 0) return
        val tone = if (accent) ToneGenerator.TONE_PROP_BEEP2 else ToneGenerator.TONE_PROP_BEEP
        try { toneGenerator?.startTone(tone, 55) } catch (_: Exception) {}
    }

    private fun isBasePlaying(): Boolean {
        return try {
            val field = MainActivityV13::class.java.getDeclaredField("playing")
            field.isAccessible = true
            field.getBoolean(this)
        } catch (_: Exception) {
            basePlayButton?.text.toString().contains("PAUSA")
        }
    }

    private fun invokeBaseMethod(name: String) {
        try {
            val method = MainActivityV13::class.java.getDeclaredMethod(name)
            method.isAccessible = true
            method.invoke(this)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo ejecutar $name", Toast.LENGTH_SHORT).show()
        }
    }

    private fun collectButtons(root: View, out: MutableList<Button>) {
        if (root is Button) out.add(root)
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) collectButtons(root.getChildAt(i), out)
        }
    }

    override fun onDestroy() {
        cancelCountIn()
        stopMetronomeTicks()
        try { toneGenerator?.release() } catch (_: Exception) {}
        toneGenerator = null
        super.onDestroy()
    }
}
