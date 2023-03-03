package com.neutrine.knostr.infra

import com.neutrine.knostr.adapters.repository.LimitsConfiguration
import com.neutrine.knostr.adapters.repository.LimitsRepository
import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton
import mu.KotlinLogging
import net.logstash.logback.argument.StructuredArguments.kv

@Singleton
class LimitsService(
    @Value("\${limits.enabled:false}") private val enabled: Boolean,
    private val limitsRepository: LimitsRepository
) {
    private val logger = KotlinLogging.logger {}

    fun isIpBlocked(ipAddress: String?): Boolean {
        return if (ipAddress != null && getConfig().blockList.ipList.contains(ipAddress)) {
            logger.info("ipBlocked", kv("ipAddress", ipAddress))
            true
        } else false
    }

    private fun getConfig(): LimitsConfiguration {
        if (enabled) {
            return limitsRepository.getLimitsConfiguration()
        }

        return EMPTY_CONFIG
    }

    companion object {
        private val EMPTY_CONFIG = LimitsConfiguration()
    }
}
