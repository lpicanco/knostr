package com.neutrine.knostr.domain

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.neutrine.knostr.adapters.repository.EventStore
import com.neutrine.knostr.adapters.ws.MessageSender
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.tracing.annotation.NewSpan
import io.micronaut.websocket.WebSocketSession
import jakarta.inject.Singleton
import mu.KotlinLogging

@Singleton
class EventService(
    private val eventStore: EventStore,
    private val subscriptionService: SubscriptionService,
    private val messageSender: MessageSender,
    private val meterRegistry: MeterRegistry
) {
    private val logger = KotlinLogging.logger {}

    @NewSpan("save-event")
    suspend fun save(event: Event, session: WebSocketSession): CommandResult {
        val result = try {
            if (!event.hasValidId()) {
                CommandResult.invalid(event, "event id does not match")
            } else if (!event.hasValidSignature()) {
                CommandResult.invalid(event, "event signature verification failed")
            } else if (eventStore.existsById(event.id)) {
                CommandResult.duplicated(event)
            } else {
                when {
                    event.shouldBeDeleted() -> handleDelete(event)
                    event.shouldOverwrite() -> handleOverwrite(event)
                    event.shouldSave() -> handleSave(event)
                }

                subscriptionService.notify(event, session)
                CommandResult.ok(event)
            }
        } catch (e: Exception) {
            logger.error("Error saving the event", e)
            CommandResult.error(event, "internal error saving the event")
        }

        messageSender.send(result.toJson(), session)
        return result
    }

    private suspend fun handleOverwrite(event: Event) {
        handleSave(event)
        eventStore.deleteOldestOfKind(event.pubkey, event.kind)
    }

    private fun handleDelete(event: Event) {
        handleSave(event)
        eventStore.deleteAll(event.pubkey, event.referencedEventIds())
    }

    private fun handleSave(event: Event) {
        eventStore.save(event)
        meterRegistry.counter(EVENT_SAVED_METRICS).increment()
    }

    companion object {
        const val EVENT_SAVED_METRICS = "event.saved"
    }
}

data class CommandResult(val eventId: String, val result: Boolean, val description: String = "") {
    fun toJson(): String {
        return jacksonObjectMapper().writeValueAsString(
            listOf("OK", eventId, result, description)
        )
    }

    companion object {
        fun ok(event: Event) = CommandResult(event.id, true)
        fun duplicated(event: Event) = CommandResult(event.id, true, "duplicate:")
        fun invalid(event: Event, message: String) = CommandResult(event.id, false, "invalid: $message")
        fun error(event: Event, message: String) = CommandResult(event.id, false, "error: $message")
    }
}
