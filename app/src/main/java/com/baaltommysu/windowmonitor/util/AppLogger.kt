package com.baaltommysu.windowmonitor.util

import android.util.Log

object AppLogger {
    private const val RootTag = "WindowMonitor"

    fun d(tag: String, message: String) {
        Log.d("$RootTag:$tag", message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e("$RootTag:$tag", message, throwable)
    }
}
