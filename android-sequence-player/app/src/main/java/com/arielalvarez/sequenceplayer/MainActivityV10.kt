package com.arielalvarez.sequenceplayer

import android.app.Activity
import android.app.AlertDialog
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
import android.widget.ArrayAdapter
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
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.InputStream
import kotlin.math.roundToInt

class MainActivityV10 : Activity() {
    companion object {
        private const val PICK_STEM = 2001
        private const val PREFS = "sequence_player_v08"
        private const val SONGS_KEY = "songs_json"
        private const val LAST_SONG_KEY = "last_song"
    }

    private data class LoadedStem(val name: String, val sampleRate: Int, val samples: ShortArray)
    private data class StemState(
        var name: String,
        var uri: String? = null,
        var loaded: LoadedStem? = null,
        var volume: Int = 100,
        var muted: Boolean = false,
        var solo: Boolean = false
    )
    private data class StemPreset(
        var name: String,
        var uri: String,
        var volume: Int,
        var muted: Boolean,
        var solo: Boolean
    )
    private data class SongPreset(var title: String, val stems: MutableList<StemPreset>)

    private val songs = mutableListOf<SongPreset>()
    private val currentStems = mutableListOf<StemState>()
    private var selectedSongIndex = -1
    private var pendingStem: StemState? = null

    @Volatile private var playing = false
    @Volatile private var loopEnabled = false
    @Volatile private var currentFrame = 0
    @Volatile private var audioGeneration = 0
    @Volatile private var songGeneration = 0
    @Volatile private var loadingSong = false

    private var sampleRate = 48000
    private var totalFrames = 0
    private var audioTrack: AudioTrack? = null
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var songSpinner: Spinner
    private lateinit var titleInput: EditText
    private lateinit var stemsContainer: LinearLayout
    private lateinit var currentTimeView: TextView
    private lateinit var durationView: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var playButton: Button
    private lateinit var stopButton: Button
    private lateinit var loopButton: Button
    private lateinit var statusView: TextView

