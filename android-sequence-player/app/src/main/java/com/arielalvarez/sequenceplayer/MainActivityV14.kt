package com.arielalvarez.sequenceplayer

import android.app.Activity
import android.app.AlertDialog
import android.app.LocalActivityManager
import android.content.Intent
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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

@Suppress("DEPRECATION")
class MainActivityV14 : Activity() {
    private data class SectionMarker(val name: String, val ms: Int)

    private lateinit var localManager: LocalActivityManager
    private lateinit var childActivity: Activity
    private lateinit var childRoot: View
    private lateinit var sectionList: LinearLayout
    private lateinit var currentSectionView: TextView
    private var seekBar: SeekBar? = null
    private var songSpinner: Spinner? = null
    private var titleInput: EditText? = null

    private val handler = Handler(Looper.getMainLooper())
    private val sectionUpdater = object : Runnable {
        override fun run() {
            updateCurrentSection()
            handler.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        localManager = LocalActivityManager(this, true)
        localManager.dispatchCreate(savedInstanceState)
        val childWindow = localManager.startActivity("player13", Intent(this, MainActivityV13::class.java))
            ?: error("No se pudo abrir el reproductor estable")
        childActivity = localManager.currentActivity ?: error("No se pudo iniciar el reproductor estable")
        childRoot = childWindow.decorView

        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(10, 14, 20))
        }

        val playerHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(childRoot, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        root.addView(playerHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(10))
            setBackgroundColor(Color.rgb(20, 20, 20))
        }
        panel.addView(TextView(this).apply {
            text = "SEQUENCE PLAYER · 0.14 · SECCIONES"
            setTextColor(Color.rgb(150, 165, 184))
            textSize = 11f
        })

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        currentSectionView = TextView(this).apply {
            text = "SECCIÓN: —"
            setTextColor(Color.WHITE)
            textSize = 16f
        }
        header.addView(currentSectionView, LinearLayout.LayoutParams(0, dp(44), 1f))
        header.addView(Button(this).apply {
            text = "+ MARCAR"
            setOnClickListener { promptAddSection() }
        }, LinearLayout.LayoutParams(dp(112), dp(44)))
        panel.addView(header)

        val sectionScroll = ScrollView(this).apply {
            isFillViewport = true
        }
        sectionList = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        sectionScroll.addView(sectionList, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        panel.addView(sectionScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))

        root.addView(panel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(125)))
        setContentView(root)

        seekBar = findFirst(childRoot, SeekBar::class.java)
        songSpinner = findFirst(childRoot, Spinner::class.java)
        titleInput = findFirst(childRoot, EditText::class.java)

        songSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                handler.postDelayed({ renderSections() }, 150)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        renderSections()
        handler.post(sectionUpdater)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (!::childActivity.isInitialized) return
        try {
            val method = childActivity.javaClass.getDeclaredMethod(
                "onActivityResult",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Intent::class.java
            )
            method.isAccessible = true
            method.invoke(childActivity, requestCode and 0xffff, resultCode, data)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo entregar el archivo al stem", Toast.LENGTH_SHORT).show()
        }
    }

    private fun promptAddSection() {
        val bar = seekBar ?: return
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
                    renderSections()
                    Toast.makeText(this, "$name guardada", Toast.LENGTH_SHORT).show()
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
                text = "Reproduce o mueve la barra y toca + MARCAR"
                setTextColor(Color.rgb(160, 160, 160))
                textSize = 12f
                gravity = Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)))
            updateCurrentSection()
            return
        }

        markers.forEach { marker ->
            val button = Button(this).apply {
                text = "${marker.name}  ${formatTime(marker.ms)}"
                setOnClickListener { jumpTo(marker.ms) }
                setOnLongClickListener {
                    AlertDialog.Builder(this@MainActivityV14)
                        .setTitle("Eliminar ${marker.name}")
                        .setMessage("¿Quitar esta sección?")
                        .setPositiveButton("ELIMINAR") { _, _ ->
                            val updated = loadSections().toMutableList()
                            val index = updated.indexOfFirst { it.name == marker.name && it.ms == marker.ms }
                            if (index >= 0) updated.removeAt(index)
                            saveSections(updated)
                            renderSections()
                        }
                        .setNegativeButton("CANCELAR", null)
                        .show()
                    true
                }
            }
            sectionList.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)).apply {
                marginEnd = dp(5)
            })
        }
        updateCurrentSection()
    }

    private fun updateCurrentSection() {
        if (!::currentSectionView.isInitialized) return
        val pos = seekBar?.progress ?: 0
        val marker = loadSections().filter { it.ms <= pos }.maxByOrNull { it.ms }
        currentSectionView.text = if (marker == null) "SECCIÓN: —" else "SECCIÓN: ${marker.name}"
    }

    private fun jumpTo(ms: Int) {
        val bar = seekBar ?: return
        val target = ms.coerceIn(0, bar.max)
        try {
            val method = childActivity.javaClass.getDeclaredMethod("seekToMs", Int::class.javaPrimitiveType)
            method.isAccessible = true
            method.invoke(childActivity, target)
            bar.progress = target
            updateCurrentSection()
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo saltar a la sección", Toast.LENGTH_SHORT).show()
        }
    }

    private fun songKey(): String {
        val title = titleInput?.text?.toString()?.trim().orEmpty()
        return if (title.isNotEmpty()) "sections_v14_$title" else "sections_v14_song_${songSpinner?.selectedItemPosition ?: 0}"
    }

    private fun loadSections(): List<SectionMarker> {
        val raw = getSharedPreferences("sequence_player_sections_v14", MODE_PRIVATE).getString(songKey(), "[]") ?: "[]"
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
        getSharedPreferences("sequence_player_sections_v14", MODE_PRIVATE)
            .edit()
            .putString(songKey(), arr.toString())
            .apply()
    }

    private fun <T : View> findFirst(root: View, clazz: Class<T>): T? {
        if (clazz.isInstance(root)) return clazz.cast(root)
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findFirst(root.getChildAt(i), clazz)
                if (found != null) return found
            }
        }
        return null
    }

    private fun formatTime(ms: Int): String {
        val sec = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(sec / 60, sec % 60)
    }

    override fun onResume() {
        super.onResume()
        if (::localManager.isInitialized) localManager.dispatchResume()
    }

    override fun onPause() {
        if (::localManager.isInitialized) localManager.dispatchPause(isFinishing)
        super.onPause()
    }

    override fun onStop() {
        if (::localManager.isInitialized) localManager.dispatchStop()
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacks(sectionUpdater)
        if (::localManager.isInitialized) localManager.dispatchDestroy(isFinishing)
        super.onDestroy()
    }
}
