package com.neutrine.knostr.domain.sign

import com.neutrine.knostr.toBigInteger
import mu.KotlinLogging
import java.math.BigInteger

@Suppress("LocalVariableName")
object Schnorr {
    private val logger = KotlinLogging.logger {}

    fun verify(msg: ByteArray, pubkey: ByteArray, sig: ByteArray): Boolean {
        return try {
            verifySignature(msg, pubkey, sig)
        } catch (e: Exception) {
            logger.warn("Schnorr signature verification failed", e)
            false
        }
    }

    private fun verifySignature(msg: ByteArray, pubkey: ByteArray, sig: ByteArray): Boolean {
        if (msg.size != 32) {
            throw Exception("The message must be a 32-byte array.")
        }
        if (pubkey.size != 32) {
            throw Exception("The public key must be a 32-byte array.")
        }
        if (sig.size != 64) {
            throw Exception("The signature must be a 64-byte array.")
        }
        val P: Point = Point.liftX(pubkey) ?: return false
        val r: BigInteger = sig.copyOfRange(0, 32).toBigInteger()
        val s: BigInteger = sig.copyOfRange(32, 64).toBigInteger()
        if (r >= Point.p || s >= Point.n) {
            return false
        }
        val len = 32 + pubkey.size + msg.size
        val buf = ByteArray(len)
        System.arraycopy(sig, 0, buf, 0, 32)
        System.arraycopy(pubkey, 0, buf, 32, pubkey.size)
        System.arraycopy(msg, 0, buf, 32 + pubkey.size, msg.size)
        val e: BigInteger = Point.taggedHash("BIP0340/challenge", buf).toBigInteger().mod(Point.n)
        val R: Point? = Point.add(
            Point.mul(Point.G, s), Point.mul(P, Point.n.subtract(e))
        )
        return R != null && R.hasEvenY() && R.x == r
    }
}
