package com.neutrine.knostr.adapters.ws

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.neutrine.knostr.domain.Event
import com.neutrine.knostr.domain.EventFilter
import com.neutrine.knostr.domain.EventFilterValidator
import com.neutrine.knostr.domain.EventService
import com.neutrine.knostr.domain.NoticeResult
import com.neutrine.knostr.domain.SubscriptionService
import com.neutrine.knostr.getRemoteAddress
import com.neutrine.knostr.infra.LimitsService
import com.neutrine.knostr.putRemoteAddress
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Produces
import io.micronaut.http.server.util.HttpClientAddressResolver
import io.micronaut.tracing.annotation.NewSpan
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.OnClose
import io.micronaut.websocket.annotation.OnError
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactive.publish
import mu.KotlinLogging
import net.logstash.logback.argument.StructuredArguments.kv
import org.reactivestreams.Publisher

@ServerWebSocket("/")
class Relay(
    private val eventService: EventService,
    private val subscriptionService: SubscriptionService,
    private val objectMapper: ObjectMapper,
    private val httpClientAddressResolver: HttpClientAddressResolver,
    private val limitsService: LimitsService
) {
    private val logger = KotlinLogging.logger {}

    @OnOpen
    @Produces("application/nostr+json")
    fun onOpen(session: WebSocketSession?, @Header accept: String?, request: HttpRequest<*>?): String? {
        if (session != null && request != null) {
            val remoteAddress = httpClientAddressResolver.resolve(request)
            session.putRemoteAddress(remoteAddress)
            logger.info("onOpen", kv("sessionId", session.id))
        }

        return when {
            session != null -> null
            accept == "application/nostr+json" -> NIP11_DATA
            else -> "Use a Nostr client or Websocket client to connect"
        }
    }

    @OnMessage(maxPayloadLength = 65536)
    @NewSpan("onMessage")
    fun onMessage(message: String, session: WebSocketSession): Publisher<Unit> = publish {
        val ip = session.getRemoteAddress()
        if (limitsService.isIpBlocked(ip)) {
            session.sendAsync(NoticeResult.blocked("IP blocked").toJson())
            return@publish
        }

        val messageArguments = objectMapper.readTree(message)
        if (messageArguments.size() < 2) {
            session.sendSync(NoticeResult("Unsupported message: $message").toJson())
            return@publish
        }

        when (val messageType = messageArguments[0].asText()) {
            "REQ" -> subscribe(messageArguments[1].asText(), messageArguments.drop(2), session)
            "EVENT" -> processEvent(messageArguments[1], session)
            "CLOSE" -> unsubscribe(messageArguments[1].asText(), session)
            "PING" -> session.sendSync(NoticeResult("PONG").toJson())
            else -> session.sendSync(NoticeResult("Unsupported message: $messageType").toJson())
        }
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

        val validationError = EventFilterValidator.validate(filters)
        if (validationError != null) {
            session.sendAsync(validationError.toJson()).await()
            logger.warn("subscribe", kv("invalidMessage", validationError.message), kv("subscriptionId", subscriptionId), kv("filters", filters))
            return
        }

        subscriptionService.subscribe(subscriptionId, session, filters)
        logger.info("subscribe", kv("subscriptionId", subscriptionId), kv("sessionId", session.id), kv("filters", filters), kv("remoteAddress", session.getRemoteAddress()))
    }

    private suspend fun processEvent(eventNode: JsonNode, session: WebSocketSession) {
        val event = objectMapper.treeToValue(eventNode, Event::class.java)
        val result = eventService.save(event, session)

        if (logger.isDebugEnabled) {
            logger.debug("processEvent", kv("event", event), kv("result", result), kv("sessionId", session.id), kv("remoteAddress", session.getRemoteAddress()))
        } else {
            logger.info("processEvent", kv("eventId", event.id), kv("result", result), kv("sessionId", session.id), kv("remoteAddress", session.getRemoteAddress()))
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

    companion object {
        val NIP11_DATA = Relay::class.java.getResource("/nip-11.json").readText()
    }
}
