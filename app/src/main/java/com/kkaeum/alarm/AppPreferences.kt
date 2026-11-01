package com.kkaeum.alarm

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class AppOptions(val theme: String = "system", val clock24: Boolean = true,
    val seconds: Boolean = true)

object AppPreferences {
    private var preferences: SharedPreferences? = null
    private val values = MutableStateFlow(AppOptions())
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, _ -> values.value = snapshot(p) }
    private fun snapshot(p: SharedPreferences) = AppOptions(p.getString("theme", "system") ?: "system",
        p.getBoolean("clock24", true), p.getBoolean("seconds", true))
    @Synchronized private fun prefs(context: Context): SharedPreferences = preferences ?: AlarmStorage.context(context)
        .getSharedPreferences("app_options", Context.MODE_PRIVATE).also {
            preferences = it; it.registerOnSharedPreferenceChangeListener(listener); values.value = snapshot(it)
        }
    fun read(context: Context) = snapshot(prefs(context))
    fun observe(context: Context): StateFlow<AppOptions> { prefs(context); return values }
    fun appearance(context: Context, options: AppOptions) {
        check(prefs(context).edit().putString("theme", options.theme).putBoolean("clock24", options.clock24)
            .putBoolean("seconds", options.seconds).commit())
        values.value = read(context)
    }

}

@Composable fun appOptions(): AppOptions {
    val context = LocalContext.current
    val value by remember(context) { AppPreferences.observe(context) }.collectAsState()
    return value
}
fun displayTime(time: LocalTime, options: AppOptions, seconds: Boolean = false): String {
    val pattern = (if (options.clock24) "HH:mm" else "a h:mm") + if (seconds && options.seconds) ":ss" else ""
    return time.format(DateTimeFormatter.ofPattern(pattern, Locale.KOREAN))
}
