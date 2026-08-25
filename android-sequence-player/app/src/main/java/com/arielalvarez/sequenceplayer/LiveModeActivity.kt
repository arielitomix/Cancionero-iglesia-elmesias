package com.arielalvarez.sequenceplayer

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class LiveModeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d=resources.displayMetrics.density
        fun dp(v:Int)=(v*d).toInt()
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(22),dp(18),dp(22));setBackgroundColor(Color.rgb(8,12,17));gravity=Gravity.CENTER_HORIZONTAL}
        root.addView(TextView(this).apply{text="MODO EN VIVO";setTextColor(Color.rgb(145,160,178));textSize=13f;gravity=Gravity.CENTER},LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(32)))
        root.addView(TextView(this).apply{text=intent.getStringExtra("song")?.ifBlank{"SEQUENCE PLAYER"}?:"SEQUENCE PLAYER";setTextColor(Color.WHITE);textSize=28f;gravity=Gravity.CENTER},LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f))
        root.addView(TextView(this).apply{text="La pantalla de interpretación ya está creada.\nAhora conectaremos aquí las secciones y el transporte sin tocar el motor de audio.";setTextColor(Color.LTGRAY);textSize=16f;gravity=Gravity.CENTER},LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f))
        root.addView(Button(this).apply{text="← VOLVER A EDICIÓN";setOnClickListener{finish()}},LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(56)))
        setContentView(root)
    }
}
