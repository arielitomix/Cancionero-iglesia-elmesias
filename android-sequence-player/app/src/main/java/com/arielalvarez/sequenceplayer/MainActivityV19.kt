package com.arielalvarez.sequenceplayer

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import java.util.Locale

class MainActivityV19 : MainActivityV18(), TextToSpeech.OnInitListener {
    private data class Marker(val name: String, val ms: Int)
    private val guideHandler = Handler(Looper.getMainLooper())
    private var guideEnabled = false
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var timeline: SeekBar? = null
    private lateinit var guideToggle: Button
    private lateinit var guideStatus: TextView
    private var lastSong = ""
    private var lastGuidePos = -1
    private val announcedMarkers = mutableSetOf<String>()
    private var queuedGuideName = ""
    private var queuedGuideTriggerMs = -1
    private var queuedGuideAnnounced = false
    private var liveRequestToken = 0
    private var liveTransportToken = 0

    private val guideWatcher = object : Runnable {
        override fun run() {
            updateLiveBridge()
            updateGuide()
            guideHandler.postDelayed(this, 40L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        findTimeline()
        installGuidePanel()
        installDeleteButton()
        installLiveButton()
        lastSong = currentSongKey()
        guideHandler.post(guideWatcher)
    }

    private fun rootLayout(): LinearLayout? = findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as? LinearLayout

    private fun installGuidePanel() {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val root = rootLayout() ?: return
        val panel = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(dp(12),dp(5),dp(12),dp(6)); setBackgroundColor(Color.rgb(24,31,40)) }
        guideStatus = TextView(this).apply { text="GUÍA: lista"; setTextColor(Color.LTGRAY); textSize=12f }
        panel.addView(guideStatus, LinearLayout.LayoutParams(0,dp(44),1f))
        guideToggle = Button(this).apply {
            text="GUÍA AUTO: OFF"
            setOnClickListener {
                guideEnabled=!guideEnabled
                resetAutomaticGuide()
                text=if(guideEnabled)"GUÍA AUTO: ON" else "GUÍA AUTO: OFF"
                guideStatus.text=if(guideEnabled)"GUÍA: avisará 1 compás antes" else "GUÍA: apagada"
            }
        }
        panel.addView(guideToggle, LinearLayout.LayoutParams(dp(170),dp(44)))
        root.addView(panel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(56)))
    }

