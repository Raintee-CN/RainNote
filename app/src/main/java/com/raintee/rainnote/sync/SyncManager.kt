package com.raintee.rainnote.sync

import android.content.Context
import com.raintee.rainnote.data.NoteRepository

class SyncManager(context: Context) {

    private val repository = NoteRepository(context.applicationContext)
    private val pairingManager = NfcPairingManager(context.applicationContext)

    fun buildLocalPayload(): SyncPayload {
        val notes = repository.getNotes()
        return SyncPayload(
            deviceId = pairingManager.localHandshake().deviceId,
            notes = notes,
            blocks = notes.flatMap { repository.getBlocks(it.id) },
            deletedIds = emptyList(),
            timestamp = System.currentTimeMillis()
        )
    }

    fun statusText(): String = pairingManager.statusText()
}
