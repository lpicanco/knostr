package com.neutrine.knostr.adapters.ws

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.websocket.WebSocketSession
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.CompletableFuture

@ExtendWith(MockKExtension::class)
@MockKExtension.ConfirmVerification
class MessageSenderTest {
    private val testScope = TestScope()
    private val meterRegistry = SimpleMeterRegistry()

    @InjectMockKs(overrideValues = true)
    private lateinit var messageSender: MessageSender

    @BeforeEach
    fun setUp() {
        meterRegistry.clear()
        clearAllMocks()
    }

    @Test
    fun `should send a message`() = testScope.runTest {
        val message = "message"
        val session = mockk<WebSocketSession>(relaxed = true)
        every { session.isOpen } returns true
        every { session.sendAsync(message) } returns CompletableFuture.completedFuture(message)

        messageSender.send(message, session)

        verify { session.sendAsync(message) }

        assertEquals(1.0, meterRegistry.counter(MessageSender.EVENT_SEND_METRICS).count())
        messageSender.close()
    }

    @Test
    fun `should not send a message if session is closed`() = testScope.runTest {
        val session = mockk<WebSocketSession>()
        every { session.isOpen } returns false
        messageSender.send("message", session)

        advanceUntilIdle()

        verifySequence { session.isOpen }
        assertEquals(0.0, meterRegistry.counter(MessageSender.EVENT_SEND_METRICS).count())

        messageSender.close()
    }

    @Test
    fun `should send a message later`() = testScope.runTest {
        val message = "message"
        val session = mockk<WebSocketSession>(relaxed = true)
        every { session.isOpen } returns true
        every { session.sendAsync(message) } returns CompletableFuture.completedFuture(message)

        messageSender.sendLater(message, session)

        verify { session wasNot Called }

        assertEquals(1.0, meterRegistry.counter(MessageSender.EVENT_SEND_LATER_SCHEDULED_METRICS).count())
        assertEquals(0.0, meterRegistry.counter(MessageSender.EVENT_SEND_LATER_METRICS).count())

        advanceUntilIdle()

        verifySequence {
            session.isOpen
            session.sendAsync(message)
        }

        assertEquals(1.0, meterRegistry.counter(MessageSender.EVENT_SEND_LATER_SCHEDULED_METRICS).count())
        assertEquals(1.0, meterRegistry.counter(MessageSender.EVENT_SEND_LATER_METRICS).count())
        messageSender.close()
    }

    @Test
    fun `should not send a message later if session is closed`() = testScope.runTest {
        val session = mockk<WebSocketSession>()
        every { session.isOpen } returns false
        messageSender.sendLater("message", session)

        advanceUntilIdle()

        verifySequence { session.isOpen }
        assertEquals(0.0, meterRegistry.counter(MessageSender.EVENT_SEND_METRICS).count())

        messageSender.close()
    }
}
