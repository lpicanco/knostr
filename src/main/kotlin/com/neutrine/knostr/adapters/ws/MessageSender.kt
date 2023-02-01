package com.neutrine.knostr.adapters.ws

import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.tracing.annotation.NewSpan
import io.micronaut.websocket.WebSocketSession
import jakarta.annotation.PreDestroy
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.time.withTimeout
import mu.KotlinLogging
import java.time.Duration

@Singleton
class MessageSender(
    private val coroutineScope: CoroutineScope,
    private val meterRegistry: MeterRegistry
) : AutoCloseable {
    private val channel = Channel<ChannelMessage>(10_000)
    private val logger = KotlinLogging.logger {}

    init {
        repeat(MESSAGE_PROCESSORS_COUNT) {
            coroutineScope.launch {
                launchProcessor()
            }
        }
    }

    @NewSpan("send-event")
    suspend fun send(message: String, session: WebSocketSession) {
        if (session.sendMessage(message)) {
            meterRegistry.counter(EVENT_SEND_METRICS).increment()
        }
    }

    @NewSpan("send-event-later")
    suspend fun sendLater(message: String, session: WebSocketSession) {
        channel.send(ChannelMessage(message, session))
        meterRegistry.counter(EVENT_SEND_LATER_SCHEDULED_METRICS).increment()
    }

    private suspend fun launchProcessor() {
        for (channelMessage in channel) {
            if (channelMessage.session.sendMessage(channelMessage.message)) {
                meterRegistry.counter(EVENT_SEND_LATER_METRICS).increment()
            }
        }
    }

    private suspend fun WebSocketSession.sendMessage(message: String): Boolean {
        try {
            if (isOpen) {
                withTimeout(Duration.ofSeconds(5)) {
                    send(message).awaitFirstOrNull()
                }
                return true
            }
        } catch (e: Exception) {
            logger.error(e) { "Error sending message" }
        }

        return false
    }

    companion object {
        const val EVENT_SEND_METRICS = "event.send"
        const val EVENT_SEND_LATER_METRICS = "event.send.later"
        const val EVENT_SEND_LATER_SCHEDULED_METRICS = "event.send.later.scheduled"
        private const val MESSAGE_PROCESSORS_COUNT = 30
    }

    @PreDestroy
    override fun close() {
        channel.close()
    }
}

data class ChannelMessage(val message: String, val session: WebSocketSession)
