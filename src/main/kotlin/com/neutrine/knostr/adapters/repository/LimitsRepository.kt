package com.neutrine.knostr.adapters.repository

import io.micronaut.cache.annotation.Cacheable
import io.micronaut.context.annotation.Value
import io.micronaut.context.env.yaml.YamlPropertySourceLoader
import io.micronaut.core.io.file.DefaultFileSystemResourceLoader
import jakarta.inject.Singleton
import mu.KotlinLogging
import kotlin.jvm.optionals.getOrElse

@Singleton
class LimitsRepository(
    @Value("\${limits.external-file-location:}") private val limitsConfigurationFile: String
) {
    private val logger = KotlinLogging.logger {}

    @Cacheable("limit-list")
    fun getLimitsConfiguration(): LimitsConfiguration {
        logger.info { "Loading limits configuration from $limitsConfigurationFile" }

        val loader = YamlPropertySourceLoader()

        val fileName = limitsConfigurationFile.substringBeforeLast(".")
        val resource = DefaultFileSystemResourceLoader()
        val propertySources = loader.load(fileName, resource).getOrElse {
            throw RuntimeException("Could not load YAML file: $limitsConfigurationFile[fileName]")
        }
        val ipBlockList = propertySources.get("block-list.ip") as? List<String> ?: emptyList()
        return LimitsConfiguration(
            LimitsConfiguration.BlockList(
                ipList = ipBlockList.toSet()
            )
        )
    }
}

data class LimitsConfiguration(
    val blockList: BlockList = BlockList()
) {
    data class BlockList(
        val ipList: Set<String> = emptySet()
    )
}
