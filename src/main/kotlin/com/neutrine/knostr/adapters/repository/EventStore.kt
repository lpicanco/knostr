package com.neutrine.knostr.adapters.repository

import com.neutrine.knostr.domain.Event
import com.neutrine.knostr.domain.EventFilter

interface EventStore {
    suspend fun save(event: Event)
    suspend fun existsById(id: String): Boolean
    suspend fun filter(filters: Set<EventFilter>): List<Event>
    suspend fun deleteAll(pubkey: String, eventIds: Set<String>)
    suspend fun deleteOldestOfKind(pubkey: String, kind: Int)
}
