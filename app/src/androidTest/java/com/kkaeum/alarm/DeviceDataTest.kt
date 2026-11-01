package com.kkaeum.alarm

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceDataTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    @Test fun versionOneMigrationPreservesExistingAlarm() = runBlocking {
        val filename = "migration-check.db"
        context.deleteDatabase(filename)
        context.openOrCreateDatabase(filename, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS alarms (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, hour INTEGER NOT NULL, minute INTEGER NOT NULL, days INTEGER NOT NULL, enabled INTEGER NOT NULL, digits INTEGER NOT NULL, count INTEGER NOT NULL, quietSeconds INTEGER NOT NULL, vibrate INTEGER NOT NULL)")
            db.execSQL("INSERT INTO alarms VALUES (42, 8, 35, 31, 1, 2, 4, 20, 1)")
            db.version = 1
        }
        val db = Room.databaseBuilder(context, AlarmDatabase::class.java, filename).addMigrations(AlarmDatabase.MIGRATION).build()
        try {
            val alarm = db.alarms().find(42)!!
            assertEquals(8, alarm.hour); assertEquals(35, alarm.minute); assertEquals(2, alarm.digits)
            assertEquals(4, alarm.count); assertEquals("기본 알람음", alarm.soundName)
            assertFalse(alarm.skipHolidays); assertEquals(3, alarm.snoozeLimit)
            db.alarms().putRule(DateRule("2026-12-25", true))
            assertEquals(true, db.alarms().rules().single().force)
            db.alarms().putSnooze(Snooze(42, 12345, 2))
            assertEquals(2, db.alarms().snooze(42)!!.used)
        } finally { db.close(); context.deleteDatabase(filename) }
    }
    @Test fun bundledHolidaysMatchReviewedDates() {
        val expected = mapOf(
            2026 to "01-01 02-16 02-17 02-18 03-01 03-02 05-01 05-05 05-24 05-25 06-03 06-06 07-17 08-15 08-17 09-24 09-25 09-26 10-03 10-05 10-09 12-25",
            2027 to "01-01 02-06 02-07 02-08 02-09 03-01 05-01 05-03 05-05 05-13 06-06 07-17 07-19 08-15 08-16 09-14 09-15 09-16 10-03 10-04 10-09 10-11 12-25 12-27")
        expected.forEach { (year, dates) ->
            val text = context.assets.open("holidays-$year.json").bufferedReader().use { it.readText() }
            assertEquals(dates.split(" ").map { "$year-$it" }.toSet(), HolidayParser.parse(text, year).map { it.date }.toSet())
        }
    }
    @Test fun malformedHolidayDataCannotReplaceCache() {
        assertTrue(runCatching { HolidayParser.parse("{}", 2026) }.isFailure)
        val text = context.assets.open("holidays-2026.json").bufferedReader().use { it.readText() }
        assertTrue(runCatching { HolidayParser.parse(text, 2027) }.isFailure)
        assertTrue(runCatching { HolidayParser.parse(text.replace("2026-12-25", "2026-12-99"), 2026) }.isFailure)
    }
    @Test fun soundAndOptionsRoundTripForPreview() {
        val alarm = Alarm(id = 42, name = "평일 출근", soundUri = "content://media/external/audio/media/1",
            skipHolidays = true, skipDate = "2026-12-25", snoozeLimit = 5, snoozeMinutes = 10)
        assertEquals(alarm, AlarmCodec.decode(AlarmCodec.encode(alarm)))
    }
    @Test fun publicFeedRefreshWorksOnDevice() = runBlocking {
        HolidayRepository(context).refresh()
        val dao = AlarmDatabase.get(context).alarms()
        assertTrue(dao.years().filter { it.year in 2026..2027 }.all { it.updatedAt > 0 })
        assertTrue(dao.holidays().any { it.date == "2027-02-09" && it.name.contains("대체공휴일") })
    }
}
