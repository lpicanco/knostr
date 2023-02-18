package com.neutrine.knostr.domain

object EventFilterValidator {
    fun validate(filters: Set<EventFilter>): NoticeResult? {
        var noticeResult: NoticeResult? = null

        if (filters.any { it.authors.size > MAX_AUTHORS_COUNT }) {
            noticeResult = NoticeResult.invalid("pubkey count must be less than or equal to $MAX_AUTHORS_COUNT")
        } else if (filters.any { it.authors.any { a -> a.length < MIN_AUTHOR_LENGTH } }) {
            noticeResult = NoticeResult.invalid("pubkey size must be greater than or equal to $MIN_AUTHOR_LENGTH")
        } else if (filters.any { it.tags.values.any { t -> t.size > MAX_TAGS_COUNT } }) {
            noticeResult = NoticeResult.invalid("tags count must be less than or equal to $MAX_TAGS_COUNT")
        } else if (filters.any { it.ids.size > MAX_IDS_COUNT }) {
            noticeResult = NoticeResult.invalid("ids count must be less than or equal to $MAX_IDS_COUNT")
        } else if (filters.any { it.ids.any { id -> id.length < MIN_AUTHOR_LENGTH } }) {
            noticeResult = NoticeResult.invalid("id size must be greater than or equal to $MIN_ID_LENGTH")
        } else if (filters.any { it.searchKeywords.size > MAX_SEARCH_KEYWORDS_COUNT }) {
            noticeResult =
                NoticeResult.invalid("searchKeywords size must be less than or equal to $MAX_SEARCH_KEYWORDS_COUNT")
        }

        return noticeResult
    }

    const val MAX_AUTHORS_COUNT = 40
    const val MIN_AUTHOR_LENGTH = 20
    const val MAX_TAGS_COUNT = 20
    const val MAX_IDS_COUNT = 40
    const val MIN_ID_LENGTH = 20
    const val MAX_SEARCH_KEYWORDS_COUNT = 2
}
