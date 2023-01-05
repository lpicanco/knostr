package com.neutrine.knostr.domain

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.neutrine.knostr.adapters.repository.EventStore
import com.neutrine.knostr.adapters.ws.MessageSender
import io.micronaut.websocket.WebSocketSession
import jakarta.inject.Singleton

@Singleton
class EventService(
    private val eventStore: EventStore,
    private val subscriptionService: SubscriptionService,
    private val messageSender: MessageSender
) {
    fun save(event: Event, session: WebSocketSession): CommandResult {
        val result = if (!event.hasValidId()) {
            CommandResult(event.id, false, "invalid: event id does not match")
        } else if (!event.hasValidSignature()) {
            CommandResult(event.id, false, "invalid: event signature verification failed")
        } else {
            val eventExists = eventStore.existsById(event.id)

            if (!eventExists) {
                eventStore.save(event)
                subscriptionService.notify(event, session)
            }

            CommandResult(event.id, true, if (eventExists) "duplicate:" else "")
        }

        messageSender.send(result.toJson(), session)
        return result
    }
}

data class CommandResult(val eventId: String, val result: Boolean, val description: String = "") {
    fun toJson(): String {
        return jacksonObjectMapper().writeValueAsString(
            listOf("OK", eventId, result, description)
        )
    }
}
