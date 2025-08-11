package com.clipboardsync.app.service.clipboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    private val tag = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(tag, "Boot receiver triggered: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                try {
                    Log.d(tag, "Starting clipboard sync service on boot")
                    ClipboardSyncService.startService(context)
                } catch (e: Exception) {
                    Log.e(tag, "Error starting service on boot", e)
                }
            }
        }
    }
}
