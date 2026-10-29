package com.colinmoerbe.poecompanion.acquisition.overrides

import com.colinmoerbe.poecompanion.acquisition.overrides.internal.DropRateOverridePersistencePort
import com.colinmoerbe.poecompanion.acquisition.overrides.persistence.DropRateOverridePersistenceAdapter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.boot.test.context.TestConfiguration
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Replaces only the persistence-port injection in this test context.
 * Both confirmations must arrive inside their service transactions before either reaches PostgreSQL.
 */
@TestConfiguration(proxyBeanMethods = false)
internal class ConcurrentConfirmationTestConfiguration {
    @Bean
    @Primary
    fun concurrentConfirmationPort(delegate: DropRateOverridePersistenceAdapter): ConcurrentConfirmationPort =
        ConcurrentConfirmationPort(delegate)
}

/** A test-only gate around the production adapter, never a production synchronization hook. */
internal class ConcurrentConfirmationPort(
    private val delegate: DropRateOverridePersistenceAdapter,
) : DropRateOverridePersistencePort {
    private val barrier = AtomicReference<CyclicBarrier?>()

    fun <T> withConcurrentConfirmations(action: () -> T): T {
        check(barrier.compareAndSet(null, CyclicBarrier(2))) { "Concurrent confirmation gate is already enabled" }
        return try {
            action()
        } finally {
            barrier.set(null)
        }
    }

    override fun findCurrent(key: DropRateOverrideKey): DropRateOverride? = delegate.findCurrent(key)

    override fun confirm(confirmation: DropRateOverride, expectedRevisionId: DropRateOverrideRevisionId?) {
        // A task-start latch would permit sequential transactions. This rendezvous is *inside*
        // DropRateOverrideService.confirmForSelectedEnvironment, immediately before persistence.
        barrier.get()?.await(30, TimeUnit.SECONDS)
        delegate.confirm(confirmation, expectedRevisionId)
    }

    override fun remove(key: DropRateOverrideKey, expectedRevisionId: DropRateOverrideRevisionId) {
        delegate.remove(key, expectedRevisionId)
    }
}
