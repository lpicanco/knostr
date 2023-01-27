package com.neutrine.knostr.adapters.ws

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.neutrine.knostr.domain.Event
import com.neutrine.knostr.domain.EventFilter
import com.neutrine.knostr.domain.EventService
import com.neutrine.knostr.domain.SubscriptionService
import com.neutrine.knostr.infra.CoroutineScopeFactory.Companion.COROUTINE_MESSAGE_HANDLER
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Produces
import io.micronaut.tracing.annotation.NewSpan
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.OnClose
import io.micronaut.websocket.annotation.OnError
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
import jakarta.inject.Named
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import mu.KotlinLogging
import net.logstash.logback.argument.StructuredArguments.kv
import org.reactivestreams.Publisher

@ServerWebSocket("/")
class Relay(
    private val eventService: EventService,
    private val subscriptionService: SubscriptionService,
    private val objectMapper: ObjectMapper,
    @Named(COROUTINE_MESSAGE_HANDLER)
    private val coroutineScope: CoroutineScope
) {
    private val logger = KotlinLogging.logger {}

    @OnOpen
    @Produces("application/nostr+json")
    fun onOpen(session: WebSocketSession?, @Header accept: String?): String? {
        return when {
            session != null -> null
            accept == "application/nostr+json" -> NIP11_DATA
            else -> "Use a Nostr client or Websocket client to connect"
        }
    }

    @OnMessage(maxPayloadLength = Int.MAX_VALUE)
    @NewSpan("onMessage")
    fun onMessage(message: String, session: WebSocketSession): Publisher<Unit> {
        if (message.length > 65536) {
            logger.warn("Message too long", kv("size", message.length))
            session.sendSync("""["NOTICE","invalid: payload size must be less than or equal to 65536"]""")
            return Publishers.empty()
        }

        val job = coroutineScope.launch(errorHandler) {
            val messageArguments = objectMapper.readTree(message)
            if (messageArguments.size() < 2) {
                throw IllegalArgumentException("Invalid message: $message")
            }

            when (messageArguments[0].asText()) {
                "REQ" -> subscribe(messageArguments[1].asText(), messageArguments.drop(2), session)
                "EVENT" -> processEvent(messageArguments[1], session)
                "CLOSE" -> unsubscribe(messageArguments[1].asText(), session)
                else -> throw IllegalArgumentException("Unsupported message: $message")
            }
        }

        return Publishers.fromCompletableFuture(job.asCompletableFuture())
    }

    @OnError
    fun onError(session: WebSocketSession, t: Throwable) {
        logger.error("Unexpected error", kv("sessionId", session.id), t)

        if (session.isOpen) {
            session.close()
        }
    }

    private suspend fun subscribe(subscriptionId: String, filterNodes: List<JsonNode>, session: WebSocketSession) {
        val filters = filterNodes.map { jsonNode ->
            val filter = objectMapper.treeToValue(jsonNode, EventFilter::class.java)

            val tags = jsonNode.fields().asSequence()
                .filter { it.key.startsWith("#") }
                .map { it.key.substringAfter("#") to it.value.map { it.asText() }.toSet() }
                .toMap()

            filter.copy(tags = tags)
        }.toSet()

        subscriptionService.subscribe(subscriptionId, session, filters)
        logger.info("subscribe", kv("subscriptionId", subscriptionId), kv("filters", filters))
    }

    private suspend fun processEvent(eventNode: JsonNode, session: WebSocketSession) {
        val event = objectMapper.treeToValue(eventNode, Event::class.java)
        val result = eventService.save(event, session)

        if (logger.isDebugEnabled) {
            logger.debug("processEvent", kv("event", event), kv("result", result), kv("sessionId", session.id))
        } else {
            logger.info("processEvent", kv("eventId", event.id), kv("result", result), kv("sessionId", session.id))
        }
    }

    private fun unsubscribe(subscriptionId: String, session: WebSocketSession) {
        subscriptionService.unsubscribe(subscriptionId, session)
        logger.info("unsubscribe", kv("subscriptionId", subscriptionId), kv("sessionId", session.id))
    }

    @OnClose
    fun onClose(session: WebSocketSession) {
        subscriptionService.unsubscribeSocketSession(session)
        logger.info("close", kv("sessionId", session.id))
    }

    val errorHandler = CoroutineExceptionHandler { _, exception ->
        logger.error("Unexpected error", exception)
    }

    companion object {
        val NIP11_DATA = Relay::class.java.getResource("/nip-11.json").readText()
    }
}
