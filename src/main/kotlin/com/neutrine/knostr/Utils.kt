package com.neutrine.knostr

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.neutrine.knostr.Utils.MESSAGE_DIGEST
import java.math.BigInteger
import java.security.MessageDigest

object Utils {
    fun hexToBytes(s: String): ByteArray {
        val len = s.length
        val buf = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            buf[i / 2] = ((s[i].digitToInt(16) shl 4) + s[i + 1].digitToInt(16)).toByte()
        }
        return buf
    }

    val objectMapper = ObjectMapper().registerKotlinModule()
    val MESSAGE_DIGEST: MessageDigest = MessageDigest.getInstance("SHA-256")
}

fun ByteArray.toBigInteger() = BigInteger(1, this)

fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

fun ByteArray.toSha256(): ByteArray {
    return MESSAGE_DIGEST.digest(this)
}

fun String.toSha256(): ByteArray = toByteArray().toSha256()
