package com.arielalvarez.sequenceplayer

import android.app.Activity
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
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

class MainActivityV07 : Activity() {

    companion object {
        private const val PICK_CLICK = 1001
        private const val PICK_DRUMS = 1002
        private const val PICK_BASS = 1003
        private const val PREFS = "sequence_player_v07"
    }

    private data class LoadedStem(val name: String, val sampleRate: Int, val samples: ShortArray)

    private var clickStem: LoadedStem? = null
    private var drumsStem: LoadedStem? = null
    private var bassStem: LoadedStem? = null

    private var clickUri: Uri? = null
    private var drumsUri: Uri? = null
    private var bassUri: Uri? = null

    @Volatile private var clickVolume = 100
    @Volatile private var drumsVolume = 100
    @Volatile private var bassVolume = 100
    @Volatile private var clickMuted = false
    @Volatile private var drumsMuted = false
    @Volatile private var bassMuted = false
    @Volatile private var clickSolo = false
    @Volatile private var drumsSolo = false
    @Volatile private var bassSolo = false

    @Volatile private var playing = false
    @Volatile private var loopEnabled = false
    @Volatile private var currentFrame = 0
    @Volatile private var generation = 0

    private var sampleRate = 48000
    private var totalFrames = 0
    private var audioTrack: AudioTrack? = null
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var clickNameView: TextView
    private lateinit var drumsNameView: TextView
    private lateinit var bassNameView: TextView
    private lateinit var currentTimeView: TextView
    private lateinit var durationView: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var playButton: Button
    private lateinit var stopButton: Button
    private lateinit var loopButton: Button
    private lateinit var statusView: TextView
    private lateinit var clickMuteButton: Button
    private lateinit var drumsMuteButton: Button
    private lateinit var bassMuteButton: Button
    private lateinit var clickSoloButton: Button
    private lateinit var drumsSoloButton: Button
    private lateinit var bassSoloButton: Button

    private val progressUpdater = object : Runnable {
        override fun run() {
            val ms = if (sampleRate > 0) ((currentFrame.toLong() * 1000L) / sampleRate).toInt() else 0
            currentTimeView.text = formatTime(ms)
            if (!seekBar.isPressed) seekBar.progress = ms.coerceAtMost(seekBar.max)
            handler.postDelayed(this, 100)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setVolumeControlStream(AudioManager.STREAM_MUSIC)
        restoreMixerSettings()
        setContentView(buildUi())
        updateControls()
        handler.post(progressUpdater)
        restoreSavedSong()
    }

    private fun buildUi(): LinearLayout {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(18))
            setBackgroundColor(Color.rgb(10, 14, 20))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        root.addView(TextView(this).apply {
            text = "SEQUENCE PLAYER · ANDROID"
            setTextColor(Color.rgb(145, 160, 178)); textSize = 12f
        })
        root.addView(TextView(this).apply {
            text = "Sequence Player"; setTextColor(Color.WHITE); textSize = 28f
        })
        root.addView(TextView(this).apply {
            text = "0.7 · recuerda tu canción y mezcla"
            setTextColor(Color.rgb(145, 160, 178)); textSize = 13f; setPadding(0,0,0,dp(10))
        })

        root.addView(Button(this).apply { text = "+ CLICK WAV"; setOnClickListener { openPicker(PICK_CLICK) } },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))
        clickNameView = fileLabel("Click: ninguno", ::dp); root.addView(clickNameView)
        root.addView(stemControls("CLICK", ::dp, { clickVolume }, { clickVolume = it; saveMixerSettings() },
            { clickMuted = !clickMuted; updateStemButtons(); saveMixerSettings() },
            { clickSolo = !clickSolo; updateStemButtons(); saveMixerSettings() },
            { m,s -> clickMuteButton=m; clickSoloButton=s }))

