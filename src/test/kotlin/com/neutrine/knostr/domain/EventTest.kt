package com.neutrine.knostr.domain

import com.neutrine.knostr.Utils.objectMapper
import com.neutrine.knostr.createEvent
import com.neutrine.knostr.loadFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class EventTest {

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/events/event-00.json",
            "/events/event-01.json",
            "/events/event-02.json",
            "/events/event-05.json",
        ]
    )
    fun `should return if an event is valid`(eventFixture: String) {
        val event = objectMapper.readValue(loadFile(eventFixture), Event::class.java)
        assertTrue(event.isValid())

        val randomId = "8c0b32be4a37ba894e62512cd0910cdcaa808591114ae4305edd79f2cb612364"

        assertFalse(event.copy(id = randomId).isValid())
        assertFalse(event.copy(pubkey = randomId).isValid())
        assertFalse(event.copy(createdAt = event.createdAt + 1).isValid())
        assertFalse(event.copy(kind = event.kind + 1).isValid())
        assertFalse(event.copy(tags = event.tags + listOf(listOf("t", "random"))).isValid())
        assertFalse(event.copy(content = "301").isValid())
        assertFalse(event.copy(sig = "b532c4890c8c9c60db2009995dec2b8c17be35cb01b0733765285ff06fa373a75654e4dee65668cbd1fea56649475211b0210e54c0897b7fa607b965b7f94d03").isValid())
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/events/event-00.json",
            "/events/event-01.json",
            "/events/event-02.json",
            "/events/event-05.json",
        ]
    )
    fun `should return if an event has a valid Id`(eventFixture: String) {
        val event = objectMapper.readValue(loadFile(eventFixture), Event::class.java)
        assertTrue(event.hasValidId())

        val randomId = "8c0b32be4a37ba894e62512cd0910cdcaa808591114ae4305edd79f2cb612364"

        assertFalse(event.copy(id = randomId).hasValidId())
        assertFalse(event.copy(pubkey = randomId).hasValidId())
        assertTrue(event.copy(sig = "b532c4890c8c9c60db2009995dec2b8c17be35cb01b0733765285ff06fa373a75654e4dee65668cbd1fea56649475211b0210e54c0897b7fa607b965b7f94d03").hasValidId())
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/events/event-00.json",
            "/events/event-01.json",
            "/events/event-02.json",
            "/events/event-05.json",
        ]
    )
    fun `should return if an event has a valid signature`(eventFixture: String) {
        val event = objectMapper.readValue(loadFile(eventFixture), Event::class.java)
        assertTrue(event.hasValidSignature())

        val randomId = "8c0b32be4a37ba894e62512cd0910cdcaa808591114ae4305edd79f2cb612364"

        assertTrue(event.copy(id = randomId).hasValidSignature())
        assertFalse(event.copy(pubkey = randomId).hasValidSignature())
        assertFalse(event.copy(sig = "b532c4890c8c9c60db2009995dec2b8c17be35cb01b0733765285ff06fa373a75654e4dee65668cbd1fea56649475211b0210e54c0897b7fa607b965b7f94d03").hasValidSignature())
    }

    @ParameterizedTest
    @CsvSource(
        "0, false",
        "1, false",
        "2, false",
        "3, false",
        "4, false",
        "5, true",
        "6, false",
        "7, false",
        "40, false",
        "10000, false",
        "20000, false",
    )
    fun `should return if an event should be deleted`(kind: Int, expected: Boolean) {
        val event = createEvent()
        assertEquals(expected, event.copy(kind = kind).shouldBeDeleted())
    }

    @ParameterizedTest
    @CsvSource(
        "0, true",
        "1, false",
        "2, false",
        "3, true",
        "4, false",
        "5, false",
        "6, false",
        "7, false"
    )
    fun `should return if an event should be overwritten`(kind: Int, expected: Boolean) {
        val event = createEvent()
        assertEquals(expected, event.copy(kind = kind).shouldOverwrite())
    }

    @Test
    fun `should return a list of referenced events`() {
        val event = createEvent().copy(
            tags = listOf(
                listOf("e", "2b2d5d3c92c4daa111f60503273dc5594f8147a59a4e2b48bb847cd38be1a1be"),
                listOf("p", "8c0b32be4a37ba894e62512cd0910cdcaa808591114ae4305edd79f2cb612364"),
                listOf("e", "770b32be4a37ba894e62512cd0910cdcaa808591114ae4305edd79f2cb612333"),
                listOf("t", "test")
            )
        )

        assertEquals(
            setOf(
                "2b2d5d3c92c4daa111f60503273dc5594f8147a59a4e2b48bb847cd38be1a1be",
                "770b32be4a37ba894e62512cd0910cdcaa808591114ae4305edd79f2cb612333"
            ),
            event.referencedEventIds()
        )
    }
}
