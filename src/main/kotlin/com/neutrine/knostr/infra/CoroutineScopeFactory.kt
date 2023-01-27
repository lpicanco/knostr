package com.neutrine.knostr.infra

import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Factory
class CoroutineScopeFactory {

    @Singleton
    @Primary
    fun coroutineScopeOnIO() = CoroutineScope(Dispatchers.IO.limitedParallelism(60) + SupervisorJob())

    @Singleton
    @Named(COROUTINE_MESSAGE_HANDLER)
    fun coroutineMessageHandler() = CoroutineScope(Dispatchers.IO.limitedParallelism(60) + SupervisorJob())

    companion object {
        const val COROUTINE_MESSAGE_HANDLER = "coroutineMessageHandler"
    }
}
