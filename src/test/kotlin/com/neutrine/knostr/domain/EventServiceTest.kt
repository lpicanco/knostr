package com.neutrine.knostr.domain

import com.neutrine.knostr.adapters.repository.EventStore
import com.neutrine.knostr.adapters.ws.MessageSender
import com.neutrine.knostr.createEvent
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.websocket.WebSocketSession
import io.mockk.Called
import io.mockk.every
import io.mockk.excludeRecords
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class EventServiceTest {
    @MockK(relaxed = true)
    private lateinit var eventStore: EventStore
    @MockK(relaxed = true)
    private lateinit var subscriptionService: SubscriptionService
    @MockK(relaxed = true)
    private lateinit var messageSender: MessageSender
    private val meterRegistry = SimpleMeterRegistry()

    @InjectMockKs
    private lateinit var eventService: EventService

    @BeforeEach
    fun setUp() {
        meterRegistry.clear()
        excludeRecords { eventStore.existsById(any()) }
    }

    @Test
    fun `should save a new event`() {
        val event = createEvent()
        val session = mockk<WebSocketSession>()
        every { eventStore.existsById(event.id) } returns false

        val result = eventService.save(event, session)
        val expectedResult = CommandResult(event.id, true)

        verify { eventStore.save(event) }
        verify { subscriptionService.notify(event, session) }
        verify { messageSender.send(expectedResult.toJson(), session) }

        assertEquals(expectedResult, result)
        assertEquals(1.0, meterRegistry.counter(EventService.EVENT_SAVED_METRICS).count())
    }

    @Test
    fun `should not save an existing event`() {
        val event = createEvent()
        val session = mockk<WebSocketSession>()
        every { eventStore.existsById(event.id) } returns true

        val result = eventService.save(event, session)
        val expectedResult = CommandResult(event.id, true, "duplicate:")

        verify { subscriptionService wasNot Called }
        verify { eventStore wasNot Called }
        verify { messageSender.send(expectedResult.toJson(), session) }

        assertEquals(expectedResult, result)
        assertEquals(0.0, meterRegistry.counter(EventService.EVENT_SAVED_METRICS).count())
    }

    @Test
    fun `should not save an event with invalid id`() {
        val event = createEvent().copy(id = "invalid")
        val session = mockk<WebSocketSession>()

        val result = eventService.save(event, session)
        val expectedResult = CommandResult(event.id, false, "invalid: event id does not match")

        verify { subscriptionService wasNot Called }
        verify { eventStore wasNot Called }
        verify { messageSender.send(expectedResult.toJson(), session) }

        assertEquals(expectedResult, result)
        assertEquals(0.0, meterRegistry.counter(EventService.EVENT_SAVED_METRICS).count())
    }

    @Test
    fun `should not save an event with invalid signature`() {
        val event = createEvent().copy(sig = "b532c4890c8c9c60db2009995dec2b8c17be35cb01b0733765285ff06fa373a75654e4dee65668cbd1fea56649475211b0210e54c0897b7fa607b965b7f94d03")
        val session = mockk<WebSocketSession>()

        val result = eventService.save(event, session)
        val expectedResult = CommandResult(event.id, false, "invalid: event signature verification failed")

        verify { subscriptionService wasNot Called }
        verify { eventStore wasNot Called }
        verify { messageSender.send(expectedResult.toJson(), session) }

        assertEquals(expectedResult, result)
        assertEquals(0.0, meterRegistry.counter(EventService.EVENT_SAVED_METRICS).count())
    }
}

class CommandResultTest {
    @Test
    fun `should convert a valid json`() {
        val validCommand = CommandResult("eventId", true)
        assertEquals("""["OK","eventId",true,""]""", validCommand.toJson())

        val invalidCommand = CommandResult("eventId", false, "NOK")
        assertEquals("""["OK","eventId",false,"NOK"]""", invalidCommand.toJson())
    }
}