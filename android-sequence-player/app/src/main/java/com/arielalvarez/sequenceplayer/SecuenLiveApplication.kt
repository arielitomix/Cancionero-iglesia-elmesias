package com.arielalvarez.sequenceplayer

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button

class SecuenLiveApplication : Application(), Application.ActivityLifecycleCallbacks {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity !is MainActivityV19) return
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        styleTree(activity, root)
        root.postDelayed({ styleTree(activity, root) }, 250L)
        root.postDelayed({ styleTree(activity, root) }, 900L)
    }

    private fun styleTree(activity: Activity, view: View) {
        if (view is Button && !isSpecialButton(view)) {
            val density = activity.resources.displayMetrics.density
            val drawable = GradientDrawable().apply {
                setColor(Color.rgb(36, 36, 40))
                cornerRadius = 10f * density
                setStroke((1f * density).toInt().coerceAtLeast(1), Color.rgb(54, 58, 62))
            }
            view.backgroundTintList = null
            view.stateListAnimator = null
            view.setTextColor(Color.WHITE)
            view.setAllCaps(false)
            view.background = drawable
            view.setPadding((10f * density).toInt(), 0, (10f * density).toInt(), 0)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) styleTree(activity, view.getChildAt(i))
        }
    }

    private fun isSpecialButton(button: Button): Boolean {
        val text = button.text?.toString().orEmpty()
        return text.contains("GUÍA AUTO") ||
            text.contains("ABRIR MODO EN VIVO") ||
            text == "BORRAR CANCIÓN"
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
