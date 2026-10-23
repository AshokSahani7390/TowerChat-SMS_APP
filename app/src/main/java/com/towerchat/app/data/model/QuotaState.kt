package com.towerchat.app.data.model

/**
 * QuotaState tracks the estimated daily cellular SMS quota.
 * Indian carriers (Jio, Airtel, Vi) standardly cap outgoing SMS at 100/day.
 * This state is tracked strictly in-memory per session.
 */
data class QuotaState(
    val usedSmsCount: Int = 0,
    val maxDailyQuota: Int = 100
) {
    val remaining: Int
        get() = (maxDailyQuota - usedSmsCount).coerceAtLeast(0)

    val isWarning: Boolean
        get() = remaining in 1..20

    val isExhausted: Boolean
        get() = remaining <= 0

    val percentageUsed: Float
        get() = (usedSmsCount.toFloat() / maxDailyQuota.toFloat()).coerceIn(0f, 1f)
}
