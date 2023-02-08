package com.neutrine.knostr.adapters.repository.jdbc

import com.neutrine.knostr.adapters.repository.EventStore
import com.neutrine.knostr.domain.Event
import com.neutrine.knostr.domain.EventFilter
import io.micronaut.context.annotation.Primary
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.runtime.JdbcOperations
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.micronaut.tracing.annotation.ContinueSpan
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
) : EventStore, CoroutineCrudRepository<Event, String> {

    private val filterDispatcher = Dispatchers.IO.limitedParallelism(30)

    @ContinueSpan
    override suspend fun filter(filters: Set<EventFilter>): List<Event> {
        return filters.asFlow().flatMapMerge(5) {
            flow { emit(filter(it)) }
        }.toList().flatten()
    }

    @Query("UPDATE event SET deleted = true WHERE pubkey = :pubkey AND event_id in (:eventIds)")
    @ContinueSpan
    abstract override suspend fun deleteAll(pubkey: String, eventIds: Set<String>)

    @ContinueSpan
    override suspend fun deleteOldestOfKind(pubkey: String, kind: Int) {
        val sql = "UPDATE event SET deleted = true WHERE pubkey = ? AND kind = ? AND deleted = false AND created_at < (SELECT MAX(created_at) FROM event WHERE pubkey = ? AND kind = ? AND deleted = false)"

        withContext(Dispatchers.IO) {
            transactionManager.executeWrite() {
                jdbcOperations.prepareStatement(sql) { stmt ->
                    stmt.setString(1, pubkey)
                    stmt.setInt(2, kind)
                    stmt.setString(3, pubkey)
                    stmt.setInt(4, kind)

                    stmt.executeUpdate()
                }
            }
        }
    }

    private suspend fun filter(filter: EventFilter): List<Event> {
        val predicates = mutableListOf("deleted = false")
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

        if (filter.searchKeywords.isNotEmpty()) {
            predicates.add(
                filter.searchKeywords.joinToString(" AND ", prefix = "(", postfix = ")") { "content ~* ?" }
            )

            parameters.addAll(filter.searchKeywords.map { it })
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

        val predicatesSql = predicates.joinToString(" AND ", prefix = "WHERE ")
        val query = "SELECT * FROM event $predicatesSql ORDER BY created_at DESC LIMIT ? "

        parameters.add(minOf(10_000, filter.limit))

        return withContext(filterDispatcher) {
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
