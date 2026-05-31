package com.raintee.rainnote.sync

import com.raintee.rainnote.data.Note
import com.raintee.rainnote.data.NoteBlock
import com.raintee.rainnote.data.NoteCard

data class PairedDevice(
    val deviceId: String,
    val deviceName: String,
    val token: String,
    val pairedAt: Long
)

data class SyncPayload(
    val deviceId: String,
    val notes: List<Note>,
    val cards: List<NoteCard>,
    val blocks: List<NoteBlock>,
    val deletedIds: List<String>,
    val timestamp: Long
)

data class PendingSyncNote(
    val id: String,
    val title: String,
    val cardCount: Int,
    val blockCount: Int,
    val charCount: Int
)
