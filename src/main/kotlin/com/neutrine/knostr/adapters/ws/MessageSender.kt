package com.neutrine.knostr.adapters.ws

import io.micronaut.websocket.WebSocketSession
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Singleton
class MessageSender(
    private val coroutineScope: CoroutineScope
) {
    fun send(message: String, session: WebSocketSession): Job = coroutineScope.launch {
        if (session.isOpen) {
            session.sendSync(message)
        }
    }
}
