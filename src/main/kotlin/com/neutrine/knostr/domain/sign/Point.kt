package com.neutrine.knostr.domain.sign

import com.neutrine.knostr.toBigInteger
import com.neutrine.knostr.toSha256
import java.lang.System.arraycopy
import java.math.BigInteger

@Suppress("LocalVariableName")
class Point(val x: BigInteger?, val y: BigInteger?) {
    private val pair: Pair<BigInteger?, BigInteger?> = x to y

    val isInfinite: Boolean = pair.first == null || pair.second == null

    fun add(P: Point?): Point? {
        return add(this, P)
    }

    fun hasEvenY(): Boolean {
        return if (y == null) false else y.mod(BigInteger.TWO).compareTo(BigInteger.ZERO) == 0
    }

    companion object {
        val p = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F", 16)
        val n = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16)
        val G = Point(
            BigInteger("79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798", 16),
            BigInteger("483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8", 16)
        )
        private val INFINITY_POINT = Point(null, null)

        fun add(P1: Point?, P2: Point?): Point? {
            if (P1 != null && P2 != null && P1.isInfinite && P2.isInfinite) {
                return INFINITY_POINT
            }
            if (P1 == null || P1.isInfinite) {
                return P2
            }
            if (P2 == null || P2.isInfinite) {
                return P1
            }
            if (P1.x == P2.x && P1.y != P2.y) {
                return INFINITY_POINT
            }
            val lam: BigInteger = if (P1 == P2) {
                val base = P2.y!!.multiply(BigInteger.TWO)
                BigInteger.valueOf(3L).multiply(P1.x).multiply(P1.x).multiply(
                    base.modPow(
                        p.subtract(BigInteger.TWO),
                        p
                    )
                ).mod(p)
            } else {
                val base = P2.x!!.subtract(P1.x)
                P2.y!!.subtract(P1.y).multiply(
                    base.modPow(
                        p.subtract(BigInteger.TWO),
                        p
                    )
                ).mod(p)
            }
            val x3 = lam.multiply(lam).subtract(P1.x).subtract(P2.x).mod(p)
            return Point(x3, lam.multiply(P1.x!!.subtract(x3)).subtract(P1.y).mod(p))
        }

        fun mul(point: Point, n: BigInteger): Point? {
            var P: Point? = point
            var R: Point? = null
            for (i in 0..255) {
                if (n.shiftRight(i).and(BigInteger.ONE) > BigInteger.ZERO) {
                    R = add(R, P)
                }
                P = add(P, P)
            }
            return R
        }

        fun taggedHash(tag: String, msg: ByteArray): ByteArray {
            val tagHash = tag.toSha256()
            val len = tagHash.size * 2 + msg.size
            val buf = ByteArray(len)
            arraycopy(tagHash, 0, buf, 0, tagHash.size)
            arraycopy(tagHash, 0, buf, tagHash.size, tagHash.size)
            arraycopy(msg, 0, buf, tagHash.size * 2, msg.size)
            return buf.toSha256()
        }

        fun liftX(b: ByteArray): Point? {
            val x = b.toBigInteger()
            if (x >= p) {
                return null
            }
            val y_sq = x.modPow(BigInteger.valueOf(3L), p).add(BigInteger.valueOf(7L)).mod(p)
            val y = y_sq.modPow(p.add(BigInteger.ONE).divide(BigInteger.valueOf(4L)), p)
            return if (y.modPow(BigInteger.TWO, p).compareTo(y_sq) != 0) {
                null
            } else {
                Point(
                    x,
                    if (y.and(BigInteger.ONE)
                        .compareTo(BigInteger.ZERO) == 0
                    ) y else p.subtract(y)
                )
            }
        }
    }
}
