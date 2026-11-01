package com.kkaeum.alarm

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceLayoutTest {
    @Test fun wheelRotationSoundPickerAndCalendar() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val rotation = device.displayRotation
        try {
            device.setOrientationLeft()
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            assertTrue(device.wait(Until.hasObject(By.text("+ 새 알람")), 10000))
            device.findObject(By.text("+ 새 알람")).click()
            assertTrue(device.wait(Until.hasObject(By.text("새 알람")), 5000))
            val pickers = device.findObjects(By.clazz("android.widget.NumberPicker"))
            assertEquals(2, pickers.size)
            val minute = pickers[1]
            val before = minute.findObject(By.clazz("android.widget.EditText")).text
            val bounds = minute.visibleBounds
            device.swipe(bounds.centerX(), bounds.centerY() + bounds.height() / 3,
                bounds.centerX(), bounds.centerY() - bounds.height() / 3, 20)
            device.waitForIdle()
            val after = device.findObjects(By.clazz("android.widget.NumberPicker"))[1]
                .findObject(By.clazz("android.widget.EditText")).text
            assertNotEquals(before, after)
            device.setOrientationNatural(); android.os.SystemClock.sleep(700)
            assertTrue(device.wait(Until.hasObject(By.clazz("android.widget.NumberPicker")), 5000))
            device.waitForIdle()
            assertEquals(after, device.findObjects(By.clazz("android.widget.NumberPicker"))[1]
                .findObject(By.clazz("android.widget.EditText")).text)
            device.setOrientationLeft(); android.os.SystemClock.sleep(700)
            assertTrue(device.wait(Until.hasObject(By.clazz("android.widget.NumberPicker")), 5000))
            device.waitForIdle()
            assertTrue(device.wait(Until.hasObject(By.text("기기 알람음")), 5000))
            device.findObject(By.text("깨움 알람음")).click()
            assertTrue(device.wait(Until.hasObject(By.text("밝은 종소리")), 5000))
            device.findObject(By.text("밝은 종소리")).click()
            assertTrue(device.wait(Until.hasObject(By.text("밝은 종소리")), 5000))
            device.findObject(By.text("기기 알람음")).click()
            assertTrue(device.wait(Until.hasObject(By.pkg("com.samsung.android.secsoundpicker")), 10000))
            device.findObject(By.text("Galaxy Bells")).click()
            if (!device.wait(Until.hasObject(By.text("새 알람")), 2000)) device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.text("새 알람")), 10000))
            assertTrue(device.wait(Until.hasObject(By.text("Galaxy Bells")), 5000))
            device.waitForIdle(); android.os.SystemClock.sleep(500)
            device.findObject(By.text("설정")).click()
            if (device.wait(Until.hasObject(By.text("버리기")), 2000)) device.findObject(By.text("버리기")).click().also { android.os.SystemClock.sleep(500) }
            assertTrue(device.wait(Until.hasObject(By.text("공휴일 · 날짜별 설정")), 5000))
            device.findObject(By.text("공휴일 · 날짜별 설정")).click()
            assertTrue(device.wait(Until.hasObject(By.text("한국 공휴일")), 5000))
            device.findObject(By.text("한국 공휴일")).click()
            assertTrue(device.wait(Until.hasObject(By.text("2026-01-01")), 5000))
            device.pressBack()
            // Discard only this unsaved test draft; no real alarm is created.
            device.findObject(By.text("+ 새 알람")).click()
            if (device.wait(Until.hasObject(By.text("버리기")), 2000)) device.findObject(By.text("버리기")).click().also { android.os.SystemClock.sleep(500) }
        } finally {
            if (device.currentPackageName == "com.samsung.android.secsoundpicker") device.pressBack()
            when (rotation) { 0 -> device.setOrientationNatural(); 1 -> device.setOrientationLeft(); 3 -> device.setOrientationRight() }
            device.unfreezeRotation()
        }
    }
}
