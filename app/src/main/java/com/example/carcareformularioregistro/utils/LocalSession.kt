package com.example.carcareformularioregistro.utils

import android.content.Context

/** Remembers whether this device's local profile was explicitly closed. This is not authentication. */
object LocalSession {
    private const val PREFERENCES = "carcare_local_session"
    private const val CLOSED = "profile_closed"

    fun isClosed(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(CLOSED, false)

    fun resume(context: Context) = setClosed(context, false)

    fun close(context: Context) = setClosed(context, true)

    private fun setClosed(context: Context, closed: Boolean) {
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putBoolean(CLOSED, closed).apply()
    }
}
