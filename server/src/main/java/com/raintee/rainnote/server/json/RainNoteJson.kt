package com.raintee.rainnote.server.json

import com.raintee.rainnote.server.model.ApiNote
import com.raintee.rainnote.server.model.ApiNoteBlock
import com.raintee.rainnote.server.model.ApiNoteCard
import com.raintee.rainnote.server.model.ApiNoteDetail
import org.json.JSONArray
import org.json.JSONObject

object RainNoteJson {
    fun ok(): String = JSONObject().put("ok", true).toString()

    fun error(code: String, message: String): String = JSONObject()
        .put("error", code)
        .put("message", message)
        .toString()

    fun notes(notes: List<ApiNote>): String = JSONObject()
        .put("notes", JSONArray(notes.map { it.toJson() }))
        .toString()

    fun detail(detail: ApiNoteDetail): String = JSONObject()
        .put("note", detail.note.toJson())
        .put("cards", JSONArray(detail.cards.map { it.toJson(includeBlocks = true) }))
        .toString()

    fun note(note: ApiNote): String = JSONObject().put("note", note.toJson()).toString()

    fun card(card: ApiNoteCard): String = JSONObject().put("card", card.toJson(includeBlocks = true)).toString()

    fun block(block: ApiNoteBlock): String = JSONObject().put("block", block.toJson()).toString()

    fun parseObject(body: String): JSONObject = if (body.isBlank()) JSONObject() else JSONObject(body)

    fun parseCards(body: String): List<ApiNoteCard> {
        val cardsJson = parseObject(body).optJSONArray("cards") ?: JSONArray()
        return buildList {
            for (index in 0 until cardsJson.length()) {
                add(cardsJson.getJSONObject(index).toCard(index))
            }
        }
    }

    fun JSONObject.string(name: String, default: String = ""): String = optString(name).ifBlank { default }

    fun JSONObject.stringOrNull(name: String): String? = if (has(name) && !isNull(name)) optString(name) else null

    fun JSONObject.stringList(name: String): List<String> {
        val array = optJSONArray(name) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) add(array.optString(index))
        }
    }

    private fun ApiNote.toJson() = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)
        .put("deletedAt", deletedAt)
        .put("version", version)

    private fun ApiNoteCard.toJson(includeBlocks: Boolean) = JSONObject()
        .put("id", id)
        .put("noteId", noteId)
        .put("title", title)
        .put("sortOrder", sortOrder)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)
        .put("deletedAt", deletedAt)
        .also { json ->
            if (includeBlocks) json.put("blocks", JSONArray(blocks.map { it.toJson() }))
        }

    private fun ApiNoteBlock.toJson() = JSONObject()
        .put("id", id)
        .put("noteId", noteId)
        .put("cardId", cardId)
        .put("type", type)
        .put("content", content)
        .put("sortOrder", sortOrder)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)
        .put("deletedAt", deletedAt)

    private fun JSONObject.toCard(defaultOrder: Int): ApiNoteCard {
        val blocksJson = optJSONArray("blocks") ?: JSONArray()
        val blocks = buildList {
            for (index in 0 until blocksJson.length()) {
                add(blocksJson.getJSONObject(index).toBlock(index))
            }
        }
        return ApiNoteCard(
            id = optString("id"),
            noteId = optString("noteId"),
            title = optString("title"),
            sortOrder = optInt("sortOrder", defaultOrder),
            createdAt = optLong("createdAt", 0L),
            updatedAt = optLong("updatedAt", 0L),
            deletedAt = optNullableLong("deletedAt"),
            blocks = blocks
        )
    }

    private fun JSONObject.toBlock(defaultOrder: Int): ApiNoteBlock = ApiNoteBlock(
        id = optString("id"),
        noteId = optString("noteId"),
        cardId = optString("cardId"),
        type = optString("type", "plain_text"),
        content = optString("content"),
        sortOrder = optInt("sortOrder", defaultOrder),
        createdAt = optLong("createdAt", 0L),
        updatedAt = optLong("updatedAt", 0L),
        deletedAt = optNullableLong("deletedAt")
    )

    private fun JSONObject.optNullableLong(name: String): Long? = if (has(name) && !isNull(name)) optLong(name) else null
}
