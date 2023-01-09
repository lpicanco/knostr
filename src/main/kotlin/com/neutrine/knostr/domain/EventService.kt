package com.neutrine.knostr.domain

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.neutrine.knostr.adapters.repository.EventStore
import com.neutrine.knostr.adapters.ws.MessageSender
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.tracing.annotation.NewSpan
import io.micronaut.websocket.WebSocketSession
import jakarta.inject.Singleton

@Singleton
class EventService(
    private val eventStore: EventStore,
    private val subscriptionService: SubscriptionService,
    private val messageSender: MessageSender,
    private val meterRegistry: MeterRegistry
) {
    @NewSpan("save-event")
    suspend fun save(event: Event, session: WebSocketSession): CommandResult {
        val result = if (!event.hasValidId()) {
            CommandResult(event.id, false, "invalid: event id does not match")
        } else if (!event.hasValidSignature()) {
            CommandResult(event.id, false, "invalid: event signature verification failed")
        } else if (eventStore.existsById(event.id)) {
            CommandResult.duplicated(event)
        } else {
            when {
                event.shouldBeDeleted() -> handleDelete(event)
                event.shouldOverwrite() -> handleOverwrite(event)
                else -> handleSave(event)
            }

            subscriptionService.notify(event, session)
            CommandResult.ok(event)
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
    }
}
