package com.kkaeum.alarm

import android.app.Application
import androidx.room.withTransaction
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

class AlarmViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AlarmDatabase.get(application).alarms()
    private val scheduler = AlarmScheduler(application)
    private val repository = HolidayRepository(application)
    val alarms = dao.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val pauses = dao.pausesFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val holidays = dao.holidaysFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val rules = dao.rulesFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val years = dao.yearsFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val snoozes = dao.snoozesFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val message = MutableStateFlow("")
    val refreshing = MutableStateFlow(false)
    val ready = MutableStateFlow(false)
    init { viewModelScope.launch {
        try {
            repository.seed()
            AlarmMutations.mutex.withLock { scheduler.rebuild() }
            repository.scheduleRefresh()
            ready.value = true
        } catch (e: Exception) { message.value = "알람을 불러오지 못했어요. 앱을 다시 열어 주세요." }
    } }
    fun save(alarm: Alarm, saved: (Alarm) -> Unit = {}) { viewModelScope.launch {
        try { AlarmMutations.mutex.withLock {
            require(alarm.hour in 0..23 && alarm.minute in 0..59 && alarm.count in 1..10 && alarm.digits in 1..2)
            if (alarm.enabled) check(scheduler.allowed()) { "정확한 알람 권한을 먼저 허용해 주세요." }
            val clean = alarm.copy(name = alarm.name.trim().ifEmpty { "알람" }, groupName = alarm.groupName.trim().take(24))
            val id = dao.put(clean).toInt()
            val result = clean.copy(id = if (clean.id == 0) id else clean.id)
            scheduler.cancelSnooze(result.id)
            try { scheduler.schedule(result) } catch (e: Exception) {
                dao.put(result.copy(enabled = false)); scheduler.cancel(result.id); throw e
            }
            message.value = if (result.enabled) "다음 알람: " + nextLabel(scheduler.next(result).time, java.time.ZonedDateTime.now(), AppPreferences.read(getApplication())) else "알람 꺼짐"; saved(result)
        } } catch (e: Exception) { message.value = if (!scheduler.allowed()) "정확한 알람 권한을 허용해 주세요." else "알람을 저장하지 못했어요. 다시 시도해 주세요." }
    } }
    fun delete(alarm: Alarm, done: () -> Unit) { viewModelScope.launch {
        try { AlarmMutations.mutex.withLock { scheduler.cancel(alarm.id); dao.delete(alarm.id) }; done() }
        catch (e: Exception) { message.value = "알람을 삭제하지 못했어요. 다시 시도해 주세요." }
    } }
    fun skip(alarm: Alarm, updated: (Alarm) -> Unit) { viewModelScope.launch {
        try { AlarmMutations.mutex.withLock {
            val fresh = dao.find(alarm.id) ?: return@withLock
            check(fresh.enabled) { "켜진 알람만 건너뛸 수 있습니다." }
            val skip = if (fresh.skipDate >= LocalDate.now().toString()) "" else scheduler.next(fresh).time.toLocalDate().toString()
            val result = fresh.copy(skipDate = skip)
            dao.put(result); scheduler.schedule(result); updated(result)
            message.value = if (skip.isEmpty()) "건너뛰기를 취소했습니다." else "$skip 알람을 한 번 건너뜁니다."
        } } catch (e: Exception) { message.value = "알람을 변경하지 못했어요. 다시 시도해 주세요." }
    } }
    fun rule(date: String, force: Boolean?) { viewModelScope.launch {
        try { AlarmMutations.mutex.withLock {
            LocalDate.parse(date)
            if (force == null) dao.deleteRule(date) else dao.putRule(DateRule(date, force))
            scheduler.rebuild(recalculate = true)
            message.value = "날짜별 설정을 저장했어요."
        } } catch (e: Exception) { message.value = "날짜별 설정을 저장하지 못했어요. 다시 시도해 주세요." }
    } }
    fun group(name: String, enabled: Boolean) { viewModelScope.launch {
        try { AlarmMutations.mutex.withLock {
            if (enabled) check(scheduler.allowed())
            val rows=dao.all().filter { it.groupName == name }
            AlarmDatabase.get(getApplication()).withTransaction { rows.forEach { dao.put(it.copy(enabled=enabled)) } }
            rows.forEach { scheduler.cancelSnooze(it.id) }
            try { rows.forEach { scheduler.schedule(it.copy(enabled=enabled)) } }
            catch (e: Exception) {
                rows.forEach { scheduler.cancelRegular(it.id) }
                AlarmDatabase.get(getApplication()).withTransaction { rows.forEach { dao.put(it.copy(enabled=false)) } }
                throw e
            }
            message.value=if (enabled) "그룹 알람을 켰습니다" else "그룹 알람을 껐습니다"
        } } catch (e: Exception) { message.value="그룹을 변경하지 못했습니다. 알람 권한을 확인해 주세요." }
    } }
    private suspend fun reschedulePeriods() {
        val periods=dao.pauses()
        dao.snoozes().forEach { row ->
            val date=java.time.Instant.ofEpochMilli(row.due).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
            if (periods.any { date >= it.start && date <= it.end }) scheduler.cancelSnooze(row.alarmId)
        }
        if (!scheduler.allowed()) dao.all().forEach { dao.clearScheduled(it.id) }
        scheduler.rebuild(recalculate=true)
    }
    fun pause(period: PausePeriod, remove: Boolean = false) { viewModelScope.launch {
        try { AlarmMutations.mutex.withLock {
            if (remove) dao.deletePause(period.start) else { validatePause(period); dao.putPause(period) }
            reschedulePeriods(); message.value=if(remove) "기간을 삭제했습니다" else "기간을 저장했습니다"
        } } catch(e: Exception) { message.value="기간을 저장하지 못했습니다. 날짜와 알람 권한을 확인해 주세요." }
    } }
    suspend fun restore(data: BackupData) {
        AlarmMutations.mutex.withLock {
            val appearanceSaved=AlarmBackup.restore(getApplication(),data)
            val scheduled=runCatching { reschedulePeriods() }.isSuccess
            message.value="${data.alarms.size}개 알람을 꺼진 상태로 가져왔습니다" +
                (if(!appearanceSaved) " · 표시 설정 저장 실패" else "") +
                (if(!scheduled) " · 기존 알람 재예약 실패: 알람 점검을 확인해 주세요" else "")
        }
    }
    fun refresh() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            try { message.value = repository.refresh() }
            catch (e: Exception) { message.value = "공휴일을 업데이트하지 못했어요. 기존 목록을 사용합니다." }
            finally { refreshing.value = false }
        }
    }
    fun cancelSnooze(id: Int) { viewModelScope.launch { AlarmMutations.mutex.withLock {
        scheduler.cancelSnooze(id); message.value = "다시 울림을 취소했습니다."
    } } }
}
