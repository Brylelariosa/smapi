package com.universalsmapi.launcher.platform

import android.content.Context
import android.util.Log
import java.io.File
import java.util.Date

/**
 * Keeps the last uncaught Java exception on disk so the next report can include it. Experiments that run game code
 * inside this process may crash it; this is how that evidence survives. Native crashes are not captured.
 */
object CrashLog {
    private const val FILE = "last-crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                File(app.filesDir, FILE).writeText("thread=${thread.name}\n${Log.getStackTraceString(error)}")
            } catch (e: Throwable) {
                // nothing sensible can be done while crashing
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Text of the previous crash with its timestamp, or null. */
    fun read(context: Context): String? {
        val f = File(context.filesDir, FILE)
        if (!f.exists()) return null
        return "modified ${Date(f.lastModified())}\n${f.readText()}"
    }
}
