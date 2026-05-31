package com.raintee.rainnote.sync

import com.raintee.rainnote.data.Note
import com.raintee.rainnote.data.NoteBlock

data class PairedDevice(
    val deviceId: String,
    val deviceName: String,
    val token: String,
    val pairedAt: Long
)

data class SyncPayload(
    val deviceId: String,
    val notes: List<Note>,
    val blocks: List<NoteBlock>,
    val deletedIds: List<String>,
    val timestamp: Long
)
