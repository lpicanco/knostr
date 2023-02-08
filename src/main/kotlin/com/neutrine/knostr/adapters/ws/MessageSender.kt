package com.neutrine.knostr.adapters.ws

import com.neutrine.knostr.await
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.tracing.annotation.NewSpan
import io.micronaut.websocket.WebSocketSession
import jakarta.annotation.PreDestroy
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
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
        coroutineScope.launch {
            startMessageConsumer()
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

    private suspend fun startMessageConsumer() {
        for (channelMessage in channel) {
            coroutineScope.launch {
                if (channelMessage.session.sendMessage(channelMessage.message)) {
                    meterRegistry.counter(EVENT_SEND_LATER_METRICS).increment()
                }
            }
        }
    }

    private suspend fun WebSocketSession.sendMessage(message: String): Boolean {
        try {
            if (isOpen) {
                sendAsync(message).await(Duration.ofSeconds(30))
                return true
            }
        } catch (e: TimeoutCancellationException) {
            logger.warn { "Timeout sending message" }
        } catch (e: Exception) {
            logger.error(e) { "Error sending message" }
        }

        return false
    }

    companion object {
        const val EVENT_SEND_METRICS = "event.send"
        const val EVENT_SEND_LATER_METRICS = "event.send.later"
        const val EVENT_SEND_LATER_SCHEDULED_METRICS = "event.send.later.scheduled"
    }

    @PreDestroy
    override fun close() {
        channel.close()
    }
}

data class ChannelMessage(val message: String, val session: WebSocketSession)
