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
    private data class Marker(val name:String,val ms:Int)
    private val guideHandler=Handler(Looper.getMainLooper());private var guideEnabled=false;private var tts:TextToSpeech?=null;private var ttsReady=false;private var timeline:SeekBar?=null;private lateinit var guideToggle:Button;private lateinit var guideStatus:TextView;private var lastSong="";private var lastGuidePos=-1;private val announcedMarkers=mutableSetOf<String>();private var queuedGuideName="";private var queuedGuideTriggerMs=-1;private var queuedGuideAnnounced=false
    private val guideWatcher=object:Runnable{override fun run(){updateGuide();guideHandler.postDelayed(this,40L)}}
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);tts=TextToSpeech(this,this);findTimeline();installGuidePanel();installDeleteButton();installLiveButton();lastSong=currentSongKey();guideHandler.post(guideWatcher)}
    private fun rootLayout():LinearLayout?=findViewById<ViewGroup>(android.R.id.content).getChildAt(0)as?LinearLayout
    private fun installGuidePanel(){val d=resources.displayMetrics.density;fun dp(v:Int)=(v*d).toInt();val root=rootLayout()?:return;val panel=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(5),dp(12),dp(6));setBackgroundColor(Color.rgb(24,31,40))};guideStatus=TextView(this).apply{text="GUÍA: lista";setTextColor(Color.LTGRAY);textSize=12f};panel.addView(guideStatus,LinearLayout.LayoutParams(0,dp(44),1f));guideToggle=Button(this).apply{text="GUÍA AUTO: OFF";setOnClickListener{guideEnabled=!guideEnabled;resetAutomaticGuide();text=if(guideEnabled)"GUÍA AUTO: ON" else "GUÍA AUTO: OFF";guideStatus.text=if(guideEnabled)"GUÍA: avisará 1 compás antes" else "GUÍA: apagada"}};panel.addView(guideToggle,LinearLayout.LayoutParams(dp(170),dp(44)));root.addView(panel,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(56)))}
    private fun installDeleteButton(){val d=resources.displayMetrics.density;fun dp(v:Int)=(v*d).toInt();rootLayout()?.addView(Button(this).apply{text="🗑 BORRAR CANCIÓN";setOnClickListener{confirmDeleteSong()}},LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(46)).apply{leftMargin=dp(12);rightMargin=dp(12);topMargin=dp(4);bottomMargin=dp(3)})}
    private fun installLiveButton(){val d=resources.displayMetrics.density;fun dp(v:Int)=(v*d).toInt();rootLayout()?.addView(Button(this).apply{text="🎵 MODO EN VIVO";setOnClickListener{startActivity(Intent(this@MainActivityV19,LiveModeActivity::class.java).putExtra("song",currentSongKey()))}},LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)).apply{leftMargin=dp(12);rightMargin=dp(12);bottomMargin=dp(8)})}
    private fun confirmDeleteSong(){val title=currentSongKey().ifBlank{"esta canción"};AlertDialog.Builder(this).setTitle("Borrar canción").setMessage("¿Seguro que quieres borrar “$title” del setlist?").setPositiveButton("BORRAR"){_,_->deleteSelectedSong()}.setNegativeButton("CANCELAR",null).show()}
    private fun deleteSelectedSong(){try{val f=MainActivityV13::class.java.getDeclaredField("songs").apply{isAccessible=true};@Suppress("UNCHECKED_CAST")val songs=f.get(this)as MutableList<Any>;val idxF=MainActivityV13::class.java.getDeclaredField("selectedSongIndex").apply{isAccessible=true};val index=idxF.getInt(this);if(index !in songs.indices){Toast.makeText(this,"No hay una canción seleccionada para borrar",Toast.LENGTH_SHORT).show();return};val deleted=currentSongKey();invokeBase("stopPlayback");songs.removeAt(index);invokeBase("saveSongLibrary");invokeBase("refreshSongSpinner");resetAutomaticGuide();clearQueuedGuide();tts?.stop();if(songs.isEmpty()){idxF.setInt(this,-1);invokeBase("newSong");guideHandler.postDelayed({removeEmptyClickNow()},80L)}else{val next=index.coerceAtMost(songs.lastIndex);idxF.setInt(this,next);invokeBase("loadSong",next)};Toast.makeText(this,"Canción borrada: $deleted",Toast.LENGTH_SHORT).show()}catch(_:Exception){Toast.makeText(this,"No se pudo borrar la canción",Toast.LENGTH_LONG).show()}}
    private fun removeEmptyClickNow(){try{val m=MainActivityV17::class.java.getDeclaredMethod("removeEmptyClickStem");m.isAccessible=true;m.invoke(this)}catch(_:Exception){}}
    private fun invokeBase(name:String,intArg:Int?=null){val m=if(intArg==null)MainActivityV13::class.java.getDeclaredMethod(name)else MainActivityV13::class.java.getDeclaredMethod(name,Int::class.javaPrimitiveType);m.isAccessible=true;if(intArg==null)m.invoke(this)else m.invoke(this,intArg)}
    private fun findTimeline(){val bars=mutableListOf<SeekBar>();collect(findViewById(android.R.id.content),bars);timeline=bars.firstOrNull{it.max!=100}}
    private fun updateGuide(){if(!guideEnabled||!ttsReady)return;val song=currentSongKey();if(song!=lastSong){lastSong=song;resetAutomaticGuide();clearQueuedGuide();tts?.stop()};val pos=timeline?.progress?:return;val previousPos=lastGuidePos;val barMs=quantizedBarDurationMs().coerceAtLeast(100);if(previousPos>=0&&pos<previousPos-250)announcedMarkers.clear();lastGuidePos=pos;if(queuedGuideTriggerMs>=0&&queuedGuideName.isNotBlank()){val announceAt=(queuedGuideTriggerMs-barMs).coerceAtLeast(0);guideStatus.text="SALTO: $queuedGuideName";if(!queuedGuideAnnounced&&pos>=announceAt&&pos<queuedGuideTriggerMs){queuedGuideAnnounced=true;speakSection(queuedGuideName,"jump_${queuedGuideTriggerMs}_${queuedGuideName}")};return};val markers=loadMarkers();if(markers.isEmpty())return;for(marker in markers){val key="$song|${marker.name}|${marker.ms}";if(key in announcedMarkers)continue;val announceAt=(marker.ms-barMs).coerceAtLeast(0);val inWindow=pos>=announceAt&&pos<marker.ms;val crossed=previousPos>=0&&previousPos<announceAt&&pos>=announceAt&&pos<marker.ms;if(inWindow||crossed){announcedMarkers.add(key);guideStatus.text="SIGUE: ${marker.name}";speakSection(marker.name,key);break}}}
    private fun resetAutomaticGuide(){announcedMarkers.clear();lastGuidePos=-1}
    override fun onSectionJumpQueued(name:String,triggerMs:Int){queuedGuideName=name;queuedGuideTriggerMs=triggerMs;queuedGuideAnnounced=false;if(::guideStatus.isInitialized)guideStatus.text="SALTO: $name al final de sección"}
    override fun onSectionJumpCancelled(){clearQueuedGuide();if(::guideStatus.isInitialized&&guideEnabled)guideStatus.text="GUÍA: salto cancelado";tts?.stop()}
    override fun onSectionJumpExecuted(name:String){clearQueuedGuide();resetAutomaticGuide();if(::guideStatus.isInitialized&&guideEnabled)guideStatus.text="ENTRANDO: $name"}
    private fun clearQueuedGuide(){queuedGuideName="";queuedGuideTriggerMs=-1;queuedGuideAnnounced=false}
    private fun speakSection(name:String,id:String){tts?.speak(normalizeName(name),TextToSpeech.QUEUE_ADD,null,id)}
    private fun normalizeName(name:String):String{val n=name.trim();return when(n.lowercase(Locale.getDefault())){"intro","introducción","introduccion"->"Intro";"verso"->"Verso";"coro"->"Coro";"puente"->"Puente";"final","outro"->"Final";else->n}}
    private fun loadMarkers():List<Marker>{val title=currentSongKey();if(title.isBlank())return emptyList();val raw=getSharedPreferences("sequence_player_sections_v15",MODE_PRIVATE).getString("sections_v15_$title","[]")?:"[]";return try{val a=JSONArray(raw);buildList{for(i in 0 until a.length()){val o=a.getJSONObject(i);val n=o.optString("name").trim();val ms=o.optInt("ms",-1);if(n.isNotEmpty()&&ms>=0)add(Marker(n,ms))}}.sortedBy{it.ms}}catch(_:Exception){emptyList()}}
    private fun collect(root:View,out:MutableList<SeekBar>){if(root is SeekBar)out.add(root);if(root is ViewGroup)for(i in 0 until root.childCount)collect(root.getChildAt(i),out)}
    override fun onInit(status:Int){if(status==TextToSpeech.SUCCESS){ttsReady=true;val r=tts?.setLanguage(Locale("es","MX"));if(r==TextToSpeech.LANG_MISSING_DATA||r==TextToSpeech.LANG_NOT_SUPPORTED)tts?.language=Locale("es","ES")}}
    override fun onDestroy(){guideHandler.removeCallbacks(guideWatcher);try{tts?.stop();tts?.shutdown()}catch(_:Exception){};tts=null;super.onDestroy()}
}
