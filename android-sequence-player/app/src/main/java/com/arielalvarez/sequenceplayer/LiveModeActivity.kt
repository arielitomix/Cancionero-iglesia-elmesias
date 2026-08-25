package com.arielalvarez.sequenceplayer

import android.app.Activity
import android.graphics.Color
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

    private fun buildUi(): ScrollView {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(8,12,17)) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18),dp(20),dp(18),dp(20))
        }
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(TextView(this).apply {
            text = "MODO EN VIVO"
            setTextColor(Color.rgb(145,160,178))
            textSize = 13f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(30)))

        root.addView(TextView(this).apply {
            text = song.ifBlank { "SEQUENCE PLAYER" }
            setTextColor(Color.WHITE)
            textSize = 30f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(68)))

        nowView = TextView(this).apply {
            text = "AHORA: —"
            setTextColor(Color.WHITE)
            textSize = 25f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(24,31,40))
        }
        root.addView(nowView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(64)).apply { bottomMargin=dp(6) })

        nextView = TextView(this).apply {
            text = "SIGUE: —"
            setTextColor(Color.LTGRAY)
            textSize = 18f
            gravity = Gravity.CENTER
        }
        root.addView(nextView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(48)))

        root.addView(TextView(this).apply {
            text = "SECCIONES"
            setTextColor(Color.rgb(145,160,178))
            textSize = 12f
            setPadding(0,dp(10),0,dp(6))
        })

        val markers = loadMarkers()
        if (markers.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "Esta canción todavía no tiene marcadores."
                setTextColor(Color.LTGRAY)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(70)))
        } else {
            markers.forEach { marker ->
                root.addView(Button(this).apply {
                    text = marker.name.uppercase()
                    textSize = 20f
                    setOnClickListener {
                        statusView.text = "PREPARADO: ${marker.name.uppercase()}"
                        LiveModeBridge.requestedSectionName = marker.name
                        LiveModeBridge.requestedSectionMs = marker.ms
                        LiveModeBridge.requestToken++
                    }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(62)).apply { bottomMargin=dp(6) })
            }
        }

        val transport = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        transport.addView(Button(this).apply {
            text="▶ / ❚❚"
            textSize=18f
            setOnClickListener { LiveModeBridge.transportCommand="toggle";LiveModeBridge.transportToken++ }
        },LinearLayout.LayoutParams(0,dp(58),1f).apply{marginEnd=dp(5)})
        transport.addView(Button(this).apply {
            text="■ STOP"
            textSize=18f
            setOnClickListener { LiveModeBridge.transportCommand="stop";LiveModeBridge.transportToken++ }
        },LinearLayout.LayoutParams(0,dp(58),1f))
        root.addView(transport,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(58)).apply{topMargin=dp(8)})

        statusView = TextView(this).apply {
            text="LISTO"
            setTextColor(Color.rgb(145,160,178))
            textSize=13f
            gravity=Gravity.CENTER
        }
        root.addView(statusView,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(44)))

        root.addView(Button(this).apply {
            text="← VOLVER A EDICIÓN"
            setOnClickListener { finish() }
        },LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)).apply{topMargin=dp(8)})
        return scroll
    }

    private fun loadMarkers(): List<Marker> {
        if (song.isBlank()) return emptyList()
        val raw=getSharedPreferences("sequence_player_sections_v15",MODE_PRIVATE).getString("sections_v15_$song","[]")?:"[]"
        return try {
            val arr=JSONArray(raw)
            buildList {
                for(i in 0 until arr.length()) {
                    val o=arr.getJSONObject(i)
                    val name=o.optString("name").trim()
                    val ms=o.optInt("ms",-1)
                    if(name.isNotEmpty()&&ms>=0)add(Marker(name,ms))
                }
            }.sortedBy { it.ms }
        } catch(_:Exception) { emptyList() }
    }

    private fun refreshSectionLabels() {
        val pos=LiveModeBridge.positionMs
        val markers=loadMarkers()
        val currentIndex=markers.indexOfLast { it.ms<=pos }
        nowView.text=if(currentIndex>=0)"AHORA: ${markers[currentIndex].name.uppercase()}" else "AHORA: —"
        nextView.text=if(currentIndex>=0&&currentIndex<markers.lastIndex)"SIGUE: ${markers[currentIndex+1].name.uppercase()}" else "SIGUE: —"
        if(LiveModeBridge.pendingSectionName.isNotBlank())statusView.text="SALTO: ${LiveModeBridge.pendingSectionName.uppercase()}"
    }

    override fun onDestroy() {
        handler.removeCallbacks(updater)
        super.onDestroy()
    }
}

object LiveModeBridge {
    @Volatile var positionMs=0
    @Volatile var requestedSectionName=""
    @Volatile var requestedSectionMs=-1
    @Volatile var requestToken=0
    @Volatile var pendingSectionName=""
    @Volatile var transportCommand=""
    @Volatile var transportToken=0
}
