package com.towerchat.app

import com.towerchat.app.data.model.CallLogEntry
import com.towerchat.app.data.model.CallLogGrouper
import com.towerchat.app.data.model.CallType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class CallLogGroupingTest {

    private fun getTimestampForDay(dayOffset: Int, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, dayOffset)
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    @Test
    fun testEmptyCallListReturnsEmpty() {
        val grouped = CallLogGrouper.groupCalls(emptyList())
        assertTrue(grouped.isEmpty())
    }

    @Test
    fun testSingleCallEntryReturnsSingleGroup() {
        val time = System.currentTimeMillis()
        val entry = CallLogEntry(
            id = 1L,
            number = "+919876543210",
            cachedName = "Alice",
            type = CallType.OUTGOING,
            timestamp = time,
            durationSeconds = 120,
            phoneAccountId = "sim1"
        )

        val grouped = CallLogGrouper.groupCalls(listOf(entry))
        assertEquals(1, grouped.size)
        val group = grouped[0]
        assertEquals(1, group.callCount)
        assertEquals("+919876543210", group.number)
        assertEquals("Alice", group.displayName)
        assertEquals(CallType.OUTGOING, group.latestType)
        assertEquals(120L, group.totalDurationSeconds)
        assertEquals(time, group.latestTimestamp)
    }

    @Test
    fun testConsecutiveCallsToSameNumberOnSameDayAreGrouped() {
        val time1 = getTimestampForDay(0, 10, 30) // Today 10:30 AM
        val time2 = getTimestampForDay(0, 10, 15) // Today 10:15 AM
        val time3 = getTimestampForDay(0, 9, 0)   // Today 9:00 AM

        val entries = listOf(
            CallLogEntry(1L, "+919876543210", "Alice", CallType.MISSED, time1, 0, "sim1"),
            CallLogEntry(2L, "+919876543210", "Alice", CallType.INCOMING, time2, 60, "sim1"),
            CallLogEntry(3L, "+919876543210", "Alice", CallType.OUTGOING, time3, 40, "sim1")
        )

        val grouped = CallLogGrouper.groupCalls(entries)
        assertEquals(1, grouped.size)

        val group = grouped[0]
        assertEquals(3, group.callCount)
        assertEquals(CallType.MISSED, group.latestType) // Latest call type preserved
        assertEquals(time1, group.latestTimestamp) // Latest timestamp preserved
        assertEquals(100L, group.totalDurationSeconds) // Sum of 0 + 60 + 40
        assertEquals(3, group.entries.size)
    }

    @Test
    fun testDifferentNumbersAreNotMerged() {
        val time1 = getTimestampForDay(0, 11, 0)
        val time2 = getTimestampForDay(0, 10, 0)

        val entries = listOf(
            CallLogEntry(1L, "+919876543210", "Alice", CallType.INCOMING, time1, 45, "sim1"),
            CallLogEntry(2L, "+919999999999", "Bob", CallType.OUTGOING, time2, 30, "sim1")
        )

        val grouped = CallLogGrouper.groupCalls(entries)
        assertEquals(2, grouped.size)
        assertEquals("Alice", grouped[0].displayName)
        assertEquals("Bob", grouped[1].displayName)
    }

    @Test
    fun testSameNumberOnDifferentDaysAreNotMerged() {
        val todayTime = getTimestampForDay(0, 14, 0)
        val yesterdayTime = getTimestampForDay(-1, 14, 0)

        val entries = listOf(
            CallLogEntry(1L, "+919876543210", "Alice", CallType.OUTGOING, todayTime, 50, "sim1"),
            CallLogEntry(2L, "+919876543210", "Alice", CallType.INCOMING, yesterdayTime, 80, "sim1")
        )

        val grouped = CallLogGrouper.groupCalls(entries)
        assertEquals(2, grouped.size)
        assertEquals(1, grouped[0].callCount)
        assertEquals(1, grouped[1].callCount)
        assertEquals(todayTime, grouped[0].latestTimestamp)
        assertEquals(yesterdayTime, grouped[1].latestTimestamp)
    }

    @Test
    fun testUncachedNumberUsesRawNumberAsDisplayName() {
        val time = System.currentTimeMillis()
        val entry = CallLogEntry(
            id = 5L,
            number = "+911234567890",
            cachedName = null,
            type = CallType.INCOMING,
            timestamp = time,
            durationSeconds = 10,
            phoneAccountId = null
        )

        val grouped = CallLogGrouper.groupCalls(listOf(entry))
        assertEquals("+911234567890", grouped[0].displayName)
    }
}
