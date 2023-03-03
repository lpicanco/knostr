package com.neutrine.knostr.infra

import com.neutrine.knostr.adapters.repository.LimitsConfiguration
import com.neutrine.knostr.adapters.repository.LimitsRepository
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class LimitsServiceTest {

    @MockK
    private lateinit var limitsRepository: LimitsRepository
    private lateinit var limitsService: LimitsService

    private val blockedIp = "192.168.1.403"

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        every { limitsRepository.getLimitsConfiguration() } returns LimitsConfiguration(
            blockList = LimitsConfiguration.BlockList(
                setOf(blockedIp)
            )
        )

        limitsService = LimitsService(true, limitsRepository)
    }

    @Test
    fun `should return true if the ip is blocked`() {
        val result = limitsService.isIpBlocked(blockedIp)
        assertTrue(result)
    }

    @Test
    fun `should return false if the ip is not blocked`() {
        val result = limitsService.isIpBlocked("192.168.1.42")
        assertFalse(result)
    }

    @Test
    fun `should return false if the ip is null`() {
        val result = limitsService.isIpBlocked(null)
        assertFalse(result)
    }

    @Test
    fun `should return false if disabled`() {
        val result = LimitsService(enabled = false, limitsRepository).isIpBlocked(blockedIp)
        assertFalse(result)
    }
}
