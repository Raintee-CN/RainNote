package com.raintee.rainnote.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class NoteDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE notes (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                deleted_at INTEGER,
                version INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE note_blocks (
                id TEXT PRIMARY KEY,
                note_id TEXT NOT NULL,
                type TEXT NOT NULL,
                content TEXT NOT NULL,
                sort_order INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                deleted_at INTEGER,
                FOREIGN KEY(note_id) REFERENCES notes(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX index_note_blocks_note_order ON note_blocks(note_id, sort_order)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS note_blocks")
        db.execSQL("DROP TABLE IF EXISTS notes")
        onCreate(db)
    }

    fun getNotes(): List<Note> = readableDatabase.query(
        "notes",
        null,
        "deleted_at IS NULL",
        null,
        null,
        null,
        "updated_at DESC"
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    Note(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                        createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                        updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
                        deletedAt = cursor.getNullableLong("deleted_at"),
                        version = cursor.getLong(cursor.getColumnIndexOrThrow("version"))
                    )
                )
            }
        }
    }

    fun getBlocks(noteId: String): List<NoteBlock> = readableDatabase.query(
        "note_blocks",
        null,
        "note_id = ? AND deleted_at IS NULL",
        arrayOf(noteId),
        null,
        null,
        "sort_order ASC"
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    NoteBlock(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        noteId = cursor.getString(cursor.getColumnIndexOrThrow("note_id")),
                        type = BlockType.fromStorageName(cursor.getString(cursor.getColumnIndexOrThrow("type"))),
                        content = cursor.getString(cursor.getColumnIndexOrThrow("content")),
                        sortOrder = cursor.getInt(cursor.getColumnIndexOrThrow("sort_order")),
                        createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                        updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
                        deletedAt = cursor.getNullableLong("deleted_at")
                    )
                )
            }
        }
    }

    fun upsertNote(note: Note) {
        writableDatabase.insertWithOnConflict("notes", null, note.toValues(), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun upsertBlock(block: NoteBlock) {
        writableDatabase.insertWithOnConflict("note_blocks", null, block.toValues(), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun softDeleteNote(noteId: String, deletedAt: Long) {
        writableDatabase.update("notes", ContentValues().apply { put("deleted_at", deletedAt) }, "id = ?", arrayOf(noteId))
        writableDatabase.update("note_blocks", ContentValues().apply { put("deleted_at", deletedAt) }, "note_id = ?", arrayOf(noteId))
    }

    fun softDeleteBlock(blockId: String, deletedAt: Long) {
        writableDatabase.update("note_blocks", ContentValues().apply { put("deleted_at", deletedAt) }, "id = ?", arrayOf(blockId))
    }

    private fun Note.toValues() = ContentValues().apply {
        put("id", id)
        put("title", title)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
        put("deleted_at", deletedAt)
        put("version", version)
    }

    private fun NoteBlock.toValues() = ContentValues().apply {
        put("id", id)
        put("note_id", noteId)
        put("type", type.storageName)
        put("content", content)
        put("sort_order", sortOrder)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
        put("deleted_at", deletedAt)
    }

    private fun android.database.Cursor.getNullableLong(column: String): Long? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getLong(index)
    }

    companion object {
        private const val DATABASE_NAME = "rainnote.db"
        private const val DATABASE_VERSION = 1
    }
}