    private fun installDeleteButton() {
        val density=resources.displayMetrics.density
        fun dp(v:Int)=(v*density).toInt()
        val root=rootLayout()?:return
        root.addView(Button(this).apply{text="🗑 BORRAR CANCIÓN";setOnClickListener{confirmDeleteSong()}},LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(46)).apply{leftMargin=dp(12);rightMargin=dp(12);topMargin=dp(4);bottomMargin=dp(6)})
    }

    private fun installLiveButton() {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val root = rootLayout() ?: return
        val button = Button(this).apply {
            text = "🎵 MODO EN VIVO"
            setOnClickListener {
                LiveModeBridge.positionMs = timeline?.progress ?: 0
                LiveModeBridge.pendingSectionName = ""
                val intent = Intent(this@MainActivityV19, LiveModeActivity::class.java)
                intent.putExtra("song", currentSongKey())
                startActivity(intent)
            }
        }
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply {
            leftMargin = dp(12)
            rightMargin = dp(12)
            bottomMargin = dp(8)
        }
        root.addView(button, params)
    }

    private fun updateLiveBridge() {
        LiveModeBridge.positionMs = timeline?.progress ?: 0
        LiveModeBridge.pendingSectionName = queuedGuideName

        if (LiveModeBridge.requestToken != liveRequestToken) {
            liveRequestToken = LiveModeBridge.requestToken
            val name = LiveModeBridge.requestedSectionName
            val ms = LiveModeBridge.requestedSectionMs
            if (name.isNotBlank() && ms >= 0) requestLiveSection(name, ms)
        }

        if (LiveModeBridge.transportToken != liveTransportToken) {
            liveTransportToken = LiveModeBridge.transportToken
            when (LiveModeBridge.transportCommand) {
                "toggle" -> toggleLivePlayback()
                "stop" -> invokeBase("stopPlayback")
            }
        }
    }

    private fun requestLiveSection(name: String, ms: Int) {
        try {
            val markerClass = Class.forName("com.arielalvarez.sequenceplayer.MainActivityV16\$SectionMarker")
            val constructor = markerClass.getDeclaredConstructor(String::class.java, Int::class.javaPrimitiveType)
            constructor.isAccessible = true
            val marker = constructor.newInstance(name, ms)
            val method = MainActivityV16::class.java.getDeclaredMethod("requestSectionJump", markerClass)
            method.isAccessible = true
            method.invoke(this, marker)
        } catch (_: Exception) {
            Toast.makeText(this, "No se pudo preparar esa sección", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleLivePlayback() {
        try {
            val field = MainActivityV13::class.java.getDeclaredField("playing")
            field.isAccessible = true
            if (field.getBoolean(this)) invokeBase("pausePlayback") else invokeBase("startPlayback")
        } catch (_: Exception) {
            Toast.makeText(this, "No se pudo cambiar la reproducción", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDeleteSong(){val title=currentSongKey().ifBlank{"esta canción"};AlertDialog.Builder(this).setTitle("Borrar canción").setMessage("¿Seguro que quieres borrar “$title” del setlist?").setPositiveButton("BORRAR"){_,_->deleteSelectedSong()}.setNegativeButton("CANCELAR",null).show()}
    private fun deleteSelectedSong(){try{val songsField=MainActivityV13::class.java.getDeclaredField("songs").apply{isAccessible=true};@Suppress("UNCHECKED_CAST")val songs=songsField.get(this)as MutableList<Any>;val indexField=MainActivityV13::class.java.getDeclaredField("selectedSongIndex").apply{isAccessible=true};val index=indexField.getInt(this);if(index !in songs.indices){Toast.makeText(this,"No hay una canción seleccionada para borrar",Toast.LENGTH_SHORT).show();return};val deletedTitle=currentSongKey();invokeBase("stopPlayback");songs.removeAt(index);invokeBase("saveSongLibrary");invokeBase("refreshSongSpinner");resetAutomaticGuide();clearQueuedGuide();tts?.stop();if(songs.isEmpty()){indexField.setInt(this,-1);invokeBase("newSong");guideHandler.postDelayed({removeEmptyClickNow()},80L)}else{val nextIndex=index.coerceAtMost(songs.lastIndex);indexField.setInt(this,nextIndex);invokeBase("loadSong",nextIndex)};Toast.makeText(this,"Canción borrada: $deletedTitle",Toast.LENGTH_SHORT).show()}catch(e:Exception){Toast.makeText(this,"No se pudo borrar la canción",Toast.LENGTH_LONG).show()}}
    private fun removeEmptyClickNow(){try{val method=MainActivityV17::class.java.getDeclaredMethod("removeEmptyClickStem");method.isAccessible=true;method.invoke(this)}catch(_:Exception){}}
    private fun invokeBase(name:String,intArg:Int?=null){val method=if(intArg==null)MainActivityV13::class.java.getDeclaredMethod(name)else MainActivityV13::class.java.getDeclaredMethod(name,Int::class.javaPrimitiveType);method.isAccessible=true;if(intArg==null)method.invoke(this)else method.invoke(this,intArg)}
    private fun findTimeline(){val bars=mutableListOf<SeekBar>();collect(findViewById(android.R.id.content),bars);timeline=bars.firstOrNull{it.max!=100}}

    private fun updateGuide() {
        if (!guideEnabled || !ttsReady) return
        val song = currentSongKey()
        if (song != lastSong) {
            lastSong = song
            resetAutomaticGuide()
            clearQueuedGuide()
            tts?.stop()
        }
        val pos = timeline?.progress ?: return
        val barMs = quantizedBarDurationMs().coerceAtLeast(100)
        if (lastGuidePos >= 0 && pos < lastGuidePos - 250) announcedMarkers.clear()
        lastGuidePos = pos
        if (queuedGuideTriggerMs >= 0 && queuedGuideName.isNotBlank()) {
            val announceAt = (queuedGuideTriggerMs - barMs).coerceAtLeast(0)
            guideStatus.text = "SALTO: $queuedGuideName"
            if (!queuedGuideAnnounced && pos >= announceAt && pos < queuedGuideTriggerMs) {
                queuedGuideAnnounced = true
                speakSection(queuedGuideName, "jump_${queuedGuideTriggerMs}_${queuedGuideName}")
            }
            return
        }
        val markers = loadMarkers()
        if (markers.isEmpty()) return
        markers.forEach { marker ->
            val key = "$song|${marker.name}|${marker.ms}"
            if (key in announcedMarkers) return@forEach
            val announceAt = (marker.ms - barMs).coerceAtLeast(0)
            if (pos >= announceAt && pos < marker.ms) {
                announcedMarkers.add(key)
                guideStatus.text = "SIGUE: ${marker.name}"
                speakSection(marker.name, key)
                return
            }
        }
    }

    private fun resetAutomaticGuide(){announcedMarkers.clear();lastGuidePos=-1}

    override fun onSectionJumpQueued(name:String,triggerMs:Int){
        tts?.stop()
        queuedGuideName=name
        queuedGuideTriggerMs=triggerMs
        queuedGuideAnnounced=false
        LiveModeBridge.pendingSectionName=name
        if(::guideStatus.isInitialized)guideStatus.text="SALTO: $name al final de sección"
    }

    override fun onSectionJumpCancelled(){clearQueuedGuide();LiveModeBridge.pendingSectionName="";if(::guideStatus.isInitialized&&guideEnabled)guideStatus.text="GUÍA: salto cancelado";tts?.stop()}
    override fun onSectionJumpExecuted(name:String){clearQueuedGuide();LiveModeBridge.pendingSectionName="";resetAutomaticGuide();if(::guideStatus.isInitialized&&guideEnabled)guideStatus.text="ENTRANDO: $name"}
    private fun clearQueuedGuide(){queuedGuideName="";queuedGuideTriggerMs=-1;queuedGuideAnnounced=false}
    private fun speakSection(name:String,utteranceId:String){val spoken=normalizeName(name);tts?.speak(spoken,TextToSpeech.QUEUE_ADD,null,utteranceId)}
    private fun normalizeName(name:String):String{val n=name.trim();return when(n.lowercase(Locale.getDefault())){"intro","introducción","introduccion"->"Intro";"verso"->"Verso";"coro"->"Coro";"puente"->"Puente";"final","outro"->"Final";else->n}}
    private fun loadMarkers():List<Marker>{val title=currentSongKey();if(title.isBlank())return emptyList();val prefs=getSharedPreferences("sequence_player_sections_v15",MODE_PRIVATE);val raw=prefs.getString("sections_v15_$title","[]")?:"[]";return try{val arr=JSONArray(raw);buildList{for(i in 0 until arr.length()){val o=arr.getJSONObject(i);val name=o.optString("name").trim();val ms=o.optInt("ms",-1);if(name.isNotEmpty()&&ms>=0)add(Marker(name,ms))}}.sortedBy{it.ms}}catch(_:Exception){emptyList()}}
    private fun collect(root:View,out:MutableList<SeekBar>){if(root is SeekBar)out.add(root);if(root is ViewGroup)for(i in 0 until root.childCount)collect(root.getChildAt(i),out)}
    override fun onInit(status:Int){if(status==TextToSpeech.SUCCESS){ttsReady=true;val result=tts?.setLanguage(Locale("es","MX"));if(result==TextToSpeech.LANG_MISSING_DATA||result==TextToSpeech.LANG_NOT_SUPPORTED)tts?.language=Locale("es","ES")}}
    override fun onDestroy(){guideHandler.removeCallbacks(guideWatcher);try{tts?.stop();tts?.shutdown()}catch(_:Exception){};tts=null;super.onDestroy()}
}
