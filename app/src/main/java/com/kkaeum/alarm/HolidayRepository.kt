package com.kkaeum.alarm

import android.content.Context
import androidx.room.withTransaction
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.concurrent.TimeUnit

object HolidayParser {
    fun parse(text: String, year: Int): List<Holiday> {
        require(text.length < 100_000) { "공휴일 응답이 너무 큽니다." }
        val json = JSONObject(text)
        require(json.length() in 10..100) { "공휴일 목록이 비었거나 올바르지 않습니다." }
        return json.keys().asSequence().map { key ->
            require(LocalDate.parse(key).year == year) { "연도가 다른 공휴일 데이터입니다." }
            val names = json.getJSONArray(key)
            require(names.length() in 1..5)
            val label = (0 until names.length()).joinToString(" · ") { names.getString(it) }
            require(label.isNotBlank() && label.length < 200)
            Holiday(key, label)
        }.toList()
    }
}

class HolidayRepository(private val context: Context) {
    private val db = AlarmDatabase.get(context)
    private val dao = db.alarms()
    suspend fun seed() {
        AlarmMutations.mutex.withLock {
            val known = dao.years().map { it.year }.toSet()
            for (year in listOf(2026, 2027)) if (year !in known) {
                val rows = withContext(Dispatchers.IO) { context.assets.open("holidays-$year.json").bufferedReader().use { HolidayParser.parse(it.readText(), year) } }
                db.withTransaction { dao.putHolidays(rows); dao.putYear(HolidayYear(year, 0, "기본 포함 자료 · 2026-10-31 확인")) }
            }
        }
    }
    suspend fun refresh(): String {
        seed()
        val year = LocalDate.now().year
        // Validate each year independently. An unpublished next year must not block this year's corrections.
        val attempts = withContext(Dispatchers.IO) {
            (year..year + 1).associateWith { y ->
                runCatching {
                val connection = URL("https://holidays.hyunbin.page/$y.json").openConnection() as HttpURLConnection
                connection.connectTimeout = 15000; connection.readTimeout = 15000
                try {
                    check(connection.responseCode == 200) { "$y 년 자료를 가져오지 못했습니다." }
                    val bytes = connection.inputStream.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(4096)
                        while (true) {
                            val n = input.read(buffer); if (n == -1) break
                            require(output.size() + n <= 100_000) { "공휴일 응답이 너무 큽니다." }
                            output.write(buffer, 0, n)
                        }
                        output.toByteArray()
                    }
                    HolidayParser.parse(bytes.toString(Charsets.UTF_8), y)
                } finally { connection.disconnect() }
                }
            }
        }
        val results = attempts.filterValues { it.isSuccess }.mapValues { it.value.getOrThrow() }
        check(results.isNotEmpty()) { "공휴일 자료를 가져오지 못했습니다." }
        AlarmMutations.mutex.withLock {
            db.withTransaction {
                results.forEach { (y, rows) ->
                    dao.clearYear("$y-%"); dao.putHolidays(rows)
                    dao.putYear(HolidayYear(y, System.currentTimeMillis(), "holidays-kr · 월력요항 기반"))
                }
            }
            AlarmScheduler(context).rebuild(recalculate = true)
        }
        val missing = attempts.filterValues { it.isFailure }.keys
        return "${results.keys.joinToString("·")}년 공휴일 갱신 완료" +
            if (missing.isEmpty()) "" else " · ${missing.joinToString("·")}년 조회 실패, 기존 자료 유지"
    }
    fun scheduleRefresh() {
        val request = PeriodicWorkRequestBuilder<HolidayWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("holiday-refresh", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class HolidayWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        HolidayRepository(applicationContext).refresh(); Result.success()
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (_: Exception) { Result.retry() }
}
