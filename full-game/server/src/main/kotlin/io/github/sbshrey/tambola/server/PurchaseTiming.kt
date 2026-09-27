package io.github.sbshrey.tambola.server

import jdk.jfr.*

/** Opt-in, fixed-field diagnostics. Never records credentials, request IDs, room IDs or SQL. */
@Name("tambola.PurchaseTiming")
@Label("Tambola purchase phases")
@Category("Tambola")
@Enabled(false)
@StackTrace(false)
internal class PurchaseTiming private constructor(@Transient private val clock: () -> Long) : Event() {
    @JvmField var friendTable = false
    @JvmField var success = false
    @JvmField @Timespan(Timespan.NANOSECONDS) var admissionNanos = 0L
    @JvmField @Timespan(Timespan.NANOSECONDS) var dispatchNanos = 0L
    @JvmField @Timespan(Timespan.NANOSECONDS) var serviceNanos = 0L
    @JvmField @Timespan(Timespan.NANOSECONDS) var poolNanos = 0L
    @JvmField var poolAcquisitions = 0
    @JvmField @Timespan(Timespan.NANOSECONDS) var allocationNanos = 0L
    @JvmField @Timespan(Timespan.NANOSECONDS) var remainderNanos = 0L
    @JvmField @Timespan(Timespan.NANOSECONDS) var totalNanos = 0L
    @Transient private val started = clock()
    @Transient private var admitted = false
    @Transient private var enteredService = false

    fun admitted() {
        admitted = true
        admissionNanos = clock() - started
    }

    // The service is synchronous inside Dispatchers.IO: this scope cannot cross a suspension.
    fun <T> service(block: () -> T): T {
        enteredService = true
        val serviceStarted = clock()
        dispatchNanos = serviceStarted - started - admissionNanos
        val previous = current.get()
        current.set(this)
        try { return block() }
        finally {
            serviceNanos = clock() - serviceStarted
            if (previous == null) current.remove() else current.set(previous)
        }
    }

    fun finish(success: Boolean) {
        this.success = success
        totalNanos = clock() - started
        if (!admitted) admissionNanos = totalNanos
        else if (!enteredService) dispatchNanos = totalNanos - admissionNanos
        remainderNanos = totalNanos - admissionNanos - dispatchNanos - serviceNanos
        end()
        commit()
    }

    companion object {
        private val eventType by lazy { EventType.getEventType(PurchaseTiming::class.java) }
        private val current = ThreadLocal<PurchaseTiming>()

        fun start(friendTable: Boolean, clock: () -> Long = System::nanoTime): PurchaseTiming? {
            if (!eventType.isEnabled) return null
            return PurchaseTiming(clock).also { it.friendTable = friendTable; it.begin() }
        }

        fun <T> pool(block: () -> T): T {
            val timing = current.get() ?: return block()
            val start = timing.clock()
            try { return block() }
            finally { timing.poolNanos += timing.clock() - start; timing.poolAcquisitions++ }
        }

        fun <T> allocation(block: () -> T): T {
            val timing = current.get() ?: return block()
            val start = timing.clock()
            try { return block() }
            finally { timing.allocationNanos += timing.clock() - start }
        }
    }
}
