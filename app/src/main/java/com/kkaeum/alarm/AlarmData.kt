package com.kkaeum.alarm

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.time.*

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hour: Int = 7, val minute: Int = 0, val days: Int = 31,
    val enabled: Boolean = true, val digits: Int = 1, val count: Int = 3,
    @ColumnInfo(defaultValue = "''") val groupName: String = "",
    @ColumnInfo(defaultValue = "'math'") val missionType: String = "math",
    @ColumnInfo(defaultValue = "1") val missionLevel: Int = 1,
    @ColumnInfo(defaultValue = "'mixed'") val choiceKind: String = "mixed",
    val quietSeconds: Int = 15, val vibrate: Boolean = true,
    @ColumnInfo(defaultValue = "'알람'") val name: String = "알람",
    @ColumnInfo(defaultValue = "''") val soundUri: String = "",
    @ColumnInfo(defaultValue = "'기본 알람음'") val soundName: String = "기본 알람음",
    @ColumnInfo(defaultValue = "0") val skipHolidays: Boolean = false,
    @ColumnInfo(defaultValue = "''") val skipDate: String = "",
    @ColumnInfo(defaultValue = "5") val snoozeMinutes: Int = 5,
    @ColumnInfo(defaultValue = "3") val snoozeLimit: Int = 3,
)
@Entity(tableName = "pause_periods") data class PausePeriod(@PrimaryKey val start: String, val end: String)
@Entity(tableName = "holidays") data class Holiday(@PrimaryKey val date: String, val name: String)
@Entity(tableName = "holiday_years") data class HolidayYear(@PrimaryKey val year: Int, val updatedAt: Long, val source: String)
@Entity(tableName = "date_rules") data class DateRule(@PrimaryKey val date: String, val force: Boolean)
@Entity(tableName = "scheduled") data class Scheduled(@PrimaryKey val alarmId: Int, val due: Long)
@Entity(tableName = "snoozes") data class Snooze(@PrimaryKey val alarmId: Int, val due: Long, val used: Int)

@Dao
interface AlarmDao {
    @Query("SELECT * FROM pause_periods ORDER BY start") fun pausesFlow(): Flow<List<PausePeriod>>
    @Query("SELECT * FROM pause_periods ORDER BY start") suspend fun pauses(): List<PausePeriod>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putPause(period: PausePeriod)
    @Query("DELETE FROM pause_periods WHERE start = :start") suspend fun deletePause(start: String)

