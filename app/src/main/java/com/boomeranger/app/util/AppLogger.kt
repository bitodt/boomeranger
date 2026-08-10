package com.boomeranger.app.util

import android.util.Log

object AppLogger {
    private const val TAG = "Boomeranger"

    fun d(message: String) = Log.d(TAG, message)
    fun i(message: String) = Log.i(TAG, message)
    fun w(message: String, throwable: Throwable? = null) = Log.w(TAG, message, throwable)
    fun e(message: String, throwable: Throwable? = null) = Log.e(TAG, message, throwable)
}