    private val progressUpdater = object : Runnable {
        override fun run() {
            val ms = if (sampleRate > 0) ((currentFrame.toLong() * 1000L) / sampleRate).toInt() else 0
            if (::currentTimeView.isInitialized) currentTimeView.text = formatTime(ms)
            if (::seekBar.isInitialized && !seekBar.isPressed) seekBar.progress = ms.coerceAtMost(seekBar.max)
            handler.postDelayed(this, 100)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setVolumeControlStream(AudioManager.STREAM_MUSIC)
        loadSongLibrary()
        setContentView(buildUi())
        handler.post(progressUpdater)
        refreshSongSpinner()
        restoreLastSong()
    }

    private fun buildUi(): ScrollView {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val root = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(10, 14, 20))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(20))
        }
        root.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        content.addView(TextView(this).apply {
            text = "SEQUENCE PLAYER · 0.10"
            setTextColor(Color.rgb(145, 160, 178)); textSize = 12f
        })
        content.addView(TextView(this).apply {
            text = "Setlist + stems flexibles"
            setTextColor(Color.WHITE); textSize = 26f
        })

        val libraryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, dp(6)) }
        songSpinner = Spinner(this)
        libraryRow.addView(songSpinner, LinearLayout.LayoutParams(0, dp(48), 1f))
        libraryRow.addView(Button(this).apply {
            text = "ABRIR"
            setOnClickListener { val pos = songSpinner.selectedItemPosition; if (pos in songs.indices) loadSong(pos) }
        }, LinearLayout.LayoutParams(dp(90), dp(48)).apply { marginStart = dp(6) })
        content.addView(libraryRow)

        val orderRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        orderRow.addView(Button(this).apply { text = "↑ SUBIR"; setOnClickListener { moveSelectedSong(-1) } }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(4) })
        orderRow.addView(Button(this).apply { text = "↓ BAJAR"; setOnClickListener { moveSelectedSong(1) } }, LinearLayout.LayoutParams(0, dp(42), 1f))
        content.addView(orderRow)

        val titleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        titleInput = EditText(this).apply {
            hint = "Nombre de la canción"; setTextColor(Color.WHITE); setHintTextColor(Color.rgb(130, 140, 150)); setSingleLine(true)
        }
        titleRow.addView(titleInput, LinearLayout.LayoutParams(0, dp(48), 1f))
        titleRow.addView(Button(this).apply { text = "NUEVA"; setOnClickListener { newSong() } }, LinearLayout.LayoutParams(dp(90), dp(48)).apply { marginStart = dp(6) })
        content.addView(titleRow)

        content.addView(Button(this).apply {
            text = "GUARDAR / ACTUALIZAR CANCIÓN"
            setOnClickListener { saveCurrentSong() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(5) })

        val stemsHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, dp(5)) }
        stemsHeader.addView(TextView(this).apply { text = "STEMS"; setTextColor(Color.WHITE); textSize = 18f }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        stemsHeader.addView(Button(this).apply { text = "+ STEM"; setOnClickListener { promptAddStem() } }, LinearLayout.LayoutParams(dp(100), dp(42)))
        content.addView(stemsHeader)

        stemsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(stemsContainer)

        val timeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, 0) }
        currentTimeView = TextView(this).apply { text = "0:00"; setTextColor(Color.WHITE); textSize = 32f }
        durationView = TextView(this).apply { text = "0:00"; setTextColor(Color.rgb(145, 160, 178)); textSize = 17f; gravity = Gravity.END }
        timeRow.addView(currentTimeView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        timeRow.addView(durationView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(timeRow)

        seekBar = SeekBar(this).apply {
            max = 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) currentTimeView.text = formatTime(p) }
                override fun onStartTrackingTouch(s: SeekBar?) = Unit
                override fun onStopTrackingTouch(s: SeekBar?) { seekToMs(s?.progress ?: 0) }
            })
        }
        content.addView(seekBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)))

        val transport = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        stopButton = Button(this).apply { text = "■ STOP"; setOnClickListener { stopPlayback() } }
        playButton = Button(this).apply { text = "▶ PLAY"; setOnClickListener { if (playing) pausePlayback() else startPlayback() } }
        loopButton = Button(this).apply { text = "↻ LOOP"; setOnClickListener { loopEnabled = !loopEnabled; text = if (loopEnabled) "↻ LOOP ✓" else "↻ LOOP" } }
        transport.addView(stopButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(4) })
        transport.addView(playButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(4) })
        transport.addView(loopButton, LinearLayout.LayoutParams(0, dp(48), 1f))
        content.addView(transport)

        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        nav.addView(Button(this).apply { text = "← ANTERIOR"; setOnClickListener { previousSong() } }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(4); topMargin = dp(5) })
        nav.addView(Button(this).apply { text = "SIGUIENTE →"; setOnClickListener { nextSong() } }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { topMargin = dp(5) })
        content.addView(nav)

        statusView = TextView(this).apply { text = "Listo."; setTextColor(Color.rgb(145, 160, 178)); textSize = 12f; setPadding(0, dp(8), 0, 0) }
        content.addView(statusView)

        updateControls()
        return root
    }

    private fun renderStemRows() {
        if (!::stemsContainer.isInitialized) return
        stemsContainer.removeAllViews()
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        currentStems.forEach { stem ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(4), 0, dp(6))
            }
            val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val label = TextView(this).apply {
                val file = stem.loaded?.name ?: if (stem.uri != null) "archivo guardado" else "sin archivo"
                text = "${stem.name} · $file"
                setTextColor(Color.rgb(205, 212, 222)); textSize = 12f
            }
            top.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            top.addView(Button(this).apply {
                text = "+ WAV"
                setOnClickListener { pickFileForStem(stem) }
            }, LinearLayout.LayoutParams(dp(82), dp(40)).apply { marginStart = dp(3) })
            top.addView(Button(this).apply {
                text = "×"
                setOnClickListener {
                    if (playing) stopPlayback()
                    currentStems.remove(stem)
                    renderStemRows(); validateLoadedStems()
                    statusView.text = "Stem eliminado."
                }
            }, LinearLayout.LayoutParams(dp(44), dp(40)).apply { marginStart = dp(3) })
            card.addView(top)

            val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val value = TextView(this).apply { text = "${stem.volume}%"; setTextColor(Color.LTGRAY); textSize = 11f }
            val slider = SeekBar(this).apply {
                max = 100; progress = stem.volume
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { value.text = "$p%"; if (fromUser) stem.volume = p }
                    override fun onStartTrackingTouch(s: SeekBar?) = Unit
                    override fun onStopTrackingTouch(s: SeekBar?) = Unit
                })
            }
            val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(value); addView(slider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30))) }
            controls.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val mute = Button(this).apply {
                text = if (stem.muted) "M ✓" else "M"
                setOnClickListener { stem.muted = !stem.muted; text = if (stem.muted) "M ✓" else "M" }
            }
            val solo = Button(this).apply {
                text = if (stem.solo) "S ✓" else "S"
                setOnClickListener { stem.solo = !stem.solo; text = if (stem.solo) "S ✓" else "S" }
            }
            controls.addView(mute, LinearLayout.LayoutParams(dp(46), dp(40)).apply { marginStart = dp(3) })
            controls.addView(solo, LinearLayout.LayoutParams(dp(46), dp(40)).apply { marginStart = dp(3) })
            card.addView(controls)
            stemsContainer.addView(card)
        }
    }

    private fun promptAddStem() {
        val input = EditText(this).apply { hint = "Ej. Guía, Keys, Guitarras"; setSingleLine(true) }
        AlertDialog.Builder(this)
            .setTitle("Añadir stem")
            .setView(input)
            .setPositiveButton("AÑADIR") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    currentStems.add(StemState(name))
                    renderStemRows(); validateLoadedStems()
                    statusView.text = "Stem añadido: $name"
                }
            }
            .setNegativeButton("CANCELAR", null)
            .show()
    }

    private fun pickFileForStem(stem: StemState) {
        pendingStem = stem
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/wav"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/wav", "audio/x-wav", "audio/wave"))
        }, PICK_STEM)
    }

    @Deprecated("Kept for prototype")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_STEM || resultCode != RESULT_OK) return
        val target = pendingStem ?: return
        pendingStem = null
        val uri = data?.data ?: return
        if (!currentStems.contains(target)) return
        try { contentResolver.takePersistableUriPermission(uri, data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) {}
        val name = getDisplayName(uri) ?: "Archivo WAV"
        if (!name.lowercase().endsWith(".wav")) { Toast.makeText(this, "Solo WAV", Toast.LENGTH_LONG).show(); return }
        val localSongGeneration = songGeneration
        statusView.text = "Cargando $name…"
        Thread {
            try {
                val loaded = loadPcm16Wav(uri, name)
                runOnUiThread {
                    if (localSongGeneration != songGeneration || !currentStems.contains(target)) return@runOnUiThread
                    target.uri = uri.toString(); target.loaded = loaded
                    renderStemRows(); validateLoadedStems()
                    statusView.text = "$name cargado."
                }
            } catch (e: Exception) {
                runOnUiThread { if (localSongGeneration == songGeneration) statusView.text = "No se pudo cargar: ${e.message ?: "WAV incompatible"}" }
            }
        }.start()
    }

    private fun newSong() {
        songGeneration++
        loadingSong = false
        stopPlayback()
        selectedSongIndex = -1
        titleInput.setText("")
        currentStems.clear()
        currentStems.addAll(listOf(StemState("Click"), StemState("Drums"), StemState("Bass")))
        currentFrame = 0; totalFrames = 0
        currentTimeView.text = "0:00"; durationView.text = "0:00"; seekBar.progress = 0; seekBar.max = 1
        renderStemRows(); updateControls()
        statusView.text = "Nueva canción. Añade o quita stems y carga sus WAV."
    }

    private fun saveCurrentSong() {
        val title = titleInput.text.toString().trim()
        if (title.isEmpty()) { Toast.makeText(this, "Ponle nombre a la canción", Toast.LENGTH_SHORT).show(); return }
        if (!allLoaded()) { Toast.makeText(this, "Todos los stems deben tener un WAV compatible", Toast.LENGTH_SHORT).show(); return }
        val preset = SongPreset(title, currentStems.map { StemPreset(it.name, it.uri!!, it.volume, it.muted, it.solo) }.toMutableList())
        if (selectedSongIndex in songs.indices) songs[selectedSongIndex] = preset else { songs.add(preset); selectedSongIndex = songs.lastIndex }
        saveSongLibrary(); refreshSongSpinner(); songSpinner.setSelection(selectedSongIndex)
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(LAST_SONG_KEY, selectedSongIndex).apply()
        statusView.text = "Canción guardada: $title"
    }

    private fun moveSelectedSong(delta: Int) {
        if (songs.size < 2 || selectedSongIndex !in songs.indices) return
        val target = selectedSongIndex + delta
        if (target !in songs.indices) return
        val tmp = songs[selectedSongIndex]; songs[selectedSongIndex] = songs[target]; songs[target] = tmp
        selectedSongIndex = target
        saveSongLibrary(); refreshSongSpinner(); songSpinner.setSelection(selectedSongIndex)
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(LAST_SONG_KEY, selectedSongIndex).apply()
        statusView.text = "Orden del setlist guardado."
    }

    private fun previousSong() { if (songs.isNotEmpty()) loadSong(if (selectedSongIndex <= 0) songs.lastIndex else selectedSongIndex - 1) }
    private fun nextSong() { if (songs.isNotEmpty()) loadSong(if (selectedSongIndex < 0 || selectedSongIndex >= songs.lastIndex) 0 else selectedSongIndex + 1) }

    private fun loadSong(index: Int) {
        if (index !in songs.indices) return
        val loadGeneration = ++songGeneration
        stopPlayback(); loadingSong = true; selectedSongIndex = index; songSpinner.setSelection(index)
        currentStems.clear(); currentFrame = 0; totalFrames = 0; updateControls()
        val song = songs[index]
        titleInput.setText(song.title)
        song.stems.forEach { currentStems.add(StemState(it.name, it.uri, null, it.volume, it.muted, it.solo)) }
        renderStemRows()
        statusView.text = "Cargando ${song.title}…"
        Thread {
            try {
                val loaded = mutableListOf<LoadedStem>()
                song.stems.forEach { preset ->
                    val uri = Uri.parse(preset.uri)
                    loaded.add(loadPcm16Wav(uri, getDisplayName(uri) ?: preset.name))
                }
                runOnUiThread {
                    if (loadGeneration != songGeneration) return@runOnUiThread
                    currentStems.forEachIndexed { i, stem -> stem.loaded = loaded[i] }
                    loadingSong = false
                    renderStemRows(); validateLoadedStems()
                    statusView.text = "${song.title} lista."
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(LAST_SONG_KEY, index).apply()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (loadGeneration == songGeneration) {
                        loadingSong = false; updateControls()
                        statusView.text = "No se pudo abrir ${song.title}: ${e.message ?: "archivo no disponible"}"
                    }
                }
            }
        }.start()
    }

    private fun refreshSongSpinner() {
        val labels = if (songs.isEmpty()) listOf("Sin canciones guardadas") else songs.mapIndexed { i, s -> "${i + 1}. ${s.title}" }
        songSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        if (selectedSongIndex in songs.indices) songSpinner.setSelection(selectedSongIndex)
    }

    private fun restoreLastSong() {
        if (songs.isEmpty()) { newSong(); return }
        val idx = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(LAST_SONG_KEY, 0).coerceIn(0, songs.lastIndex)
        loadSong(idx)
    }

    private fun loadSongLibrary() {
        songs.clear()
        val raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(SONGS_KEY, "[]") ?: "[]"
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val stems = mutableListOf<StemPreset>()
                val newStems = o.optJSONArray("stems")
                if (newStems != null) {
                    for (j in 0 until newStems.length()) {
                        val s = newStems.getJSONObject(j)
                        val uri = s.optString("uri")
                        if (uri.isNotEmpty()) stems.add(StemPreset(s.optString("name", "Stem ${j + 1}"), uri, s.optInt("volume", 100), s.optBoolean("muted", false), s.optBoolean("solo", false)))
                    }
                } else {
                    val legacy = listOf(
                        Triple("Click", o.optString("clickUri"), o.optInt("clickVolume", 100)),
                        Triple("Drums", o.optString("drumsUri"), o.optInt("drumsVolume", 100)),
                        Triple("Bass", o.optString("bassUri"), o.optInt("bassVolume", 100))
                    )
                    legacy.forEach { (name, uri, volume) ->
                        if (uri.isNotEmpty()) {
                            val muted = when (name) { "Click" -> o.optBoolean("clickMuted", false); "Drums" -> o.optBoolean("drumsMuted", false); else -> o.optBoolean("bassMuted", false) }
                            val solo = when (name) { "Click" -> o.optBoolean("clickSolo", false); "Drums" -> o.optBoolean("drumsSolo", false); else -> o.optBoolean("bassSolo", false) }
                            stems.add(StemPreset(name, uri, volume, muted, solo))
                        }
                    }
                }
                if (stems.isNotEmpty()) songs.add(SongPreset(o.optString("title", "Canción ${i + 1}"), stems))
            }
        } catch (_: Exception) {}
    }

    private fun saveSongLibrary() {
        val arr = JSONArray()
        songs.forEach { song ->
            arr.put(JSONObject().apply {
                put("title", song.title)
                put("stems", JSONArray().apply {
                    song.stems.forEach { s -> put(JSONObject().apply { put("name", s.name); put("uri", s.uri); put("volume", s.volume); put("muted", s.muted); put("solo", s.solo) }) }
                })
            })
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(SONGS_KEY, arr.toString()).apply()
    }

    private fun validateLoadedStems() {
        if (currentStems.isEmpty()) { totalFrames = 0; updateControls(); return }
        val loaded = currentStems.mapNotNull { it.loaded }
        if (loaded.size != currentStems.size) { updateControls(); return }
        val rates = loaded.map { it.sampleRate }.toSet()
        if (rates.size > 1) { statusView.text = "Todos los WAV deben usar el mismo sample rate."; updateControls(); return }
        sampleRate = loaded.first().sampleRate
        totalFrames = loaded.maxOf { it.samples.size }
        val durationMs = ((totalFrames.toLong() * 1000L) / sampleRate).toInt()
        seekBar.max = durationMs.coerceAtLeast(1); durationView.text = formatTime(durationMs); currentFrame = 0
        updateControls()
    }

    private fun allLoaded(): Boolean = currentStems.isNotEmpty() && currentStems.all { it.uri != null && it.loaded != null } && currentStems.mapNotNull { it.loaded?.sampleRate }.toSet().size == 1

    private fun startPlayback() {
        if (playing || loadingSong || !allLoaded()) return
        playing = true; playButton.text = "❚❚ PAUSA"; statusView.text = "Reproduciendo."; startAudioEngine()
    }
    private fun pausePlayback() { playing = false; audioGeneration++; releaseTrack(); playButton.text = "▶ PLAY"; statusView.text = "Pausado." }
    private fun stopPlayback() { playing = false; audioGeneration++; releaseTrack(); currentFrame = 0; if (::seekBar.isInitialized) seekBar.progress = 0; if (::currentTimeView.isInitialized) currentTimeView.text = "0:00"; if (::playButton.isInitialized) playButton.text = "▶ PLAY" }
    private fun seekToMs(ms: Int) { val newFrame = ((ms.toLong() * sampleRate) / 1000L).toInt().coerceIn(0, totalFrames); val wasPlaying = playing; audioGeneration++; releaseTrack(); currentFrame = newFrame; if (wasPlaying) startAudioEngine() }
    private fun releaseTrack() { try { audioTrack?.pause() } catch (_: Exception) {}; try { audioTrack?.flush() } catch (_: Exception) {}; try { audioTrack?.release() } catch (_: Exception) {}; audioTrack = null }

    private fun startAudioEngine() {
        val stems = currentStems.map { it to (it.loaded ?: return) }
        val localGeneration = ++audioGeneration
        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setBufferSizeInBytes(minBuffer * 2).setTransferMode(AudioTrack.MODE_STREAM).build()
        audioTrack = track; track.play()
        Thread {
            val framesPerBlock = 1024
            val out = ShortArray(framesPerBlock * 2)
            while (playing && localGeneration == audioGeneration) {
                var frame = currentFrame
                if (frame >= totalFrames) {
                    if (loopEnabled) { currentFrame = 0; frame = 0 } else { playing = false; handler.post { playButton.text = "▶ PLAY"; statusView.text = "Terminó la canción." }; break }
                }
                val frames = minOf(framesPerBlock, totalFrames - frame)
                val anySolo = currentStems.any { it.solo }
                var p = 0
                for (i in 0 until frames) {
                    val idx = frame + i
                    var left = 0f; var right = 0f
                    stems.forEach { (state, loaded) ->
                        val gain = gain(state.volume, state.muted, state.solo, anySolo)
                        val sample = if (idx < loaded.samples.size) loaded.samples[idx].toInt() * gain else 0f
                        val cue = state.name.contains("click", true) || state.name.contains("guía", true) || state.name.contains("guia", true)
                        if (cue) left += sample else right += sample
                    }
                    out[p++] = left.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    out[p++] = right.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                if (track.write(out, 0, frames * 2, AudioTrack.WRITE_BLOCKING) < 0) break
                currentFrame += frames
            }
            try { track.stop() } catch (_: Exception) {}; try { track.release() } catch (_: Exception) {}; if (audioTrack === track) audioTrack = null
        }.apply { name = "SequencePlayerAudio10"; priority = Thread.MAX_PRIORITY; start() }
    }

    private fun gain(volume: Int, muted: Boolean, solo: Boolean, anySolo: Boolean): Float { if (muted) return 0f; if (anySolo && !solo) return 0f; return volume.coerceIn(0, 100) / 100f }
    private fun updateControls() { val ready = allLoaded() && !loadingSong; if (::playButton.isInitialized) playButton.isEnabled = ready; if (::stopButton.isInitialized) stopButton.isEnabled = ready; if (::loopButton.isInitialized) loopButton.isEnabled = ready; if (::seekBar.isInitialized) seekBar.isEnabled = ready }

    private fun loadPcm16Wav(uri: Uri, name: String): LoadedStem {
        val input = BufferedInputStream(contentResolver.openInputStream(uri) ?: error("No se pudo abrir el archivo"), 128 * 1024)
        input.use { stream ->
            if (readFourCc(stream) != "RIFF") error("No es WAV RIFF"); readLeInt(stream); if (readFourCc(stream) != "WAVE") error("WAV no válido")
            var format = -1; var channels = -1; var rate = -1; var bits = -1
            while (true) {
                val id = readFourCc(stream); val size = readLeInt(stream)
                when (id) {
                    "fmt " -> { if (size < 16) error("fmt inválido"); format = readLeShort(stream); channels = readLeShort(stream); rate = readLeInt(stream); readLeInt(stream); readLeShort(stream); bits = readLeShort(stream); skipFully(stream, size - 16); if ((size and 1) == 1) skipFully(stream, 1) }
                    "data" -> {
                        if (format != 1) error("Solo PCM"); if (bits != 16) error("Solo 16 bits"); if (channels < 1) error("Canales inválidos"); if (rate <= 0) error("Sample rate inválido")
                        val bytesPerFrame = channels * 2; val count = size / bytesPerFrame; val samples = ShortArray(count); val buf = ByteArray(bytesPerFrame)
                        for (frame in 0 until count) { readFully(stream, buf, 0, bytesPerFrame); var sum = 0; var off = 0; repeat(channels) { val lo = buf[off].toInt() and 0xff; val hi = buf[off + 1].toInt(); sum += ((hi shl 8) or lo).toShort().toInt(); off += 2 }; samples[frame] = (sum / channels).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort() }
                        return LoadedStem(name, rate, samples)
                    }
                    else -> { skipFully(stream, size); if ((size and 1) == 1) skipFully(stream, 1) }
                }
            }
        }
    }

    private fun readFourCc(input: InputStream): String { val b = ByteArray(4); readFully(input, b, 0, 4); return String(b, Charsets.US_ASCII) }
    private fun readLeInt(input: InputStream): Int { val a = input.read(); val b = input.read(); val c = input.read(); val d = input.read(); if (a < 0 || b < 0 || c < 0 || d < 0) throw EOFException(); return a or (b shl 8) or (c shl 16) or (d shl 24) }
    private fun readLeShort(input: InputStream): Int { val a = input.read(); val b = input.read(); if (a < 0 || b < 0) throw EOFException(); return a or (b shl 8) }
    private fun skipFully(input: InputStream, bytes: Int) { var r = bytes; while (r > 0) { val s = input.skip(r.toLong()).toInt(); if (s > 0) r -= s else { if (input.read() < 0) throw EOFException(); r-- } } }
    private fun readFully(input: InputStream, buf: ByteArray, off: Int, len: Int) { var total = 0; while (total < len) { val n = input.read(buf, off + total, len - total); if (n < 0) throw EOFException(); total += n } }
    private fun getDisplayName(uri: Uri): String? { contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (i >= 0 && c.moveToFirst()) return c.getString(i) }; return uri.lastPathSegment }
    private fun formatTime(ms: Int): String { val sec = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(sec / 60, sec % 60) }

    override fun onDestroy() { playing = false; audioGeneration++; songGeneration++; handler.removeCallbacks(progressUpdater); releaseTrack(); super.onDestroy() }
}
