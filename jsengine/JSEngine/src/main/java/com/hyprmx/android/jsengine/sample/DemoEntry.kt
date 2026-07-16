package com.hyprmx.android.jsengine.sample

import android.app.Activity

/** One row in [DemoListActivity]: a title, a subtitle, and the Activity it launches. */
data class DemoEntry(
    val titleRes: Int,
    val subtitleRes: Int,
    val target: Class<out Activity>,
)
