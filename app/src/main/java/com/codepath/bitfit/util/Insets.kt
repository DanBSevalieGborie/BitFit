package com.codepath.bitfit.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Pads this view by the system bar / display cutout insets so content never hides under
 * the status bar or navigation bar (Android 15+ draws apps edge-to-edge by default).
 */
fun View.padForSystemBars(top: Boolean = false, bottom: Boolean = false, sides: Boolean = true, ime: Boolean = false) {
    val start = paddingLeft
    val end = paddingRight
    val initialTop = paddingTop
    val initialBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        var types = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        if (ime) types = types or WindowInsetsCompat.Type.ime()
        val bars = insets.getInsets(types)
        v.updatePadding(
            left = start + if (sides) bars.left else 0,
            right = end + if (sides) bars.right else 0,
            top = initialTop + if (top) bars.top else 0,
            bottom = initialBottom + if (bottom) bars.bottom else 0,
        )
        insets
    }
}
