package com.clipboardsync.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ClipboardSyncApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
    }
}
