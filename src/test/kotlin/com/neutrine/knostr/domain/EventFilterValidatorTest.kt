package com.neutrine.knostr.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class EventFilterValidatorTest {

    @ParameterizedTest
    @CsvSource(
        "52b9055fabe28c51641bd0230096258f7d6e517910bf99fd3425d1ae507d8c34,2e9397a8c9268585668b76479f88e359d0ee261f8e8ea07b3b3450546d1601c8,1,1672313107,1672313107,client,mock,TRUE",
        ",,,1672313106,1672313108,,,TRUE",
        "40416723131016723131066,2e9397a8c9268585668b76479f88e359d0ee261f8e8ea07b3b3450546d1601c8,1,1672313107,1672313107,client,mock,FALSE",
        "52b9055fabe28c51641bd0230096258f7d6e517910bf99fd3425d1ae507d8c34,7910bf99fd3425d1ae507d8c34,1,1672313107,1672313107,client,mock,FALSE",
        "52b9055fabe28c51641bd0230096258f7d6e517910bf99fd3425d1ae507d8c34,2e9397a8c9268585668b76479f88e359d0ee261f8e8ea07b3b3450546d1601c8,2,1672313107,1672313107,client,mock,FALSE",
        "52b9055fabe28c51641bd0230096258f7d6e517910bf99fd3425d1ae507d8c34,2e9397a8c9268585668b76479f88e359d0ee261f8e8ea07b3b3450546d1601c8,1,1672313108,1672313107,client,mock,FALSE",
        "52b9055fabe28c51641bd0230096258f7d6e517910bf99fd3425d1ae507d8c34,2e9397a8c9268585668b76479f88e359d0ee261f8e8ea07b3b3450546d1601c8,1,1672313107,1672313106,client,mock,FALSE",
        "52b9055fabe28c51641bd0230096258f7d6e517910bf99fd3425d1ae507d8c34,2e9397a8c9268585668b76479f88e359d0ee261f8e8ea07b3b3450546d1601c8,1,1672313107,1672313107,client,moc,FALSE"
    )
    fun `should return null if the filter is valid`(
        id: String?,
        author: String?,
        kind: Int?,
        since: Int?,
        until: Int?,
        tagKey: String?,
        tagValue: String?,
        expected: Boolean
    ) {
        val filter01 = EventFilter(
            ids = id?.let { setOf(it) } ?: emptySet(),
            authors = author?.let { setOf(it) } ?: emptySet(),
            kinds = kind?.let { setOf(it) } ?: emptySet(),
            tags = tagKey?.let { mapOf(tagKey to setOf(tagValue!!)) } ?: emptyMap(),
            since = since,
            until = until
        )

        val filter02 = EventFilter(
            ids = id?.let { setOf(it) } ?: emptySet(),
            tags = tagKey?.let { mapOf(tagKey to setOf(tagValue!!)) } ?: emptyMap(),
            since = since,
            until = until
        )
        assertNull(EventFilterValidator.validate(setOf(filter01, filter02)))
    }

    @Test
    fun `should convert to Json`() {
        assertEquals("""["NOTICE","invalid: error message"]""", NoticeResult.invalid("error message").toJson())
    }

    @Test
    fun `should return a NoticeResult if the authors count is invalid`() {
        val authorId = "32b9055fabe28c51641bd0230096258f7d6e517910bf99fd3425d1ae507d"
        val filter = EventFilter(
            authors = (1..EventFilterValidator.MAX_AUTHORS_COUNT + 1).map {
                "$authorId$it"
            }.toSet()
        )
        assertEquals("invalid: pubkey count must be less than or equal to 40", EventFilterValidator.validate(setOf(filter))?.message)
    }

    @Test
    fun `should return a NoticeResult if the authors length is invalid`() {
        val filter = EventFilter(
            authors = setOf("32b9055fabe28c51641")
        )
        assertEquals("invalid: pubkey size must be greater than or equal to 20", EventFilterValidator.validate(setOf(filter))?.message)
    }

    @Test
    fun `should return a NoticeResult if the tags count is invalid`() {
        val filter = EventFilter(
            tags = mapOf(
                "key1" to (1..EventFilterValidator.MAX_TAGS_COUNT + 1).map {
                    "tagValue$it"
                }.toSet()
            )
        )
        assertEquals(
            "invalid: tags count must be less than or equal to 20",
            EventFilterValidator.validate(setOf(filter))?.message
        )
    }

    @Test
    fun `should return a NoticeResult if the ids count is invalid`() {
        val id = "55b9055fabe28c51641bd0230096258f7d6e517910bf99fd3425d1a55"
        val filter = EventFilter(
            ids = (1..EventFilterValidator.MAX_IDS_COUNT + 1).map {
                "$id$it"
            }.toSet()
        )
        assertEquals("invalid: ids count must be less than or equal to 40", EventFilterValidator.validate(setOf(filter))?.message)
    }

    @Test
    fun `should return a NoticeResult if the ids length is invalid`() {
        val filter = EventFilter(
            ids = setOf("55b9055fabe28c51641")
        )
        assertEquals("invalid: id size must be greater than or equal to 20", EventFilterValidator.validate(setOf(filter))?.message)
    }
}
