package com.arielalvarez.sequenceplayer

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray

class LiveModeActivity : Activity() {
    private data class Marker(val name: String, val ms: Int)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var nowView: TextView
    private lateinit var nextView: TextView
    private lateinit var statusView: TextView
    private var song = ""

    private val bg = Color.rgb(8, 11, 14)
    private val surface = Color.rgb(25, 27, 29)
    private val surfaceHigh = Color.rgb(36, 36, 40)
    private val lime = Color.rgb(166, 255, 0)
    private val secondaryText = Color.rgb(161, 161, 170)

    private val updater = object : Runnable {
        override fun run() {
            refreshSectionLabels()
            handler.postDelayed(this, 150L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        song = intent.getStringExtra("song").orEmpty()
        setContentView(buildUi())
        handler.post(updater)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int = 14, strokeColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (strokeColor != null) setStroke(dp(1), strokeColor)
        }

    private fun buildUi(): ScrollView {
        val scroll = ScrollView(this).apply { setBackgroundColor(bg) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(22))
        }
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(TextView(this).apply {
            text = "SecuenLive"
            setTextColor(lime)
            textSize = 17f
            gravity = Gravity.CENTER
            letterSpacing = 0.04f
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)))

        root.addView(TextView(this).apply {
            text = song.ifBlank { "SIN CANCIÓN" }
            setTextColor(Color.WHITE)
            textSize = 28f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)))

        root.addView(TextView(this).apply {
            text = "SECCIÓN ACTUAL"
            setTextColor(secondaryText)
            textSize = 11f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)))

        nowView = TextView(this).apply {
            text = "—"
            setTextColor(lime)
            textSize = 32f
            gravity = Gravity.CENTER
            background = rounded(surface, 18, Color.rgb(48, 53, 57))
        }
        root.addView(nowView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(78)).apply { bottomMargin = dp(8) })

        nextView = TextView(this).apply {
            text = "SIGUE  •  —"
            setTextColor(Color.WHITE)
            textSize = 17f
            gravity = Gravity.CENTER
        }
        root.addView(nextView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))

        root.addView(TextView(this).apply {
            text = "IR A SECCIÓN"
            setTextColor(secondaryText)
            textSize = 12f
            setPadding(dp(2), dp(14), 0, dp(8))
        })

        val markers = loadMarkers()
        if (markers.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "Esta canción todavía no tiene marcadores."
                setTextColor(secondaryText)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(70)))
        } else {
            val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            markers.chunked(2).forEach { rowMarkers ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                rowMarkers.forEachIndexed { index, marker ->
                    row.addView(Button(this).apply {
                        text = marker.name.uppercase()
                        textSize = 15f
                        setTextColor(Color.WHITE)
                        background = rounded(surfaceHigh, 12, Color.rgb(57, 60, 64))
                        setOnClickListener {
                            statusView.text = "PREPARADO  •  ${marker.name.uppercase()}"
                            statusView.setTextColor(lime)
                            LiveModeBridge.requestedSectionName = marker.name
                            LiveModeBridge.requestedSectionMs = marker.ms
                            LiveModeBridge.requestToken++
                        }
                    }, LinearLayout.LayoutParams(0, dp(56), 1f).apply {
                        if (index == 0) marginEnd = dp(4) else marginStart = dp(4)
                        bottomMargin = dp(8)
                    })
                }
                if (rowMarkers.size == 1) row.addView(TextView(this), LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginStart = dp(4) })
                grid.addView(row)
            }
            root.addView(grid)
        }

        val transport = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        transport.addView(Button(this).apply {
            text = "▶  /  ❚❚"
            textSize = 18f
            setTextColor(Color.rgb(12, 16, 10))
            background = rounded(lime, 14)
            setOnClickListener { LiveModeBridge.transportCommand = "toggle"; LiveModeBridge.transportToken++ }
        }, LinearLayout.LayoutParams(0, dp(60), 1f).apply { marginEnd = dp(5) })
        transport.addView(Button(this).apply {
            text = "■  STOP"
            textSize = 17f
            setTextColor(Color.WHITE)
            background = rounded(surfaceHigh, 14, Color.rgb(57, 60, 64))
            setOnClickListener { LiveModeBridge.transportCommand = "stop"; LiveModeBridge.transportToken++ }
        }, LinearLayout.LayoutParams(0, dp(60), 1f).apply { marginStart = dp(5) })
        root.addView(transport, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(70)))

        statusView = TextView(this).apply {
            text = "LISTO PARA TOCAR"
            setTextColor(secondaryText)
            textSize = 12f
            gravity = Gravity.CENTER
        }
        root.addView(statusView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))

        root.addView(Button(this).apply {
            text = "←  VOLVER A EDICIÓN"
            textSize = 14f
            setTextColor(Color.WHITE)
            background = rounded(surface, 12, Color.rgb(48, 53, 57))
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(4) })
        return scroll
    }

    private fun loadMarkers(): List<Marker> {
        if (song.isBlank()) return emptyList()
        val raw = getSharedPreferences("sequence_player_sections_v15", MODE_PRIVATE).getString("sections_v15_$song", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val name = o.optString("name").trim()
                    val ms = o.optInt("ms", -1)
                    if (name.isNotEmpty() && ms >= 0) add(Marker(name, ms))
                }
            }.sortedBy { it.ms }
        } catch (_: Exception) { emptyList() }
    }

    private fun refreshSectionLabels() {
        val pos = LiveModeBridge.positionMs
        val markers = loadMarkers()
        val currentIndex = markers.indexOfLast { it.ms <= pos }
        nowView.text = if (currentIndex >= 0) markers[currentIndex].name.uppercase() else "—"
        nextView.text = if (currentIndex >= 0 && currentIndex < markers.lastIndex) "SIGUE  •  ${markers[currentIndex + 1].name.uppercase()}" else "SIGUE  •  —"
        if (LiveModeBridge.pendingSectionName.isNotBlank()) {
            statusView.text = "SALTO  •  ${LiveModeBridge.pendingSectionName.uppercase()}"
            statusView.setTextColor(lime)
        } else {
            statusView.text = "LISTO PARA TOCAR"
            statusView.setTextColor(secondaryText)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(updater)
        super.onDestroy()
    }
}

object LiveModeBridge {
    @Volatile var positionMs = 0
    @Volatile var requestedSectionName = ""
    @Volatile var requestedSectionMs = -1
    @Volatile var requestToken = 0
    @Volatile var pendingSectionName = ""
    @Volatile var transportCommand = ""
    @Volatile var transportToken = 0
}
