package com.arielalvarez.sequenceplayer

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

open class MainActivityV16 : MainActivityV13() {
    private data class SectionMarker(val name: String, val ms: Int)

    private lateinit var sectionList: LinearLayout
    private lateinit var currentSectionView: TextView
    private lateinit var sectionLoopButton: Button
    private var timeline: SeekBar? = null
    private var songSpinner: Spinner? = null
    private var titleInput: EditText? = null
    private val sectionHandler = Handler(Looper.getMainLooper())
    private var sectionLoopEnabled = false
    private var loopStartMs = 0
    private var loopEndMs = 0
    private var loopSectionName = ""
    private var lastSongKey = ""

    private val sectionUpdater = object : Runnable {
        override fun run() {
            updateCurrentSection()
            enforceSectionLoop()
            sectionHandler.postDelayed(this, 40)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSectionPanel()
        lastSongKey = songKey()
        sectionHandler.post(sectionUpdater)
    }

    private fun installSectionPanel() {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val contentRoot = findViewById<ViewGroup>(android.R.id.content)
        if (contentRoot.childCount == 0) return
        val playerView = contentRoot.getChildAt(0)
        contentRoot.removeView(playerView)

        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(10, 14, 20))
        }
        wrapper.addView(playerView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(7), dp(12), dp(8))
            setBackgroundColor(Color.rgb(20, 20, 20))
        }
        panel.addView(TextView(this).apply {
            text = "SEQUENCE PLAYER · 0.16 · LOOP POR SECCIÓN"
            setTextColor(Color.rgb(145,160,178))
            textSize = 11f
        })

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        currentSectionView = TextView(this).apply {
            text = "SECCIÓN: —"
            setTextColor(Color.WHITE)
            textSize = 15f
        }
        header.addView(currentSectionView, LinearLayout.LayoutParams(0, dp(42), 1f))
        header.addView(Button(this).apply {
            text = "+ MARCAR"
            setOnClickListener { promptAddSection() }
        }, LinearLayout.LayoutParams(dp(112), dp(42)))
        panel.addView(header)

        val scroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        sectionList = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        scroller.addView(sectionList, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)))
        panel.addView(scroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)))

        sectionLoopButton = Button(this).apply {
            text = "↻ LOOP SECCIÓN: OFF"
            setOnClickListener { toggleSectionLoop() }
        }
        panel.addView(sectionLoopButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))

        wrapper.addView(panel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(160)))
        contentRoot.addView(wrapper, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val seekBars = mutableListOf<SeekBar>()
        collectViews(playerView, SeekBar::class.java, seekBars)
        timeline = seekBars.firstOrNull { it.max != 100 }

        val spinners = mutableListOf<Spinner>()
        collectViews(playerView, Spinner::class.java, spinners)
        songSpinner = spinners.firstOrNull()

        val edits = mutableListOf<EditText>()
        collectViews(playerView, EditText::class.java, edits)
        titleInput = edits.firstOrNull()

        songSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                disableSectionLoop()
                sectionHandler.postDelayed({ renderSections() }, 200)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        renderSections()
    }

    private fun promptAddSection() {
        val bar = timeline ?: return
        if (bar.max <= 1) {
            Toast.makeText(this, "Carga una canción primero", Toast.LENGTH_SHORT).show()
            return
        }
        val input = EditText(this).apply {
            hint = "Ej. Intro, Verso, Coro, Puente"
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Marcar sección en ${formatTime(bar.progress)}")
            .setView(input)
            .setPositiveButton("GUARDAR") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val markers = loadSections().toMutableList()
                    markers.add(SectionMarker(name, bar.progress))
                    saveSections(markers.sortedBy { it.ms })
                    disableSectionLoop()
                    renderSections()
                }
            }
            .setNegativeButton("CANCELAR", null)
            .show()
    }

    private fun renderSections() {
        if (!::sectionList.isInitialized) return
        sectionList.removeAllViews()
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val markers = loadSections().sortedBy { it.ms }

        if (markers.isEmpty()) {
            sectionList.addView(TextView(this).apply {
                text = "Mueve la barra y toca + MARCAR"
                setTextColor(Color.rgb(160,160,160))
                gravity = Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)))
            updateCurrentSection()
            return
        }

        markers.forEach { marker ->
            sectionList.addView(Button(this).apply {
                text = "${marker.name}  ${formatTime(marker.ms)}"
                setOnClickListener {
                    jumpTo(marker.ms)
                    if (sectionLoopEnabled) activateLoopForPosition(marker.ms)
                }
                setOnLongClickListener {
                    AlertDialog.Builder(this@MainActivityV16)
                        .setTitle("Eliminar ${marker.name}")
                        .setPositiveButton("ELIMINAR") { _, _ ->
                            val updated = loadSections().toMutableList()
                            val idx = updated.indexOfFirst { it.name == marker.name && it.ms == marker.ms }
                            if (idx >= 0) updated.removeAt(idx)
                            saveSections(updated)
                            disableSectionLoop()
                            renderSections()
                        }
                        .setNegativeButton("CANCELAR", null)
                        .show()
                    true
                }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)).apply { marginEnd = dp(5) })
        }
        updateCurrentSection()
    }

    private fun toggleSectionLoop() {
        if (sectionLoopEnabled) {
            disableSectionLoop()
            return
        }
        val bar = timeline ?: return
        if (loadSections().isEmpty()) {
            Toast.makeText(this, "Primero crea al menos una sección", Toast.LENGTH_SHORT).show()
            return
        }
        if (activateLoopForPosition(bar.progress)) {
            sectionLoopEnabled = true
            sectionLoopButton.text = "↻ LOOP: $loopSectionName ✓"
            Toast.makeText(this, "Loop de $loopSectionName activado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun activateLoopForPosition(positionMs: Int): Boolean {
        val markers = loadSections().sortedBy { it.ms }
        if (markers.isEmpty()) return false
        val index = markers.indexOfLast { it.ms <= positionMs }.let { if (it < 0) 0 else it }
        val marker = markers[index]
        val bar = timeline ?: return false
        loopStartMs = marker.ms.coerceIn(0, bar.max)
        loopEndMs = if (index < markers.lastIndex) markers[index + 1].ms.coerceIn(loopStartMs + 1, bar.max) else bar.max
        if (loopEndMs <= loopStartMs) return false
        loopSectionName = marker.name
        if (sectionLoopEnabled) sectionLoopButton.text = "↻ LOOP: $loopSectionName ✓"
        return true
    }

    private fun enforceSectionLoop() {
        if (!sectionLoopEnabled) return
        val key = songKey()
        if (key != lastSongKey) {
            lastSongKey = key
            disableSectionLoop()
            return
        }
        val bar = timeline ?: return
        val pos = bar.progress
        if (pos >= loopEndMs - 35) {
            jumpTo(loopStartMs)
        }
    }

    private fun disableSectionLoop() {
        sectionLoopEnabled = false
        loopStartMs = 0
        loopEndMs = 0
        loopSectionName = ""
        if (::sectionLoopButton.isInitialized) sectionLoopButton.text = "↻ LOOP SECCIÓN: OFF"
    }

    private fun jumpTo(ms: Int) {
        val bar = timeline ?: return
        val target = ms.coerceIn(0, bar.max)
        try {
            val method = MainActivityV13::class.java.getDeclaredMethod("seekToMs", Int::class.javaPrimitiveType)
            method.isAccessible = true
            method.invoke(this, target)
            bar.progress = target
            updateCurrentSection()
        } catch (_: Exception) {
            Toast.makeText(this, "No se pudo saltar a la sección", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateCurrentSection() {
        if (!::currentSectionView.isInitialized) return
        val pos = timeline?.progress ?: 0
        val marker = loadSections().filter { it.ms <= pos }.maxByOrNull { it.ms }
        currentSectionView.text = if (marker == null) "SECCIÓN: —" else "SECCIÓN: ${marker.name}"
    }

    private fun songKey(): String {
        val title = titleInput?.text?.toString()?.trim().orEmpty()
        return if (title.isNotEmpty()) "sections_v15_$title" else "sections_v15_song_${songSpinner?.selectedItemPosition ?: 0}"
    }

    private fun loadSections(): List<SectionMarker> {
        val raw = getSharedPreferences("sequence_player_sections_v15", MODE_PRIVATE).getString(songKey(), "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val name = o.optString("name").trim()
                    val ms = o.optInt("ms", -1)
                    if (name.isNotEmpty() && ms >= 0) add(SectionMarker(name, ms))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveSections(markers: List<SectionMarker>) {
        val arr = JSONArray()
        markers.forEach { marker ->
            arr.put(JSONObject().apply {
                put("name", marker.name)
                put("ms", marker.ms)
            })
        }
        getSharedPreferences("sequence_player_sections_v15", MODE_PRIVATE)
            .edit().putString(songKey(), arr.toString()).apply()
    }

    private fun <T : View> collectViews(root: View, clazz: Class<T>, out: MutableList<T>) {
        if (clazz.isInstance(root)) out.add(clazz.cast(root)!!)
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) collectViews(root.getChildAt(i), clazz, out)
        }
    }

    private fun formatTime(ms: Int): String {
        val sec = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(sec / 60, sec % 60)
    }

    override fun onDestroy() {
        sectionHandler.removeCallbacks(sectionUpdater)
        super.onDestroy()
    }
}
