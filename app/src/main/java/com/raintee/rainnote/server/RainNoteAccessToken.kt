package com.raintee.rainnote.server

import android.content.Context
import kotlin.random.Random

object RainNoteAccessToken {
    private const val PREFS_NAME = "rainnote_server"
    private const val KEY_TOKEN = "access_token"

    fun get(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_TOKEN, null)
        if (!existing.isNullOrBlank()) return existing
        return reset(context)
    }

    fun reset(context: Context): String {
        val token = Random.nextInt(100000, 1000000).toString()
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, token)
            .apply()
        return token
    }
}
