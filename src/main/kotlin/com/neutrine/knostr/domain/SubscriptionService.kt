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
import java.util.concurrent.ConcurrentHashMap

@Singleton
class SubscriptionService(
    private val objectMapper: ObjectMapper,
    private val eventRepository: EventStore,
    private val messageSender: MessageSender,
    private val meterRegistry: MeterRegistry
) {
    private val subscriptions: MutableMap<String, Subscription> = ConcurrentHashMap()

    @NewSpan("subscribe")
    suspend fun subscribe(subscriptionId: String, session: WebSocketSession, filters: Set<EventFilter>) {
        subscriptions[getKey(subscriptionId, session)] = Subscription(subscriptionId, session, filters)
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

    fun exists(subscription: Subscription): Boolean {
        val found = subscriptions[getKey(subscription.id, subscription.socketSession)]
        return found != null && found.filters == subscription.filters
    }

    fun unsubscribe(subscriptionId: String, session: WebSocketSession) {
        subscriptions.remove(getKey(subscriptionId, session))
    }

    fun unsubscribeSocketSession(session: WebSocketSession) {
        getSubscriptions(session).forEach { unsubscribe(it.id, session) }
    }

    fun notify(event: Event, session: WebSocketSession) {
        subscriptions.values.filter { subscription ->
            subscription.socketSession.id != session.id && subscription.socketSession.isOpen &&
                subscription.filters.any { it.test(event) }
        }.map { subscription ->
            messageSender.send(createEventMessage(event, subscription.id), subscription.socketSession)
        }
    }

    private fun getSubscriptions(session: WebSocketSession): List<Subscription> {
        return subscriptions.values.filter { it.socketSession == session }
    }

    private fun createEventMessage(event: Event, subscriptionId: String): String {
        return objectMapper.writeValueAsString(
            listOf("EVENT", subscriptionId, event)
        )
    }

    private fun getKey(subscriptionId: String, session: WebSocketSession): String {
        return "$subscriptionId-${session.id}"
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
