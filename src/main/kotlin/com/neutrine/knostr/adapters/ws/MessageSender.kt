package com.neutrine.knostr.adapters.ws

import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.tracing.annotation.NewSpan
import io.micronaut.websocket.WebSocketSession
import jakarta.annotation.PreDestroy
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.withTimeout
import kotlinx.coroutines.withContext
import java.time.Duration

@Singleton
class MessageSender(
    private val coroutineScope: CoroutineScope,
    private val meterRegistry: MeterRegistry
) : AutoCloseable {
    private val channel = Channel<ChannelMessage>(10_000)
    private val messageSenderDispatcher = Dispatchers.IO.limitedParallelism(30)

    init {
        repeat(MESSAGE_PROCESSORS_COUNT) {
            coroutineScope.launch {
                launchProcessor()
            }
        }
    }

    @NewSpan("send-event")
    fun send(message: String, session: WebSocketSession): Job = coroutineScope.launch {
        if (session.isOpen) {
            withTimeout(Duration.ofSeconds(2)) {
                session.sendSync(message)
            }
            meterRegistry.counter(EVENT_SEND_METRICS).increment()
        }
    }

    @NewSpan("send-event-later")
    suspend fun sendLater(message: String, session: WebSocketSession) {
        channel.send(ChannelMessage(message, session))
    }

    private suspend fun launchProcessor() {
        for (channelMessage in channel) {
            if (channelMessage.session.isOpen) {
                withTimeout(Duration.ofSeconds(2)) {
                    withContext(messageSenderDispatcher) {
                        channelMessage.session.sendSync(channelMessage.message)
                    }
                }
                meterRegistry.counter(EVENT_SEND_METRICS).increment()
            }
        }
    }

    companion object {
        const val EVENT_SEND_METRICS = "event.send"
        private const val MESSAGE_PROCESSORS_COUNT = 20
    }

    @PreDestroy
    override fun close() {
        channel.close()
    }
}

data class ChannelMessage(val message: String, val session: WebSocketSession)