        root.addView(Button(this).apply { text = "+ DRUMS WAV"; setOnClickListener { openPicker(PICK_DRUMS) } },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))
        drumsNameView = fileLabel("Drums: ninguno", ::dp); root.addView(drumsNameView)
        root.addView(stemControls("DRUMS", ::dp, { drumsVolume }, { drumsVolume = it; saveMixerSettings() },
            { drumsMuted = !drumsMuted; updateStemButtons(); saveMixerSettings() },
            { drumsSolo = !drumsSolo; updateStemButtons(); saveMixerSettings() },
            { m,s -> drumsMuteButton=m; drumsSoloButton=s }))

        root.addView(Button(this).apply { text = "+ BASS WAV"; setOnClickListener { openPicker(PICK_BASS) } },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))
        bassNameView = fileLabel("Bass: ninguno", ::dp); root.addView(bassNameView)
        root.addView(stemControls("BASS", ::dp, { bassVolume }, { bassVolume = it; saveMixerSettings() },
            { bassMuted = !bassMuted; updateStemButtons(); saveMixerSettings() },
            { bassSolo = !bassSolo; updateStemButtons(); saveMixerSettings() },
            { m,s -> bassMuteButton=m; bassSoloButton=s }))

        val timeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        currentTimeView = TextView(this).apply { text="0:00"; setTextColor(Color.WHITE); textSize=34f }
        durationView = TextView(this).apply { text="0:00"; setTextColor(Color.rgb(145,160,178)); textSize=18f; gravity=Gravity.END }
        timeRow.addView(currentTimeView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        timeRow.addView(durationView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(timeRow)

        seekBar = SeekBar(this).apply {
            max = 1
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if(fromUser) currentTimeView.text=formatTime(p) }
                override fun onStartTrackingTouch(s: SeekBar?) = Unit
                override fun onStopTrackingTouch(s: SeekBar?) { seekToMs(s?.progress ?: 0) }
            })
        }
        root.addView(seekBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)))

        val transport = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER }
        stopButton = Button(this).apply { text="■ STOP"; setOnClickListener { stopPlayback() } }
        playButton = Button(this).apply { text="▶ PLAY"; setOnClickListener { if(playing) pausePlayback() else startPlayback() } }
        loopButton = Button(this).apply {
            text="↻ LOOP"; setOnClickListener { loopEnabled=!loopEnabled; text=if(loopEnabled) "↻ LOOP ✓" else "↻ LOOP" }
        }
        transport.addView(stopButton, LinearLayout.LayoutParams(0,dp(50),1f).apply{marginEnd=dp(4)})
        transport.addView(playButton, LinearLayout.LayoutParams(0,dp(50),1f).apply{marginEnd=dp(4)})
        transport.addView(loopButton, LinearLayout.LayoutParams(0,dp(50),1f))
        root.addView(transport)

        root.addView(Button(this).apply {
            text = "OLVIDAR CANCIÓN GUARDADA"
            setOnClickListener { clearSavedSong() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply { topMargin=dp(8) })

        statusView = TextView(this).apply {
            text="Buscando canción guardada…"; setTextColor(Color.rgb(145,160,178)); textSize=12f; setPadding(0,dp(8),0,0)
        }
        root.addView(statusView)
        root.addView(TextView(this).apply {
            text="Los WAV y ajustes quedan recordados al cerrar la app."; setTextColor(Color.rgb(110,124,142)); textSize=11f
        })

        updateStemButtons()
        return root
    }

    private fun fileLabel(t:String, dp:(Int)->Int)=TextView(this).apply{
        text=t; setTextColor(Color.rgb(190,199,210)); textSize=12f; setPadding(0,dp(3),0,dp(2))
    }

    private fun stemControls(label:String, dp:(Int)->Int, volume:()->Int, setVolume:(Int)->Unit,
        toggleMute:()->Unit, toggleSolo:()->Unit, assign:(Button,Button)->Unit):LinearLayout {
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL}
        val value=TextView(this).apply{text="$label ${volume()}%"; setTextColor(Color.LTGRAY); textSize=11f}
        val slider=SeekBar(this).apply{
            max=100; progress=volume()
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
                override fun onProgressChanged(s:SeekBar?,p:Int,fromUser:Boolean){value.text="$label $p%"; if(fromUser)setVolume(p)}
                override fun onStartTrackingTouch(s:SeekBar?)=Unit
                override fun onStopTrackingTouch(s:SeekBar?)=Unit
            })
        }
        val m=Button(this).apply{text="M";setOnClickListener{toggleMute()}}
        val s=Button(this).apply{text="S";setOnClickListener{toggleSolo()}}
        assign(m,s)
        val left=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;addView(value);addView(slider,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(30)))}
        row.addView(left,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f))
        row.addView(m,LinearLayout.LayoutParams(dp(44),dp(40)).apply{marginStart=dp(4)})
        row.addView(s,LinearLayout.LayoutParams(dp(44),dp(40)).apply{marginStart=dp(4)})
        return row
    }

    private fun updateStemButtons(){
        if(::clickMuteButton.isInitialized)clickMuteButton.text=if(clickMuted)"M ✓" else "M"
        if(::drumsMuteButton.isInitialized)drumsMuteButton.text=if(drumsMuted)"M ✓" else "M"
        if(::bassMuteButton.isInitialized)bassMuteButton.text=if(bassMuted)"M ✓" else "M"
        if(::clickSoloButton.isInitialized)clickSoloButton.text=if(clickSolo)"S ✓" else "S"
        if(::drumsSoloButton.isInitialized)drumsSoloButton.text=if(drumsSolo)"S ✓" else "S"
        if(::bassSoloButton.isInitialized)bassSoloButton.text=if(bassSolo)"S ✓" else "S"
    }

    private fun openPicker(code:Int){
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{
            addCategory(Intent.CATEGORY_OPENABLE); type="audio/wav"
            putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("audio/wav","audio/x-wav","audio/wave"))
        },code)
    }

    @Deprecated("Prototype")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        super.onActivityResult(requestCode,resultCode,data)
        if(resultCode!=RESULT_OK||requestCode !in listOf(PICK_CLICK,PICK_DRUMS,PICK_BASS))return
        val uri=data?.data?:return
        try{contentResolver.takePersistableUriPermission(uri,data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){}
        val name=getDisplayName(uri)?:"Archivo WAV"
        statusView.text="Cargando $name…"
        Thread{
            try{
                val stem=loadPcm16Wav(uri,name)
                runOnUiThread{
                    when(requestCode){
                        PICK_CLICK->{clickStem=stem;clickUri=uri;clickNameView.text="Click: $name"}
                        PICK_DRUMS->{drumsStem=stem;drumsUri=uri;drumsNameView.text="Drums: $name"}
                        PICK_BASS->{bassStem=stem;bassUri=uri;bassNameView.text="Bass: $name"}
                    }
                    saveUris(); validateLoadedStems()
                }
            }catch(e:Exception){runOnUiThread{statusView.text="No se pudo cargar: ${e.message ?: "WAV incompatible"}"}}
        }.start()
    }

    private fun restoreSavedSong(){
        val p=getSharedPreferences(PREFS,MODE_PRIVATE)
        val c=p.getString("click_uri",null); val d=p.getString("drums_uri",null); val b=p.getString("bass_uri",null)
        if(c==null||d==null||b==null){statusView.text="Carga Click + Drums + Bass; se guardarán automáticamente.";return}
        statusView.text="Restaurando canción guardada…"
        Thread{
            try{
                val cu=Uri.parse(c); val du=Uri.parse(d); val bu=Uri.parse(b)
                val cs=loadPcm16Wav(cu,getDisplayName(cu)?:"Click")
                val ds=loadPcm16Wav(du,getDisplayName(du)?:"Drums")
                val bs=loadPcm16Wav(bu,getDisplayName(bu)?:"Bass")
                runOnUiThread{
                    clickUri=cu;drumsUri=du;bassUri=bu;clickStem=cs;drumsStem=ds;bassStem=bs
                    clickNameView.text="Click: ${cs.name}";drumsNameView.text="Drums: ${ds.name}";bassNameView.text="Bass: ${bs.name}"
                    validateLoadedStems(); statusView.text="Canción restaurada. Lista para tocar."
                }
            }catch(e:Exception){runOnUiThread{statusView.text="No pude restaurar los WAV; vuelve a seleccionarlos."}}
        }.start()
    }

    private fun saveUris(){
        getSharedPreferences(PREFS,MODE_PRIVATE).edit()
            .putString("click_uri",clickUri?.toString()).putString("drums_uri",drumsUri?.toString()).putString("bass_uri",bassUri?.toString()).apply()
    }

    private fun saveMixerSettings(){
        getSharedPreferences(PREFS,MODE_PRIVATE).edit()
            .putInt("click_vol",clickVolume).putInt("drums_vol",drumsVolume).putInt("bass_vol",bassVolume)
            .putBoolean("click_mute",clickMuted).putBoolean("drums_mute",drumsMuted).putBoolean("bass_mute",bassMuted)
            .putBoolean("click_solo",clickSolo).putBoolean("drums_solo",drumsSolo).putBoolean("bass_solo",bassSolo).apply()
    }

    private fun restoreMixerSettings(){
        val p=getSharedPreferences(PREFS,MODE_PRIVATE)
        clickVolume=p.getInt("click_vol",100);drumsVolume=p.getInt("drums_vol",100);bassVolume=p.getInt("bass_vol",100)
        clickMuted=p.getBoolean("click_mute",false);drumsMuted=p.getBoolean("drums_mute",false);bassMuted=p.getBoolean("bass_mute",false)
        clickSolo=p.getBoolean("click_solo",false);drumsSolo=p.getBoolean("drums_solo",false);bassSolo=p.getBoolean("bass_solo",false)
    }

    private fun clearSavedSong(){
        stopPlayback();clickStem=null;drumsStem=null;bassStem=null;clickUri=null;drumsUri=null;bassUri=null
        getSharedPreferences(PREFS,MODE_PRIVATE).edit().clear().apply()
        clickNameView.text="Click: ninguno";drumsNameView.text="Drums: ninguno";bassNameView.text="Bass: ninguno"
        durationView.text="0:00";seekBar.max=1;updateControls();statusView.text="Canción olvidada. Puedes cargar otra."
    }

    private fun validateLoadedStems(){
        val stems=listOfNotNull(clickStem,drumsStem,bassStem)
        if(stems.isEmpty()){updateControls();return}
        if(stems.map{it.sampleRate}.toSet().size>1){statusView.text="Los WAV deben usar el mismo sample rate.";updateControls();return}
        sampleRate=stems.first().sampleRate;totalFrames=stems.maxOf{it.samples.size}
        val ms=((totalFrames.toLong()*1000L)/sampleRate).toInt();seekBar.max=ms.coerceAtLeast(1);durationView.text=formatTime(ms)
        if(allLoaded()){currentFrame=0;statusView.text="Canción lista y guardada."}else statusView.text="${stems.size} de 3 WAV cargados."
        updateControls()
    }

    private fun allLoaded()=clickStem!=null&&drumsStem!=null&&bassStem!=null&&setOf(clickStem!!.sampleRate,drumsStem!!.sampleRate,bassStem!!.sampleRate).size==1

    private fun startPlayback(){if(playing||!allLoaded())return;playing=true;playButton.text="❚❚ PAUSA";statusView.text="Reproduciendo.";startAudioEngine()}
    private fun pausePlayback(){playing=false;generation++;releaseTrack();playButton.text="▶ PLAY";statusView.text="Pausado."}
    private fun stopPlayback(){playing=false;generation++;releaseTrack();currentFrame=0;if(::seekBar.isInitialized)seekBar.progress=0;if(::currentTimeView.isInitialized)currentTimeView.text="0:00";if(::playButton.isInitialized)playButton.text="▶ PLAY"}
    private fun seekToMs(ms:Int){val f=((ms.toLong()*sampleRate)/1000L).toInt().coerceIn(0,totalFrames);val was=playing;generation++;releaseTrack();currentFrame=f;if(was)startAudioEngine()}
    private fun releaseTrack(){try{audioTrack?.pause()}catch(_:Exception){};try{audioTrack?.flush()}catch(_:Exception){};try{audioTrack?.release()}catch(_:Exception){};audioTrack=null}

    private fun startAudioEngine(){
        val click=clickStem?:return;val drums=drumsStem?:return;val bass=bassStem?:return;val local=++generation
        val min=AudioTrack.getMinBufferSize(sampleRate,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096)
        val track=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setBufferSizeInBytes(min*2).setTransferMode(AudioTrack.MODE_STREAM).build()
        audioTrack=track;track.play()
        Thread{
            val blockFrames=1024;val out=ShortArray(blockFrames*2)
            while(playing&&local==generation){
                var frame=currentFrame
                if(frame>=totalFrames){if(loopEnabled){currentFrame=0;frame=0}else{playing=false;handler.post{playButton.text="▶ PLAY";statusView.text="Terminó."};break}}
                val frames=minOf(blockFrames,totalFrames-frame);val anySolo=clickSolo||drumsSolo||bassSolo
                val cg=gain(clickVolume,clickMuted,clickSolo,anySolo);val dg=gain(drumsVolume,drumsMuted,drumsSolo,anySolo);val bg=gain(bassVolume,bassMuted,bassSolo,anySolo)
                var p=0
                for(i in 0 until frames){val idx=frame+i;val c=if(idx<click.samples.size)click.samples[idx].toInt()else 0;val d=if(idx<drums.samples.size)drums.samples[idx].toInt()else 0;val b=if(idx<bass.samples.size)bass.samples[idx].toInt()else 0
                    out[p++]=(c*cg).roundToInt().coerceIn(Short.MIN_VALUE.toInt(),Short.MAX_VALUE.toInt()).toShort()
                    out[p++]=((d*dg)+(b*bg)).roundToInt().coerceIn(Short.MIN_VALUE.toInt(),Short.MAX_VALUE.toInt()).toShort()}
                if(track.write(out,0,frames*2,AudioTrack.WRITE_BLOCKING)<0)break;currentFrame+=frames
            }
            try{track.stop()}catch(_:Exception){};try{track.release()}catch(_:Exception){};if(audioTrack===track)audioTrack=null
        }.apply{name="SequencePlayerAudio";priority=Thread.MAX_PRIORITY;start()}
    }

    private fun gain(v:Int,m:Boolean,s:Boolean,anySolo:Boolean):Float{if(m)return 0f;if(anySolo&&!s)return 0f;return v.coerceIn(0,100)/100f}
    private fun updateControls(){if(!::playButton.isInitialized)return;val ready=allLoaded();playButton.isEnabled=ready;stopButton.isEnabled=ready;loopButton.isEnabled=ready;seekBar.isEnabled=ready}

    private fun loadPcm16Wav(uri:Uri,name:String):LoadedStem{
        BufferedInputStream(contentResolver.openInputStream(uri)?:error("No se pudo abrir"),128*1024).use{stream->
            if(readFourCc(stream)!="RIFF")error("No es WAV RIFF");readLeInt(stream);if(readFourCc(stream)!="WAVE")error("WAV no válido")
            var format=-1;var channels=-1;var rate=-1;var bits=-1
            while(true){val id=readFourCc(stream);val size=readLeInt(stream);when(id){
                "fmt "->{if(size<16)error("fmt inválido");format=readLeShort(stream);channels=readLeShort(stream);rate=readLeInt(stream);readLeInt(stream);readLeShort(stream);bits=readLeShort(stream);skipFully(stream,size-16);if((size and 1)==1)skipFully(stream,1)}
                "data"->{if(format!=1)error("Solo PCM");if(bits!=16)error("Solo 16 bits");if(channels<1||rate<=0)error("WAV inválido");val bpf=channels*2;val count=size/bpf;val samples=ShortArray(count);val buf=ByteArray(bpf)
                    for(frame in 0 until count){readFully(stream,buf,0,bpf);var sum=0;var off=0;repeat(channels){val lo=buf[off].toInt()and 0xff;val hi=buf[off+1].toInt();sum+=((hi shl 8)or lo).toShort().toInt();off+=2};samples[frame]=(sum/channels).coerceIn(Short.MIN_VALUE.toInt(),Short.MAX_VALUE.toInt()).toShort()};return LoadedStem(name,rate,samples)}
                else->{skipFully(stream,size);if((size and 1)==1)skipFully(stream,1)}
            }}
        }
    }

    private fun readFourCc(i:InputStream):String{val b=ByteArray(4);readFully(i,b,0,4);return String(b,Charsets.US_ASCII)}
    private fun readLeInt(i:InputStream):Int{val a=i.read();val b=i.read();val c=i.read();val d=i.read();if(a<0||b<0||c<0||d<0)throw EOFException();return a or(b shl 8)or(c shl 16)or(d shl 24)}
    private fun readLeShort(i:InputStream):Int{val a=i.read();val b=i.read();if(a<0||b<0)throw EOFException();return a or(b shl 8)}
    private fun skipFully(i:InputStream,n:Int){var r=n;while(r>0){val s=i.skip(r.toLong()).toInt();if(s>0)r-=s else{if(i.read()<0)throw EOFException();r--}}}
    private fun readFully(i:InputStream,b:ByteArray,o:Int,n:Int){var t=0;while(t<n){val r=i.read(b,o+t,n-t);if(r<0)throw EOFException();t+=r}}
    private fun getDisplayName(uri:Uri):String?{contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{c->val x=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(x>=0&&c.moveToFirst())return c.getString(x)};return uri.lastPathSegment}
    private fun formatTime(ms:Int):String{val s=(ms/1000).coerceAtLeast(0);return "%d:%02d".format(s/60,s%60)}

    override fun onDestroy(){playing=false;generation++;handler.removeCallbacks(progressUpdater);releaseTrack();super.onDestroy()}
}
