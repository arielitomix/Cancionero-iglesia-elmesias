package com.arielalvarez.sequenceplayer

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

open class MainActivityV18 : MainActivityV17() {
    private var signature = "4/4"
    private lateinit var sig44: Button
    private lateinit var sig34: Button
    private lateinit var sig68: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSignaturePanel()
        currentSongKey().takeIf { it.isNotBlank() }?.let { loadSignature(it) }
    }

    private fun installSignaturePanel() {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val contentRoot = findViewById<ViewGroup>(android.R.id.content)
        if (contentRoot.childCount == 0) return
        val root = contentRoot.getChildAt(0) as? LinearLayout ?: return
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12),dp(5),dp(12),dp(7)); setBackgroundColor(Color.rgb(20,27,35)) }
        panel.addView(TextView(this).apply { text="0.18 · COMPÁS POR CANCIÓN"; setTextColor(Color.rgb(145,160,178)); textSize=11f })
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        sig44=signatureButton("4/4");sig34=signatureButton("3/4");sig68=signatureButton("6/8")
        row.addView(sig44,LinearLayout.LayoutParams(0,dp(42),1f).apply{marginEnd=dp(4)});row.addView(sig34,LinearLayout.LayoutParams(0,dp(42),1f).apply{marginEnd=dp(4)});row.addView(sig68,LinearLayout.LayoutParams(0,dp(42),1f));panel.addView(row)
        root.addView(panel,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(70)));updateSignatureButtons()
    }
    private fun signatureButton(value:String)=Button(this).apply{text=value;setOnClickListener{signature=value;updateSignatureButtons();currentSongKey().takeIf{it.isNotBlank()}?.let{saveSignature(it)}}}
    private fun updateSignatureButtons(){if(!::sig44.isInitialized)return;sig44.text=if(signature=="4/4")"✓ 4/4" else "4/4";sig34.text=if(signature=="3/4")"✓ 3/4" else "3/4";sig68.text=if(signature=="6/8")"✓ 6/8" else "6/8"}
    override fun beatsPerBar():Int=when(signature){"3/4"->3;"6/8"->6;else->4}
    override fun beatUnitFactor():Double=if(signature=="6/8")0.5 else 1.0
    protected fun currentSignature():String=signature
    override fun onSongKeyChanged(key:String){loadSignature(key)}
    override fun onNewSongCreated(){signature="4/4";updateSignatureButtons()}
    private fun saveSignature(key:String){getSharedPreferences("sequence_player_signature_v18",MODE_PRIVATE).edit().putString("signature_$key",signature).apply()}
    private fun loadSignature(key:String){signature=getSharedPreferences("sequence_player_signature_v18",MODE_PRIVATE).getString("signature_$key","4/4")?:"4/4";if(signature !in setOf("4/4","3/4","6/8"))signature="4/4";updateSignatureButtons()}
}
