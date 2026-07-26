package neth.iecal.curbox.utils

import neth.iecal.curbox.data.models.AppTimeConfig
import neth.iecal.curbox.data.models.TimeInterval
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class TimeGroupWindowTest {
    private fun at(dayOfWeek: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(2026, Calendar.JULY, 27, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
            while (get(Calendar.DAY_OF_WEEK) != dayOfWeek) add(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis

    @Test
    fun afterMidnightBelongsToPreviousDaysOvernightInterval() {
        val mondayOvernight = TimeInterval(22, 0, 2, 0)
        val config = AppTimeConfig(
            isEveryday = false,
            dailyIntervals = mutableMapOf(
                Calendar.MONDAY - 1 to mutableListOf(mondayOvernight)
            )
        )

        assertNotNull(config.activeWindow(at(Calendar.TUESDAY, 1, 0)))
        assertNull(config.activeWindow(at(Calendar.WEDNESDAY, 1, 0)))
    }

    @Test
    fun futureDaysOvernightIntervalDoesNotStartEarly() {
        val tuesdayOvernight = TimeInterval(22, 0, 2, 0)
        val config = AppTimeConfig(
            isEveryday = false,
            dailyIntervals = mutableMapOf(
                Calendar.TUESDAY - 1 to mutableListOf(tuesdayOvernight)
            )
        )

        assertNull(config.activeWindow(at(Calendar.TUESDAY, 1, 0)))
        assertNotNull(config.activeWindow(at(Calendar.WEDNESDAY, 1, 0)))
    }
}
