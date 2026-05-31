package com.raintee.rainnote.sync

import android.content.Context
import com.raintee.rainnote.data.NoteRepository

class SyncManager(context: Context) {

    private val repository = NoteRepository(context.applicationContext)
    private val pairingManager = NfcPairingManager(context.applicationContext)
    private val bluetoothSyncManager = BluetoothSyncManager(context.applicationContext)

    fun buildLocalPayload(): SyncPayload {
        val notes = repository.getNotes()
        val cards = notes.flatMap { repository.getCards(it.id) }
        return SyncPayload(
            deviceId = pairingManager.localHandshake().deviceId,
            notes = notes,
            cards = cards,
            blocks = cards.flatMap { repository.getBlocks(it.id) },
            deletedIds = emptyList(),
            timestamp = System.currentTimeMillis()
        )
    }

    fun statusText(): String = pairingManager.statusText()

    fun bluetoothStatusText(): String = bluetoothSyncManager.statusText()
}
