package com.kkaeum.alarm

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test

class PreferencesTest {
    @Test fun settingsAreSeparateAndThemePersists() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val original = AppPreferences.read(context)
        try {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            assertTrue(device.wait(Until.hasObject(By.text("설정")), 10000))
            device.waitForIdle(); android.os.SystemClock.sleep(500)
            device.findObject(By.text("설정")).click()
            if (device.wait(Until.hasObject(By.text("버리기")), 1000)) device.findObject(By.text("버리기")).click().also { android.os.SystemClock.sleep(500) }
            assertTrue(device.wait(Until.hasObject(By.text("다크")), 5000))
            device.findObject(By.text("다크")).click(); android.os.SystemClock.sleep(500); device.waitForIdle()
            assertEquals("dark", AppPreferences.read(context).theme)
            assertFalse(device.hasObject(By.text("한국 공휴일 건너뛰기")))
            assertTrue(device.hasObject(By.text("공휴일 · 날짜별 설정")))
            assertTrue(device.findObjects(By.clazz("android.widget.NumberPicker")).isEmpty())
            device.findObject(By.text("라이트")).click(); android.os.SystemClock.sleep(500); device.waitForIdle()
            assertEquals("light", AppPreferences.read(context).theme)
            val stored = AlarmStorage.context(context).getSharedPreferences("app_options", Context.MODE_PRIVATE)
            assertEquals("light", stored.getString("theme", null))
            device.findObject(By.text("← 알람 목록")).click()
            assertTrue(device.wait(Until.hasObject(By.text("다음 알람")), 5000))
            assertTrue(device.findObjects(By.clazz("android.widget.NumberPicker")).isEmpty())
        } finally { AppPreferences.appearance(context, original) }
    }
}
