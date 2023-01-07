package com.neutrine.knostr.domain

import com.fasterxml.jackson.databind.ObjectMapper
import com.neutrine.knostr.adapters.repository.EventStore
import com.neutrine.knostr.adapters.ws.MessageSender
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.tracing.annotation.NewSpan
import io.micronaut.websocket.WebSocketSession
import jakarta.inject.Singleton
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import java.util.Collections

@Singleton
class SubscriptionService(
    private val objectMapper: ObjectMapper,
    private val eventRepository: EventStore,
    private val messageSender: MessageSender,
    private val meterRegistry: MeterRegistry
) {
    private val subscriptions: MutableList<Subscription> = Collections.synchronizedList(mutableListOf())

    @NewSpan("subscribe")
    suspend fun subscribe(subscriptionId: String, session: WebSocketSession, filters: Set<EventFilter>) {
        subscriptions.add(Subscription(subscriptionId, session, filters))
        meterRegistry.counter(EVENT_SUBSCRIPTION_METRICS).increment()

        eventRepository.filter(filters)
            .map { sendEvent(it, subscriptionId, session) }
            .joinAll()

        messageSender.send(
            objectMapper.writeValueAsString(
                listOf("EOSE", subscriptionId)
            ),
            session
        )
    }

    fun unsubscribe(subscriptionId: String, session: WebSocketSession) {
        subscriptions.filter { it.id == subscriptionId && it.socketSession.id == session.id }
            .forEach { subscriptions.remove(it) }
    }

    fun unsubscribeSocketSession(session: WebSocketSession) {
        subscriptions.filter { it.socketSession.id == session.id }
            .forEach { subscriptions.remove(it) }
    }

    fun notify(event: Event, session: WebSocketSession) {
        subscriptions.filter { subscription ->
            subscription.socketSession.id != session.id && subscription.socketSession.isOpen &&
                subscription.filters.any { it.test(event) }
        }.map { subscription ->
            messageSender.send(createEventMessage(event, subscription.id), subscription.socketSession)
        }
    }

    private fun createEventMessage(event: Event, subscriptionId: String): String {
        return objectMapper.writeValueAsString(
            listOf("EVENT", subscriptionId, event)
        )
    }

    private fun sendEvent(event: Event, subscriptionId: String, session: WebSocketSession): Job {
        return messageSender.send(
            objectMapper.writeValueAsString(
                listOf("EVENT", subscriptionId, event)
            ),
            session
        )
    }

    companion object {
        const val EVENT_SUBSCRIPTION_METRICS = "event.subscription"
    }
}

data class Subscription(
    val id: String,
    val socketSession: WebSocketSession,
    val filters: Set<EventFilter>
)
