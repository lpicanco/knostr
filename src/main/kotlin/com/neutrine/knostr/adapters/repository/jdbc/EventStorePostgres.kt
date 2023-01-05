package com.neutrine.knostr.adapters.repository.jdbc

import com.neutrine.knostr.adapters.repository.EventStore
import com.neutrine.knostr.domain.Event
import com.neutrine.knostr.domain.EventFilter
import io.micronaut.context.annotation.Primary
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.runtime.JdbcOperations
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.transaction.SynchronousTransactionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import java.sql.Connection

@JdbcRepository(dialect = Dialect.POSTGRES)
@Primary
abstract class EventStorePostgres(
    private val jdbcOperations: JdbcOperations,
    private val transactionManager: SynchronousTransactionManager<Connection>
) : EventStore, CrudRepository<Event, String> {

    override suspend fun filter(filters: Set<EventFilter>): List<Event> {
        return filters.asFlow().flatMapMerge {
            flow { emit(filter(it)) }
        }.toList().flatten()
    }

    private suspend fun filter(filter: EventFilter): List<Event> {
        val predicates = mutableListOf<String>()
        val parameters = mutableListOf<Any>()

        if (filter.since != null) {
            predicates.add("created_at >= ?")
            parameters.add(filter.since)
        }

        if (filter.until != null) {
            predicates.add("created_at <= ?")
            parameters.add(filter.until)
        }

        if (filter.ids.isNotEmpty()) {
            predicates.add(
                filter.ids.joinToString(" OR ", prefix = "(", postfix = ")") { "event_id like ?" }
            )

            parameters.addAll(filter.ids.map { "$it%" })
        }

        if (filter.authors.isNotEmpty()) {
            predicates.add(
                filter.authors.joinToString(" OR ", prefix = "(", postfix = ")") { "pubkey like ?" }
            )

            parameters.addAll(filter.authors.map { "$it%" })
        }

        if (filter.kinds.isNotEmpty()) {
            predicates.add("kind IN (${filter.kinds.joinToString(",") { "?" }})")
            parameters.addAll(filter.kinds)
        }

        if (filter.tags.isNotEmpty()) {
            filter.tags.filterValues { it.isNotEmpty() }.forEach { tag ->
                predicates.add(
                    tag.value.joinToString(" OR ", prefix = "(", postfix = ")") {
                        parameters.add("""[["${tag.key}", "$it"]]""")
                        """tags @> ?::jsonb"""
                    }
                )
            }
        }

        val predicatesSql = if (predicates.isNotEmpty()) {
            predicates.joinToString(" AND ", prefix = "WHERE ")
        } else ""

        val query = "SELECT * FROM event $predicatesSql ORDER BY created_at DESC LIMIT ? "

        parameters.add(minOf(10_000, filter.limit))

        return withContext(Dispatchers.IO) {
            transactionManager.executeRead {
                jdbcOperations.prepareStatement(query) { stmt ->
                    parameters.forEachIndexed { index, parameter ->
                        stmt.setObject(index + 1, parameter)
                    }

                    val resultSet = stmt.executeQuery()
                    jdbcOperations.entityStream(resultSet, Event::class.java)
                }.toList()
            }
        }
    }
}
