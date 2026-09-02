package com.arielalvarez.sequenceplayer

import android.graphics.Color
import android.media.AudioTrack
import android.media.PlaybackParams
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivityV20 : MainActivityV19() {

    private lateinit var pitchValueView: TextView
    private var pitchSemitones = 0
    private var activePitchSongKey = ""
    private var lastPitchTrack: AudioTrack? = null
    private val pitchHandler = Handler(Looper.getMainLooper())
    private val audioTrackField by lazy { MainActivityV13::class.java.getDeclaredField("audioTrack").apply { isAccessible = true } }

    private val pitchWatcher = object : Runnable {
        override fun run() {
            val track = currentAudioTrack()
            if (track != null && track !== lastPitchTrack) {
                lastPitchTrack = track
                applyPitchToTrack(track, showError = false)
            }
            if (track == null) lastPitchTrack = null
            pitchHandler.postDelayed(this, 25)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mergeEditorIntoSingleScroll()
        installPitchPanel()
        activePitchSongKey = currentSongKey()
        if (activePitchSongKey.isNotBlank()) loadPitch(activePitchSongKey) else updatePitchLabel()
        pitchHandler.post(pitchWatcher)
    }

    override fun onDestroy() {
        pitchHandler.removeCallbacks(pitchWatcher)
        super.onDestroy()
    }

    private fun mergeEditorIntoSingleScroll() {
        val contentRoot = findViewById<ViewGroup>(android.R.id.content)
        if (contentRoot.childCount == 0) return
        val outer = contentRoot.getChildAt(0) as? LinearLayout ?: return
        val playerScroll = findVerticalScroll(outer) ?: return
        val scrollContent = playerScroll.getChildAt(0) as? LinearLayout ?: return
        val sectionWrapper = playerScroll.parent as? ViewGroup ?: return
        val sectionPanels = mutableListOf<View>()
        for (i in 0 until sectionWrapper.childCount) { val child = sectionWrapper.getChildAt(i); if (child !== playerScroll) sectionPanels.add(child) }
        val playerBranch = findDirectBranchContaining(outer, playerScroll) ?: return
        val outerPanels = mutableListOf<View>()
        for (i in 0 until outer.childCount) { val child = outer.getChildAt(i); if (child !== playerBranch) outerPanels.add(child) }
        val header = outerPanels.firstOrNull { containsText(it, "SecuenLive") }
        val remainingOuterPanels = outerPanels.filter { it !== header }
        sectionPanels.forEach { (it.parent as? ViewGroup)?.removeView(it) }
        outerPanels.forEach { (it.parent as? ViewGroup)?.removeView(it) }
        (playerScroll.parent as? ViewGroup)?.removeView(playerScroll)
        contentRoot.removeView(outer)
        if (header != null) scrollContent.addView(header, 0)
        sectionPanels.forEach { scrollContent.addView(it) }
        remainingOuterPanels.forEach { scrollContent.addView(it) }
        scrollContent.setBackgroundColor(Color.rgb(8, 11, 14))
        playerScroll.setBackgroundColor(Color.rgb(8, 11, 14))
        playerScroll.isFillViewport = false
        contentRoot.addView(playerScroll, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun installPitchPanel() {
        val contentRoot = findViewById<ViewGroup>(android.R.id.content)
        val playerScroll = findVerticalScroll(contentRoot) ?: return
        val scrollContent = playerScroll.getChildAt(0) as? LinearLayout ?: return
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(0), dp(10), dp(0), dp(10)) }
        panel.addView(TextView(this).apply { text = "TONO"; setTextColor(Color.WHITE); textSize = 17f })
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        controls.addView(Button(this).apply { text = "−1"; setOnClickListener { changePitch(-1) } }, LinearLayout.LayoutParams(0, dp(46), 1f))
        pitchValueView = TextView(this).apply { text = "0 st"; gravity = Gravity.CENTER; setTextColor(Color.WHITE); textSize = 18f }
        controls.addView(pitchValueView, LinearLayout.LayoutParams(0, dp(46), 1f))
        controls.addView(Button(this).apply { text = "+1"; setOnClickListener { changePitch(1) } }, LinearLayout.LayoutParams(0, dp(46), 1f))
        panel.addView(controls)
        panel.addView(Button(this).apply { text = "VOLVER A TONO ORIGINAL"; setOnClickListener { setPitch(0) } }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))
        val index = if (scrollContent.childCount > 0) 1.coerceAtMost(scrollContent.childCount) else 0
        scrollContent.addView(panel, index)
        updatePitchLabel()
    }

    private fun changePitch(delta: Int) = setPitch(pitchSemitones + delta)

    private fun setPitch(value: Int) {
        pitchSemitones = value.coerceIn(-6, 6)
        updatePitchLabel()
        val key = currentSongKey()
        if (key.isNotBlank()) { activePitchSongKey = key; savePitch(key, pitchSemitones) }
        currentAudioTrack()?.let { track -> lastPitchTrack = track; applyPitchToTrack(track, showError = true) }
    }

    private fun updatePitchLabel() {
        if (!::pitchValueView.isInitialized) return
        pitchValueView.text = if (pitchSemitones > 0) "+$pitchSemitones st" else "$pitchSemitones st"
    }

    private fun savePitch(key: String, semitones: Int) {
        getSharedPreferences("secuenlive_pitch_v20", MODE_PRIVATE).edit().putInt("pitch_$key", semitones.coerceIn(-6, 6)).apply()
    }

    private fun loadPitch(key: String) {
        pitchSemitones = getSharedPreferences("secuenlive_pitch_v20", MODE_PRIVATE).getInt("pitch_$key", 0).coerceIn(-6, 6)
        updatePitchLabel()
        currentAudioTrack()?.let { track -> lastPitchTrack = track; applyPitchToTrack(track, showError = false) }
    }

    override fun onSongKeyChanged(key: String) {
        super.onSongKeyChanged(key)
        if (key.isBlank() || key == activePitchSongKey) return
        activePitchSongKey = key
        loadPitch(key)
    }

    override fun onNewSongCreated() {
        super.onNewSongCreated()
        activePitchSongKey = ""
        pitchSemitones = 0
        updatePitchLabel()
        currentAudioTrack()?.let { track -> lastPitchTrack = track; applyPitchToTrack(track, showError = false) }
    }

    private fun currentAudioTrack(): AudioTrack? = try { audioTrackField.get(this) as? AudioTrack } catch (_: Exception) { null }

    private fun applyPitchToTrack(track: AudioTrack, showError: Boolean): Boolean {
        return try {
            val factor = Math.pow(2.0, pitchSemitones.toDouble() / 12.0).toFloat()
            track.setPlaybackParams(PlaybackParams().setSpeed(1.0f).setPitch(factor).setAudioFallbackMode(PlaybackParams.AUDIO_FALLBACK_MODE_DEFAULT))
            true
        } catch (_: Exception) {
            if (showError) Toast.makeText(this, "Este dispositivo no pudo aplicar el cambio de tono.", Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun findVerticalScroll(view: View): ScrollView? { if (view is ScrollView) return view; if (view is ViewGroup) for (i in 0 until view.childCount) findVerticalScroll(view.getChildAt(i))?.let { return it }; return null }
    private fun findDirectBranchContaining(parent: ViewGroup, target: View): View? { for (i in 0 until parent.childCount) { val child = parent.getChildAt(i); if (child === target || containsView(child, target)) return child }; return null }
    private fun containsView(root: View, target: View): Boolean { if (root === target) return true; if (root is ViewGroup) for (i in 0 until root.childCount) if (containsView(root.getChildAt(i), target)) return true; return false }
    private fun containsText(root: View, needle: String): Boolean { if (root is TextView && root.text?.toString()?.contains(needle, ignoreCase = true) == true) return true; if (root is ViewGroup) for (i in 0 until root.childCount) if (containsText(root.getChildAt(i), needle)) return true; return false }
}
