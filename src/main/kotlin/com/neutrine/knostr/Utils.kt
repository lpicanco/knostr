package com.neutrine.knostr

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micronaut.websocket.WebSocketSession
import kotlinx.coroutines.future.await
import kotlinx.coroutines.time.withTimeout
import java.math.BigInteger
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.CompletableFuture

object Utils {
    fun hexToBytes(s: String): ByteArray {
        val len = s.length
        val buf = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            buf[i / 2] = ((s[i].digitToInt(16) shl 4) + s[i + 1].digitToInt(16)).toByte()
        }
        return buf
    }

    val objectMapper = jacksonObjectMapper()
}

fun ByteArray.toBigInteger() = BigInteger(1, this)

fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

fun ByteArray.toSha256(): ByteArray {
    return MessageDigest.getInstance("SHA-256").digest(this)
}

fun WebSocketSession.putRemoteAddress(remoteAddress: String) {
    attributes.put("remoteAddress", remoteAddress)
}

fun WebSocketSession.getRemoteAddress(): String? {
    return attributes.getValue("remoteAddress")?.toString()
}

fun String.toSha256(): ByteArray = toByteArray().toSha256()

suspend fun <T> CompletableFuture<T>.await(timeout: Duration): T? {
    var result: T?
    withTimeout(timeout) {
        result = await()
    }

    return result
}
