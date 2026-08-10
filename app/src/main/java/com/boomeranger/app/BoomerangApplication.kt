package com.boomeranger.app

import android.app.Application
import com.boomeranger.app.util.AppLogger

class BoomerangApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.i("Boomeranger started")
    }
}
