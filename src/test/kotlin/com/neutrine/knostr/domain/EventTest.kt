package com.neutrine.knostr.domain

import com.neutrine.knostr.Utils.objectMapper
import com.neutrine.knostr.loadFile
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class EventTest {

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/events/event-00.json",
            "/events/event-01.json",
            "/events/event-02.json",
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
}