    @Query("SELECT * FROM alarms ORDER BY hour, minute, id") fun observe(): Flow<List<Alarm>>
    @Query("SELECT * FROM alarms") suspend fun all(): List<Alarm>
    @Query("SELECT * FROM alarms WHERE id = :id") suspend fun find(id: Int): Alarm?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(alarm: Alarm): Long
    @Query("DELETE FROM alarms WHERE id = :id") suspend fun delete(id: Int)
    @Query("SELECT * FROM holidays ORDER BY date") fun holidaysFlow(): Flow<List<Holiday>>
    @Query("SELECT * FROM holidays") suspend fun holidays(): List<Holiday>
    @Query("SELECT * FROM holiday_years ORDER BY year") fun yearsFlow(): Flow<List<HolidayYear>>
    @Query("SELECT * FROM holiday_years") suspend fun years(): List<HolidayYear>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putHolidays(rows: List<Holiday>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putYear(year: HolidayYear)
    @Query("DELETE FROM holidays WHERE date LIKE :prefix") suspend fun clearYear(prefix: String)
    @Query("SELECT * FROM date_rules ORDER BY date") fun rulesFlow(): Flow<List<DateRule>>
    @Query("SELECT * FROM date_rules") suspend fun rules(): List<DateRule>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putRule(rule: DateRule)
    @Query("DELETE FROM date_rules WHERE date = :date") suspend fun deleteRule(date: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putScheduled(row: Scheduled)
    @Query("SELECT * FROM scheduled WHERE alarmId = :id") suspend fun scheduled(id: Int): Scheduled?
    @Query("DELETE FROM scheduled WHERE alarmId = :id") suspend fun clearScheduled(id: Int)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSnooze(row: Snooze)
    @Query("SELECT * FROM snoozes WHERE alarmId = :id") suspend fun snooze(id: Int): Snooze?
    @Query("SELECT * FROM snoozes") suspend fun snoozes(): List<Snooze>
    @Query("SELECT * FROM snoozes") fun snoozesFlow(): Flow<List<Snooze>>
    @Query("DELETE FROM snoozes WHERE alarmId = :id") suspend fun clearSnooze(id: Int)
}

@Database(entities = [Alarm::class, Holiday::class, HolidayYear::class, DateRule::class, Scheduled::class, Snooze::class, PausePeriod::class], version = 4, exportSchema = false)
abstract class AlarmDatabase : RoomDatabase() {
    abstract fun alarms(): AlarmDao
    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN missionType TEXT NOT NULL DEFAULT 'math'")
                db.execSQL("ALTER TABLE alarms ADD COLUMN missionLevel INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE alarms ADD COLUMN choiceKind TEXT NOT NULL DEFAULT 'mixed'")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN groupName TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE TABLE IF NOT EXISTS pause_periods (start TEXT NOT NULL PRIMARY KEY, end TEXT NOT NULL)")
            }
        }
        val MIGRATION = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("name TEXT NOT NULL DEFAULT '알람'", "soundUri TEXT NOT NULL DEFAULT ''",
                    "soundName TEXT NOT NULL DEFAULT '기본 알람음'", "skipHolidays INTEGER NOT NULL DEFAULT 0",
                    "skipDate TEXT NOT NULL DEFAULT ''", "snoozeMinutes INTEGER NOT NULL DEFAULT 5",
                    "snoozeLimit INTEGER NOT NULL DEFAULT 3").forEach { db.execSQL("ALTER TABLE alarms ADD COLUMN $it") }
                db.execSQL("CREATE TABLE IF NOT EXISTS holidays (date TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS holiday_years (year INTEGER NOT NULL PRIMARY KEY, updatedAt INTEGER NOT NULL, source TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS date_rules (date TEXT NOT NULL PRIMARY KEY, force INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS scheduled (alarmId INTEGER NOT NULL PRIMARY KEY, due INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS snoozes (alarmId INTEGER NOT NULL PRIMARY KEY, due INTEGER NOT NULL, used INTEGER NOT NULL)")
            }
        }
        @Volatile private var instance: AlarmDatabase? = null
        fun get(context: Context): AlarmDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(AlarmStorage.context(context), AlarmDatabase::class.java, "kkaeum.db")
                .addMigrations(MIGRATION, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
        }
    }
}

data class Skipped(val date: LocalDate, val reason: String)
data class NextOccurrence(val time: ZonedDateTime, val skipped: List<Skipped>)
object NextAlarm {
    fun at(alarm: Alarm, now: ZonedDateTime): ZonedDateTime = calculate(alarm, now).time
    fun calculate(alarm: Alarm, now: ZonedDateTime, holidays: Map<String, String> = emptyMap(),
        rules: Map<String, Boolean> = emptyMap(), pauses: List<PausePeriod> = emptyList()): NextOccurrence {
        require(alarm.hour in 0..23 && alarm.minute in 0..59 && alarm.days in 0..127)
        val skipped = mutableListOf<Skipped>()
        for (offset in 0..1095) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            val candidate = date.atTime(alarm.hour, alarm.minute).atZone(now.zone)
            if (!candidate.isAfter(now)) continue
            val key = date.toString()
            val selected = alarm.days == 0 || alarm.days and (1 shl (date.dayOfWeek.value - 1)) != 0
            if (!selected && rules[key] != true) continue
            val reason = when {
                pauses.any { key >= it.start && key <= it.end } -> "기간 건너뛰기"
                alarm.skipDate == key -> "이번만 건너뛰기"
                rules[key] == false -> "개인 휴일"
                rules[key] == true -> null
                alarm.skipHolidays -> holidays[key]
                else -> null
            }
            if (reason != null) { skipped += Skipped(date, reason); continue }
            return NextOccurrence(candidate, skipped)
        }
        error("향후 3년 안에 실행할 날짜가 없습니다.")
    }
}
