package com.neutrine.knostr.adapters.ws

import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.tracing.annotation.NewSpan
import io.micronaut.websocket.WebSocketSession
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Singleton
class MessageSender(
    private val coroutineScope: CoroutineScope,
    private val meterRegistry: MeterRegistry
) {
    @NewSpan("send-event")
    fun send(message: String, session: WebSocketSession): Job = coroutineScope.launch {
        if (session.isOpen) {
            session.sendSync(message)
            meterRegistry.counter(EVENT_SEND_METRICS).increment()
        }
    }

    companion object {
        const val EVENT_SEND_METRICS = "event.send"
    }
}
