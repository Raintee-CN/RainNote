package com.raintee.rainnote.server.http

import com.raintee.rainnote.server.json.RainNoteJson
import com.raintee.rainnote.server.json.RainNoteJson.string
import com.raintee.rainnote.server.json.RainNoteJson.stringList
import com.raintee.rainnote.server.json.RainNoteJson.stringOrNull
import com.raintee.rainnote.server.service.RainNoteService
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.options
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing

class RainNoteKtorServer(
    private val service: RainNoteService,
    private val port: Int = DEFAULT_PORT,
    private val host: String = "0.0.0.0",
    private val tokenProvider: () -> String? = { null }
) {
    private var engine: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    val isStarted: Boolean get() = engine != null

    fun start() {
        if (engine != null) return
        engine = embeddedServer(CIO, host = host, port = port) {
            routing {
                options("/{...}") { call.respondCors(HttpStatusCode.OK, "") }

                get("/api/health") {
                    call.respondJson(
                        "{\"ok\":true,\"app\":\"RainNote\",\"server\":\"ktor\",\"port\":$port}"
                    )
                }

                get("/api/notes") {
                    if (!call.authorized()) return@get
                    call.respondJson(RainNoteJson.notes(service.listNotes()))
                }

                post("/api/notes") {
                    if (!call.authorized()) return@post
                    val body = RainNoteJson.parseObject(call.receiveText())
                    call.respondJson(RainNoteJson.note(service.createNote(body.string("title", "未命名便签"))))
                }

                get("/api/notes/{noteId}") {
                    if (!call.authorized()) return@get
                    val noteId = call.parameters["noteId"].orEmpty()
                    val detail = service.getNote(noteId)
                    if (detail == null) call.respondNotFound() else call.respondJson(RainNoteJson.detail(detail))
                }

                put("/api/notes/{noteId}") {
                    if (!call.authorized()) return@put
                    val noteId = call.parameters["noteId"].orEmpty()
                    val body = RainNoteJson.parseObject(call.receiveText())
                    val note = service.updateNote(noteId, body.string("title", "未命名便签"))
                    if (note == null) call.respondNotFound() else call.respondJson(RainNoteJson.note(note))
                }

                delete("/api/notes/{noteId}") {
                    if (!call.authorized()) return@delete
                    if (service.deleteNote(call.parameters["noteId"].orEmpty())) call.respondJson(RainNoteJson.ok()) else call.respondNotFound()
                }

                put("/api/notes/{noteId}/content") {
                    if (!call.authorized()) return@put
                    val detail = service.replaceNoteContent(
                        noteId = call.parameters["noteId"].orEmpty(),
                        cards = RainNoteJson.parseCards(call.receiveText())
                    )
                    if (detail == null) call.respondNotFound() else call.respondJson(RainNoteJson.detail(detail))
                }

                post("/api/notes/{noteId}/cards") {
                    if (!call.authorized()) return@post
                    val body = RainNoteJson.parseObject(call.receiveText())
                    val card = service.createCard(call.parameters["noteId"].orEmpty(), body.string("title", "未命名卡片"))
                    if (card == null) call.respondNotFound() else call.respondJson(RainNoteJson.card(card))
                }

                put("/api/notes/{noteId}/cards/order") {
                    if (!call.authorized()) return@put
                    val ids = RainNoteJson.parseObject(call.receiveText()).stringList("orderedIds")
                    if (service.reorderCards(call.parameters["noteId"].orEmpty(), ids)) call.respondJson(RainNoteJson.ok()) else call.respondNotFound()
                }

                put("/api/cards/{cardId}") {
                    if (!call.authorized()) return@put
                    val body = RainNoteJson.parseObject(call.receiveText())
                    val card = service.updateCard(call.parameters["cardId"].orEmpty(), body.string("title", "未命名卡片"))
                    if (card == null) call.respondNotFound() else call.respondJson(RainNoteJson.card(card))
                }

                delete("/api/cards/{cardId}") {
                    if (!call.authorized()) return@delete
                    if (service.deleteCard(call.parameters["cardId"].orEmpty())) call.respondJson(RainNoteJson.ok()) else call.respondNotFound()
                }

                post("/api/cards/{cardId}/blocks") {
                    if (!call.authorized()) return@post
                    val body = RainNoteJson.parseObject(call.receiveText())
                    val block = service.createBlock(
                        cardId = call.parameters["cardId"].orEmpty(),
                        type = body.string("type", "plain_text"),
                        content = body.string("content")
                    )
                    if (block == null) call.respondNotFound() else call.respondJson(RainNoteJson.block(block))
                }

                put("/api/cards/{cardId}/blocks/order") {
                    if (!call.authorized()) return@put
                    val ids = RainNoteJson.parseObject(call.receiveText()).stringList("orderedIds")
                    if (service.reorderBlocks(call.parameters["cardId"].orEmpty(), ids)) call.respondJson(RainNoteJson.ok()) else call.respondNotFound()
                }

                put("/api/blocks/{blockId}") {
                    if (!call.authorized()) return@put
                    val body = RainNoteJson.parseObject(call.receiveText())
                    val block = service.updateBlock(
                        blockId = call.parameters["blockId"].orEmpty(),
                        type = body.stringOrNull("type"),
                        content = body.stringOrNull("content")
                    )
                    if (block == null) call.respondNotFound() else call.respondJson(RainNoteJson.block(block))
                }

                delete("/api/blocks/{blockId}") {
                    if (!call.authorized()) return@delete
                    if (service.deleteBlock(call.parameters["blockId"].orEmpty())) call.respondJson(RainNoteJson.ok()) else call.respondNotFound()
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        engine?.stop(gracePeriodMillis = 500, timeoutMillis = 1500)
        engine = null
    }

    private suspend fun ApplicationCall.authorized(): Boolean {
        val token = tokenProvider().orEmpty()
        if (token.isBlank()) return true
        val requestToken = request.headers["X-RainNote-Token"].orEmpty()
        if (requestToken == token) return true
        respondCors(HttpStatusCode.Unauthorized, RainNoteJson.error("unauthorized", "Invalid token"))
        return false
    }

    companion object {
        const val DEFAULT_PORT = 48622
    }
}

private suspend fun ApplicationCall.respondJson(body: String, status: HttpStatusCode = HttpStatusCode.OK) {
    respondCors(status, body)
}

private suspend fun ApplicationCall.respondNotFound() {
    respondCors(HttpStatusCode.NotFound, RainNoteJson.error("not_found", "Resource not found"))
}

private suspend fun ApplicationCall.respondCors(status: HttpStatusCode, body: String) {
    response.headers.append(HttpHeaders.AccessControlAllowOrigin, "*")
    response.headers.append(HttpHeaders.AccessControlAllowMethods, "GET,POST,PUT,DELETE,OPTIONS")
    response.headers.append(HttpHeaders.AccessControlAllowHeaders, "Content-Type,X-RainNote-Token")
    respondText(body, ContentType.Application.Json, status)
}
