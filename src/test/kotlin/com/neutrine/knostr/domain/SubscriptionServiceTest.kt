package com.neutrine.knostr.domain

import com.fasterxml.jackson.databind.ObjectMapper
import com.neutrine.knostr.Utils
import com.neutrine.knostr.adapters.repository.EventStore
import com.neutrine.knostr.adapters.ws.MessageSender
import com.neutrine.knostr.createEvent
import com.neutrine.knostr.domain.SubscriptionService.Companion.EVENT_SUBSCRIPTION_METRICS
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.websocket.WebSocketSession
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verifySequence
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class SubscriptionServiceTest {
    @MockK(relaxed = true)
    private lateinit var eventRepository: EventStore
    @MockK(relaxed = true)
    private lateinit var messageSender: MessageSender
    private val objectMapper: ObjectMapper = Utils.objectMapper
    private val meterRegistry = SimpleMeterRegistry()

    private val session = mockk<WebSocketSession>()

    @InjectMockKs
    private lateinit var subscriptionService: SubscriptionService

    @BeforeEach
    fun setUp() {
        meterRegistry.clear()
        every { session.id } returns "session-id"
        every { session.isOpen } returns true
    }

    @Test
    fun `should subscribe and send events matching the filters`() = runTest {
        val subId = "sub42"
        val filters = setOf(EventFilter(since = 42))

        val events = listOf(createEvent("/events/event-00.json"), createEvent("/events/event-01.json"))

        coEvery { eventRepository.filter(filters) } returns events
        every { messageSender.send(any(), any()) } returns Job().also { it.complete() }

        subscriptionService.subscribe(subId, session, filters)

        verifySequence {
            messageSender.send("""["EVENT","$subId",${objectMapper.writeValueAsString(events[0])}]""", session)
            messageSender.send("""["EVENT","$subId",${objectMapper.writeValueAsString(events[1])}]""", session)
            messageSender.send("""["EOSE","$subId"]""", session)
        }

        assertTrue(subscriptionService.exists(Subscription(subId, session, filters)))
        assertEquals(1.0, meterRegistry.counter(EVENT_SUBSCRIPTION_METRICS).count())
    }

    @Test
    fun `should return if subscription exists`() = runTest {
        val subscription = Subscription("sub42", session, setOf(EventFilter(since = 42)))
        assertFalse(subscriptionService.exists(subscription))
        subscriptionService.subscribe(subscription.id, session, subscription.filters)
        assertTrue(subscriptionService.exists(subscription))
    }

    @Test
    fun `should unsubscribe`() = runTest {
        val subscription = Subscription("sub42", session, setOf(EventFilter(since = 42)))
        subscriptionService.subscribe(subscription.id, session, subscription.filters)
        assertTrue(subscriptionService.exists(subscription))

        subscriptionService.unsubscribe(subscription.id, session)
        assertFalse(subscriptionService.exists(subscription))
    }

    @Test
    fun `unsubscribe a socket session`() = runTest {
        val session02 = mockk<WebSocketSession>()
        every { session02.id } returns "session-02"

        val subscription01 = Subscription("sub42", session, setOf(EventFilter(since = 42)))
        val subscription02 = Subscription("sub43", session, emptySet())
        val subscription03 = Subscription("sub43", session02, emptySet())

        subscriptionService.subscribe(subscription01.id, session, subscription01.filters)
        subscriptionService.subscribe(subscription02.id, session, subscription02.filters)
        subscriptionService.subscribe(subscription03.id, session02, subscription03.filters)

        assertTrue(subscriptionService.exists(subscription01))
        assertTrue(subscriptionService.exists(subscription02))
        assertTrue(subscriptionService.exists(subscription03))

        subscriptionService.unsubscribeSocketSession(session)
        assertFalse(subscriptionService.exists(subscription01))
        assertFalse(subscriptionService.exists(subscription02))
        assertTrue(subscriptionService.exists(subscription03))
    }

    @Test
    fun `should notify a event`() = runTest {
        val session02 = mockk<WebSocketSession>()
        every { session02.id } returns "session-02"
        every { session02.isOpen } returns true
        val eventSession = mockk<WebSocketSession>()
        every { eventSession.id } returns "eventSession"
        every { eventSession.isOpen } returns true

        val event = createEvent()

        subscriptionService.subscribe("sub42", session, setOf(EventFilter(since = event.createdAt)))
        subscriptionService.subscribe("sub43", session02, setOf(EventFilter()))
        subscriptionService.subscribe("sub44", session02, setOf(EventFilter(kinds = setOf(9))))
        subscriptionService.subscribe("sub45", session02, emptySet())
        subscriptionService.subscribe("sub46", eventSession, setOf(EventFilter(since = event.createdAt)))
        clearMocks(messageSender)

        subscriptionService.notify(event, eventSession)

        verifySequence {
            messageSender.send("""["EVENT","sub42",${objectMapper.writeValueAsString(event)}]""", session)
            messageSender.send("""["EVENT","sub43",${objectMapper.writeValueAsString(event)}]""", session02)
        }
    }
}
