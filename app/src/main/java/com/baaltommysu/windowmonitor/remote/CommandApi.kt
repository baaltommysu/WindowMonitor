package com.baaltommysu.windowmonitor.remote

import android.content.Context

data class RemoteCommand(
    val takePhotoNow: Boolean = false,
    val intervalMinutes: Long? = null
)

class CommandApi(private val context: Context) {
    fun fetchCommand(): RemoteCommand {
        // Placeholder for a server polling implementation:
        // GET /device/{id}/command -> {"take_photo_now":true,"interval_minutes":30}
        return RemoteCommand()
    }
}
