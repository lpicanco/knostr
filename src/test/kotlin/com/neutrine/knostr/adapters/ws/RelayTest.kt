package com.neutrine.knostr.adapters.ws

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.neutrine.knostr.adapters.ws.Relay.Companion.NIP11_DATA
import com.neutrine.knostr.domain.EventService
import com.neutrine.knostr.domain.SubscriptionService
import io.micronaut.websocket.WebSocketSession
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
@MockKExtension.ConfirmVerification
class RelayTest {

    @MockK(relaxed = true)
    private lateinit var eventService: EventService
    @MockK(relaxed = true)
    private lateinit var subscriptionService: SubscriptionService
    private val objectMapper = jacksonObjectMapper()
    private val testScope = TestScope()

    private val session = mockk<WebSocketSession>(relaxed = true)

    @InjectMockKs
    private lateinit var relay: Relay

    @BeforeEach
    fun setUp() {
        clearAllMocks()
    }

    @Test
    fun `should open a socket session`() {
        val response = relay.onOpen(session, null)
        assertNull(response)
        confirmVerified()
    }

    @Test
    fun `should return a NIP11 data`() {
        val response = relay.onOpen(null, "application/nostr+json")
        assertEquals(NIP11_DATA, response)
        confirmVerified()
    }

    @Test
    fun `should return a default string for a http request`() {
        val response = relay.onOpen(null, null)
        assertEquals("Use a Nostr client or Websocket client to connect", response)
        confirmVerified()
    }

    @Test
    fun `should notify a NOTICE for large payloads`() = runTest {
        relay.onMessage("message".repeat(66_000), session)
        val expectedResult = """["NOTICE","invalid: payload size must be less than or equal to 65536"]"""

        advanceUntilIdle()

        verify { session.sendSync(expectedResult) }
        confirmVerified()
    }
}
