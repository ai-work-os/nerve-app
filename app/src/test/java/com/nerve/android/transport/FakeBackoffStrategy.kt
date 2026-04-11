package com.nerve.android.transport

class FakeBackoffStrategy(
    private val delayMs: Long = 0,
) : BackoffStrategy {
    var invocationCount: Int = 0
        private set

    override fun nextDelayMillis(attempt: Int): Long {
        invocationCount += 1
        return delayMs
    }
}
