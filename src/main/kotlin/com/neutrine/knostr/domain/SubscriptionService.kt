package com.neutrine.knostr.domain

import com.fasterxml.jackson.databind.ObjectMapper
import com.neutrine.knostr.adapters.repository.EventStore
import com.neutrine.knostr.adapters.ws.MessageSender
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.tracing.annotation.NewSpan
import io.micronaut.websocket.WebSocketSession
import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Singleton
class SubscriptionService(
    private val objectMapper: ObjectMapper,
    private val eventRepository: EventStore,
    private val messageSender: MessageSender,
    private val meterRegistry: MeterRegistry
) {
    private val subscriptions: MutableMap<String, Subscription> = ConcurrentHashMap()
    private val subscriptionCount: AtomicInteger = meterRegistry.gauge(EVENT_SUBSCRIPTION_TOTAL_METRICS, AtomicInteger(0))

    @NewSpan("subscribe")
    suspend fun subscribe(subscriptionId: String, session: WebSocketSession, filters: Set<EventFilter>) {
        subscriptions[getKey(subscriptionId, session)] = Subscription(subscriptionId, session, filters)
        meterRegistry.counter(EVENT_SUBSCRIPTION_METRICS).increment()
        subscriptionCount.set(subscriptions.size)

        eventRepository.filter(filters)
            .map { sendEvent(it, subscriptionId, session) }

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
        subscriptionCount.set(subscriptions.size)
    }

    fun unsubscribeSocketSession(session: WebSocketSession) {
        getSubscriptions(session).forEach { unsubscribe(it.id, session) }
    }

    suspend fun notify(event: Event, session: WebSocketSession) {
        subscriptions.values.filter { subscription ->
            subscription.socketSession.id != session.id && subscription.socketSession.isOpen &&
                subscription.filters.any { it.test(event) }
        }.map { subscription ->
            messageSender.sendLater(createEventMessage(event, subscription.id), subscription.socketSession)
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

    private suspend fun sendEvent(event: Event, subscriptionId: String, session: WebSocketSession) {
        messageSender.send(
            objectMapper.writeValueAsString(
                listOf("EVENT", subscriptionId, event)
            ),
            session
        )
    }

    companion object {
        const val EVENT_SUBSCRIPTION_METRICS = "event.subscription"
        const val EVENT_SUBSCRIPTION_TOTAL_METRICS = "event.subscription.total"
    }
}

data class Subscription(
    val id: String,
    val socketSession: WebSocketSession,
    val filters: Set<EventFilter>
)
