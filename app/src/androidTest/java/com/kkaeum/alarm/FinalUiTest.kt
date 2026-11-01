package com.kkaeum.alarm
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
class FinalUiTest {
    @Test fun checkVolumeAndSnoozeSummary() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        fun tap(label: String) {
            device.waitForIdle(); SystemClock.sleep(500)
            val bounds=device.findObject(By.text(label)).visibleBounds
            device.swipe(bounds.centerX(),bounds.centerY(),bounds.centerX(),bounds.centerY(),20)
            SystemClock.sleep(500)
        }
        val audio=context.getSystemService(AudioManager::class.java)
        val original=audio.getStreamVolume(AudioManager.STREAM_ALARM)
        val dao=AlarmDatabase.get(context).alarms()
        val id=dao.put(Alarm(name="[표시 검사]", enabled=false)).toInt()
        try {
            dao.putSnooze(Snooze(id,System.currentTimeMillis()+2*60*60*1000,1))
            audio.setStreamVolume(AudioManager.STREAM_ALARM,0,0)
            context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            assertTrue(device.wait(Until.hasObject(By.text("설정")),10000))
            assertTrue(device.wait(Until.hasObject(By.text("다시 울림")),5000))
            assertTrue(device.hasObject(By.text("[표시 검사]")))
            device.waitForIdle(); SystemClock.sleep(500)
            if (audio.getStreamMinVolume(AudioManager.STREAM_ALARM) == 0) {
                assertTrue(device.wait(Until.hasObject(By.textContains("알람 음량이 0입니다")),5000))
                tap("확인")
            } else {
                tap("설정")
                assertTrue(device.wait(Until.hasObject(By.text("알람 점검")),5000))
                SystemClock.sleep(500); tap("알람 점검")
            }
            assertTrue(device.wait(Until.hasObject(By.text("알람 점검")),5000))
            assertTrue(device.hasObject(By.text("정확한 알람")))
            if (audio.getStreamMinVolume(AudioManager.STREAM_ALARM) == 0) assertTrue(device.hasObject(By.text("알람 소리가 나지 않습니다")))
            val slider=device.findObject(By.clazz("android.widget.SeekBar"))
            assertNotNull(slider)
            slider.click(); SystemClock.sleep(1500)
            assertTrue(audio.getStreamVolume(AudioManager.STREAM_ALARM)>0)
            tap("닫기"); SystemClock.sleep(1500)
            assertFalse(device.hasObject(By.textContains("알람 음량이 0입니다")))
        } catch (e: Throwable) {
            device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "final-ui-failure.png"))
            throw e
        } finally {
            audio.setStreamVolume(AudioManager.STREAM_ALARM,original,0)
            AlarmScheduler(context).cancel(id); dao.delete(id)
        }
    }
}
