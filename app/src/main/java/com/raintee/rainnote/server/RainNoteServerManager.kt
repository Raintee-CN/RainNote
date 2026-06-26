package com.raintee.rainnote.server

import android.content.Context
import com.raintee.rainnote.debug.AppLog
import com.raintee.rainnote.server.http.RainNoteKtorServer
import com.raintee.rainnote.server.service.RainNoteTools

object RainNoteServerManager {
    const val PORT = RainNoteKtorServer.DEFAULT_PORT

    @Volatile private var server: RainNoteKtorServer? = null
    @Volatile private var toolsInstance: RainNoteTools? = null

    fun start(context: Context) {
        if (server != null) return
        val service = RainNoteRepositoryService(context.applicationContext)
        toolsInstance = RainNoteTools(service)
        val appContext = context.applicationContext
        server = RainNoteKtorServer(
            service = service,
            tokenProvider = { RainNoteAccessToken.get(appContext) },
            webAssetProvider = { path ->
                runCatching { appContext.assets.open("web/$path").use { it.readBytes() } }.getOrNull()
            }
        ).also { it.start() }
        AppLog.d("RainNoteServer", "listening on http://0.0.0.0:$PORT")
    }

    fun stop() {
        server?.stop()
        server = null
        toolsInstance = null
    }

    fun tools(context: Context): RainNoteTools {
        val existing = toolsInstance
        if (existing != null) return existing
        return RainNoteTools(RainNoteRepositoryService(context.applicationContext)).also { toolsInstance = it }
    }
}
