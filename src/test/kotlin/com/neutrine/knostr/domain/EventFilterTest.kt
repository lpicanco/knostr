package com.neutrine.knostr.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class EventFilterTest {
    private val baseFilter = EventFilter()

    @Test
    fun `should return true when the filter is empty`() {
        assertTrue(EventFilter().test(EVENT))
    }

    @Test
    fun `should filter by id`() {
        assertTrue(baseFilter.copy(ids = setOf(EVENT.id)).test(EVENT))
        assertTrue(baseFilter.copy(ids = setOf(EVENT.id.substring(0, 10))).test(EVENT))
        assertTrue(baseFilter.copy(ids = setOf("404", EVENT.id)).test(EVENT))
        assertFalse(baseFilter.copy(ids = setOf(EVENT.id.substring(10))).test(EVENT))
        assertFalse(baseFilter.copy(ids = setOf(EVENT.pubkey)).test(EVENT))
    }

    @Test
    fun `should filter by author`() {
        assertTrue(baseFilter.copy(authors = setOf(EVENT.pubkey)).test(EVENT))
        assertTrue(baseFilter.copy(authors = setOf(EVENT.pubkey.substring(0, 10))).test(EVENT))
        assertTrue(baseFilter.copy(authors = setOf("404", EVENT.pubkey)).test(EVENT))
        assertFalse(baseFilter.copy(authors = setOf(EVENT.pubkey.substring(10))).test(EVENT))
        assertFalse(baseFilter.copy(authors = setOf(EVENT.id)).test(EVENT))
    }

    @Test
    fun `should filter by kind`() {
        assertTrue(baseFilter.copy(kinds = setOf(EVENT.kind)).test(EVENT))
        assertTrue(baseFilter.copy(kinds = setOf(404, EVENT.kind)).test(EVENT))
        assertFalse(baseFilter.copy(kinds = setOf(404)).test(EVENT))
    }

    @Test
    fun `should filter by since`() {
        assertTrue(baseFilter.copy(since = EVENT.createdAt - 1).test(EVENT))
        assertTrue(baseFilter.copy(since = EVENT.createdAt).test(EVENT))
        assertFalse(baseFilter.copy(since = EVENT.createdAt + 1).test(EVENT))
    }

    @Test
    fun `should filter by until`() {
        assertTrue(baseFilter.copy(until = EVENT.createdAt + 1).test(EVENT))
        assertTrue(baseFilter.copy(until = EVENT.createdAt).test(EVENT))
        assertFalse(baseFilter.copy(until = EVENT.createdAt - 1).test(EVENT))
    }

    @Test
    fun `should filter by tags`() {
        assertTrue(baseFilter.copy(tags = mapOf("client" to setOf("mock"))).test(EVENT))
        assertFalse(baseFilter.copy(tags = mapOf("client" to setOf("moc"))).test(EVENT))
        assertTrue(baseFilter.copy(tags = mapOf("client" to setOf("moc", "mock"))).test(EVENT))
        assertTrue(
            baseFilter.copy(
                tags = mapOf(
                    "client" to setOf("moc", "mock"),
                    "e" to setOf("5e7ae588d7d11eac4c25906e6da807e68c6498f49a38e4692be5a089616ceb18")
                )
            ).test(EVENT)
        )
        assertTrue(
            baseFilter.copy(
                tags = mapOf(
                    "client" to setOf("mock"),
                    "e" to setOf("404")
                )
            ).test(EVENT)
        )
    }

    @Test
    fun `should filter by search`() {
        assertTrue(baseFilter.copy(search = EVENT.content).test(EVENT))
        assertTrue(baseFilter.copy(search = "quick").test(EVENT))
        assertTrue(baseFilter.copy(search = "quick brown").test(EVENT))
        assertTrue(baseFilter.copy(search = "quick fox").test(EVENT))
        assertTrue(baseFilter.copy(search = "quick, fox").test(EVENT))
        assertTrue(baseFilter.copy(search = "QuiCK fOx").test(EVENT))
        assertFalse(baseFilter.copy(search = "bro").test(EVENT))
        assertFalse(baseFilter.copy(search = "quicks").test(EVENT))
        assertFalse(baseFilter.copy(search = "quick not").test(EVENT))
    }

    @Test
    fun `should filter by search on json`() {
        val content = """"content": "{\"lud06\":\"\",\"display_name\":\"Luiz\",\"website\":\"\",\"name\":\"lpicanco\",\"about\":\"\",\"picture\":\"https:\\/\\/avatars.githubusercontent.com\\/u\\/8377\"}""""
        val event = EVENT.copy(content = content)
        assertTrue(baseFilter.copy(search = event.content).test(event))
        assertTrue(baseFilter.copy(search = "avatars.githubusercontent").test(event))
        assertTrue(baseFilter.copy(search = "githubusercontent").test(event))
        assertTrue(baseFilter.copy(search = "lud06").test(event))
        assertFalse(baseFilter.copy(search = "lud06s").test(event))
    }

    @ParameterizedTest
    @CsvSource(
        "52b9055fabe28c51641bd0230096258f7d6e517910bf99fd3425d1ae507d8c34,2e9397a8c9268585668b76479f88e359d0ee261f8e8ea07b3b3450546d1601c8,1,1672313107,1672313107,client,mock,TRUE",
        ",,,1672313106,1672313108,,,TRUE",
        "404,2e9397a8c9268585668b76479f88e359d0ee261f8e8ea07b3b3450546d1601c8,1,1672313107,1672313107,client,mock,FALSE",
        "52b9055fabe28c51641bd0230096258f7d6e517910bf99fd3425d1ae507d8c34,404,1,1672313107,1672313107,client,mock,FALSE",
        "52b9055fabe28c51641bd0230096258f7d6e517910bf99fd3425d1ae507d8c34,2e9397a8c9268585668b76479f88e359d0ee261f8e8ea07b3b3450546d1601c8,2,1672313107,1672313107,client,mock,FALSE",
        "52b9055fabe28c51641bd0230096258f7d6e517910bf99fd3425d1ae507d8c34,2e9397a8c9268585668b76479f88e359d0ee261f8e8ea07b3b3450546d1601c8,1,1672313108,1672313107,client,mock,FALSE",
        "52b9055fabe28c51641bd0230096258f7d6e517910bf99fd3425d1ae507d8c34,2e9397a8c9268585668b76479f88e359d0ee261f8e8ea07b3b3450546d1601c8,1,1672313107,1672313106,client,mock,FALSE",
        "52b9055fabe28c51641bd0230096258f7d6e517910bf99fd3425d1ae507d8c34,2e9397a8c9268585668b76479f88e359d0ee261f8e8ea07b3b3450546d1601c8,1,1672313107,1672313107,client,moc,FALSE"
    )
    fun `should filter by multiple fields`(
        id: String?,
        author: String?,
        kind: Int?,
        since: Int?,
        until: Int?,
        tagKey: String?,
        tagValue: String?,
        expected: Boolean
    ) {
        val filter = EventFilter(
            ids = id?.let { setOf(it) } ?: emptySet(),
            authors = author?.let { setOf(it) } ?: emptySet(),
            kinds = kind?.let { setOf(it) } ?: emptySet(),
            tags = tagKey?.let { mapOf(tagKey to setOf(tagValue!!)) } ?: emptyMap(),
            since = since,
            until = until
        )
        assertEquals(expected, filter.test(EVENT))
    }

    @ParameterizedTest
    @CsvSource(
        "52b9055fabe28c51641bd0230096258f7d6e517910bf99fd3425d1ae507d8c34,TRUE",
        "52b9055fabe28c51641bd0230096258f7d6e517910bf99fd3425d1ae507d8c3,FALSE",
        "52b9,FALSE"
    )
    fun `should return if an exact match can be used for eventId`(eventId: String, expected: Boolean) {
        assertEquals(expected, EventFilter.canUseExactMatchForEventId(eventId))
    }

    @ParameterizedTest
    @CsvSource(
        "52b9055fabe28c51641bd0230096258f7d6e517910bf99fd3425d1ae507d8c34,TRUE",
        "52b9055fabe28c51641bd0230096258f7d6e517910bf99fd3425d1ae507d8c3,FALSE",
        "52b9,FALSE"
    )
    fun `should return if an exact match can be used for pubkey`(pubkey: String, expected: Boolean) {
        assertEquals(expected, EventFilter.canUseExactMatchForAuthor(pubkey))
    }

    companion object {
        private val EVENT = Event(
            id = "52b9055fabe28c51641bd0230096258f7d6e517910bf99fd3425d1ae507d8c34",
            pubkey = "2e9397a8c9268585668b76479f88e359d0ee261f8e8ea07b3b3450546d1601c8",
            createdAt = 1672313107,
            kind = 1,
            tags = listOf(
                listOf("e", "5e7ae588d7d11eac4c25906e6da807e68c6498f49a38e4692be5a089616ceb18", "wss://relay.damus.io"),
                listOf("e", "1f5b64dbe65ad8ef9d82810af83cd9cbce082f9f2aa4c94ef978d711c5039e51"),
                listOf("p", "af8a52753d36b10c1bcc1e92d61ea2a4d3badec286444ab022d1e56fae9e200f"),
                listOf("client", "mock"),
            ),
            content = "The quick brown fox, jumps over the lazy dog",
            sig = ""
        )
    }
}
