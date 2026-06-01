package com.baaltommysu.windowmonitor.remote

import android.content.Context
import com.baaltommysu.windowmonitor.worker.WorkScheduler

class FcmService {
    fun handleMessage(context: Context, action: String?) {
        if (action == "take_photo_now") {
            WorkScheduler.captureNow(context)
        }
    }
}
