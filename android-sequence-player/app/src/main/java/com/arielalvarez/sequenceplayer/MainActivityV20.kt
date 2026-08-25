package com.arielalvarez.sequenceplayer

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivityV20 : MainActivityV19() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mergeEditorIntoSingleScroll()
    }

    private fun mergeEditorIntoSingleScroll() {
        val contentRoot = findViewById<ViewGroup>(android.R.id.content)
        if (contentRoot.childCount == 0) return

        val outer = contentRoot.getChildAt(0) as? LinearLayout ?: return
        val playerScroll = findVerticalScroll(outer) ?: return
        val scrollContent = playerScroll.getChildAt(0) as? LinearLayout ?: return

        // V16 dejó los marcadores como un panel fijo debajo del reproductor.
        val sectionWrapper = playerScroll.parent as? ViewGroup ?: return
        val sectionPanels = mutableListOf<View>()
        for (i in 0 until sectionWrapper.childCount) {
            val child = sectionWrapper.getChildAt(i)
            if (child !== playerScroll) sectionPanels.add(child)
        }

        // V17/V18/V19 añadieron metrónomo, compás, guía y acciones como
        // hermanos fijos del reproductor. Los movemos al mismo ScrollView.
        val playerBranch = findDirectBranchContaining(outer, playerScroll) ?: return
        val outerPanels = mutableListOf<View>()
        for (i in 0 until outer.childCount) {
            val child = outer.getChildAt(i)
            if (child !== playerBranch) outerPanels.add(child)
        }

        val header = outerPanels.firstOrNull { containsText(it, "SecuenLive") }
        val remainingOuterPanels = outerPanels.filter { it !== header }

        // Desconectar vistas de sus padres antes de reubicarlas.
        sectionPanels.forEach { (it.parent as? ViewGroup)?.removeView(it) }
        outerPanels.forEach { (it.parent as? ViewGroup)?.removeView(it) }
        (playerScroll.parent as? ViewGroup)?.removeView(playerScroll)
        contentRoot.removeView(outer)

        // El encabezado queda arriba y todo lo demás continúa debajo del
        // reproductor: stems, transporte, marcadores, metrónomo, compás,
        // guía y modo en vivo. Ahora TODO se desplaza junto.
        if (header != null) {
            scrollContent.addView(header, 0)
        }
        sectionPanels.forEach { scrollContent.addView(it) }
        remainingOuterPanels.forEach { scrollContent.addView(it) }

        scrollContent.setBackgroundColor(Color.rgb(8, 11, 14))
        playerScroll.setBackgroundColor(Color.rgb(8, 11, 14))
        playerScroll.isFillViewport = false

        contentRoot.addView(
            playerScroll,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun findVerticalScroll(view: View): ScrollView? {
        if (view is ScrollView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findVerticalScroll(view.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    private fun findDirectBranchContaining(parent: ViewGroup, target: View): View? {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child === target || containsView(child, target)) return child
        }
        return null
    }

    private fun containsView(root: View, target: View): Boolean {
        if (root === target) return true
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                if (containsView(root.getChildAt(i), target)) return true
            }
        }
        return false
    }

    private fun containsText(root: View, needle: String): Boolean {
        if (root is TextView && root.text?.toString()?.contains(needle, ignoreCase = true) == true) {
            return true
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                if (containsText(root.getChildAt(i), needle)) return true
            }
        }
        return false
    }
}
