package com.neutrine.knostr.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.neutrine.knostr.Utils.hexToBytes
import com.neutrine.knostr.Utils.objectMapper
import com.neutrine.knostr.domain.sign.Schnorr
import com.neutrine.knostr.toHex
import com.neutrine.knostr.toSha256
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.annotation.Transient
import io.micronaut.data.annotation.TypeDef
import io.micronaut.data.model.DataType

@MappedEntity
data class Event(
    @field:Id
    @MappedProperty("event_id")
    val id: String, // 32-bytes sha256 of the serialized event data
    val pubkey: String, // 32-bytes hex-encoded public key of the event creator
    @JsonProperty("created_at")
    val createdAt: Int, // unix timestamp in seconds
    val kind: Int, // integer
    @field:TypeDef(type = DataType.JSON)
    val tags: List<List<String>>, // "e", <32-bytes hex of the id of another event>, <recommended relay URL>
    val content: String, // arbitrary string
    val sig: String // 64-bytes signature of the sha256 hash of the serialized event data, which is the same as the "id" field
) {
    private val sha256: ByteArray by lazy {
        val fields = arrayListOf(
            0,
            pubkey,
            createdAt,
            kind,
            tags,
            content
        )

        objectMapper.writeValueAsString(fields).toSha256()
    }

    @JsonIgnore
    @Transient
    fun isValid(): Boolean = hasValidId() && hasValidSignature()

    fun hasValidId(): Boolean = sha256.toHex() == id
    fun hasValidSignature(): Boolean = Schnorr.verify(sha256, hexToBytes(pubkey), hexToBytes(sig))
}
