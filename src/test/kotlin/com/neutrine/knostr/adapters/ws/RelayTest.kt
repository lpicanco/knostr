package com.neutrine.knostr.adapters.ws

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.neutrine.knostr.adapters.ws.Relay.Companion.NIP11_DATA
import com.neutrine.knostr.domain.EventFilter
import com.neutrine.knostr.domain.EventService
import com.neutrine.knostr.domain.SubscriptionService
import com.neutrine.knostr.getRemoteAddress
import com.neutrine.knostr.infra.LimitsService
import io.micronaut.core.convert.value.MutableConvertibleValues
import io.micronaut.http.HttpRequest
import io.micronaut.http.server.util.HttpClientAddressResolver
import io.micronaut.websocket.WebSocketSession
import io.mockk.clearAllMocks
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.excludeRecords
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.CompletableFuture

@ExtendWith(MockKExtension::class)
@MockKExtension.ConfirmVerification
class RelayTest {

    @MockK(relaxed = true)
    private lateinit var eventService: EventService
    @MockK(relaxed = true)
    private lateinit var subscriptionService: SubscriptionService
    @MockK(relaxed = true)
    private lateinit var httpClientAddressResolver: HttpClientAddressResolver
    @MockK(relaxed = true)
    private lateinit var limitsService: LimitsService

    private val objectMapper = jacksonObjectMapper()

    private val session = mockk<WebSocketSession>(relaxed = false)

    @InjectMockKs
    private lateinit var relay: Relay

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        every { session.sendAsync(any<String>()) } returns CompletableFuture.completedFuture("")
        every { session.id } returns "SESSION_ID"
        every { session.attributes } returns MutableConvertibleValues.of(mutableMapOf())

        excludeRecords { session.id }
        excludeRecords { session.getRemoteAddress() }
        excludeRecords { limitsService.isIpBlocked(any()) }
    }

    @Test
    fun `should open a socket session`() {
        val request = mockk<HttpRequest<*>>(relaxed = true)
        every { httpClientAddressResolver.resolve(request) } returns "IP_ADDRESS"

        val response = relay.onOpen(session, null, request)

        assertNull(response)
        verify { httpClientAddressResolver.resolve(request) }
    }

    @Test
    fun `should return a NIP11 data`() {
        val response = relay.onOpen(null, "application/nostr+json", null)
        assertEquals(NIP11_DATA, response)
        confirmVerified()
    }

    @Test
    fun `should return a default string for a http request`() {
        val response = relay.onOpen(null, null, null)
        assertEquals("Use a Nostr client or Websocket client to connect", response)
        confirmVerified()
    }

    @Test
    fun `should subscribe to a filter`() = runTest {
        relay.onMessage("""["REQ", "CID", {"ids": ["433343242343334324234333432423"]}]""", session).awaitFirstOrNull()
        val expectedFilter = EventFilter(ids = setOf("433343242343334324234333432423"))
        coVerify { subscriptionService.subscribe("CID", session, setOf(expectedFilter)) }
        confirmVerified()
    }

    @Test
    fun `should notify a NOTICE for invalid filters`() = runTest {
        relay.onMessage("""["REQ", "CID", {"ids": ["2e"]}]""", session).awaitFirstOrNull()
        val expectedResult = """["NOTICE","invalid: id size must be greater than or equal to 20"]"""

        verify { session.sendAsync(expectedResult) }
        confirmVerified()
    }

    @Test
    fun `should notify a NOTICE for a blocked IP`() = runTest {
        every { limitsService.isIpBlocked(any()) } returns true

        relay.onMessage("""["REQ", "CID", {"ids": ["433343242343334324234333432423"]}]""", session).awaitFirstOrNull()
        val expectedResult = """["NOTICE","blocked: IP blocked"]"""

        verify { session.sendAsync(expectedResult) }
        confirmVerified()
    }
}
