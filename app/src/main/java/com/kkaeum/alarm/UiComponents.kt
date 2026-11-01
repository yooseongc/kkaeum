package com.kkaeum.alarm

import android.app.Activity
import android.widget.NumberPicker
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

val Ink: Color @Composable get() = MaterialTheme.colorScheme.onSurface
val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val Line: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
@Composable fun KkaeumTheme(content: @Composable () -> Unit) {
    val options = appOptions()
    val dark = options.theme == "dark" || (options.theme == "system" && isSystemInDarkTheme())
    val palette = if (dark) darkColorScheme(
        primary = Color(0xFFBCB2FF), onPrimary = Color(0xFF261864), primaryContainer = Color(0xFF39305D), onPrimaryContainer = Color(0xFFE8E1FF),
        secondary = Color(0xFFE6E3DC), secondaryContainer = Color(0xFF302F34), onSecondaryContainer = Color(0xFFE6E3DC),
        background = Color(0xFF19191C), surface = Color(0xFF222226), onBackground = Color(0xFFE9E6DF), onSurface = Color(0xFFE9E6DF),
        surfaceVariant = Color(0xFF302F34), onSurfaceVariant = Color(0xFFBDB9B2), outline = Color(0xFF8B878F), outlineVariant = Color(0xFF444248),
        surfaceContainer = Color(0xFF27272C), surfaceContainerHighest = Color(0xFF333238)) else lightColorScheme(primary = Color(0xFF5645D4), onPrimary = Color.White,
        primaryContainer = Color(0xFFEEEBFB), onPrimaryContainer = Color(0xFF3A2A99),
        secondary = Color(0xFF37352F), secondaryContainer = Color(0xFFF0EEEC), onSecondaryContainer = Color(0xFF37352F),
        surfaceVariant = Color(0xFFF0EEEC), onSurfaceVariant = Color(0xFF69665F), outline = Color(0xFF9A968E), outlineVariant = Color(0xFFE5E3DF),
        surfaceContainerHighest = Color(0xFFF0EEEC), surfaceContainer = Color(0xFFF6F5F4),
        background = Color(0xFFFAFAF9), surface = Color.White, onSurface = Color(0xFF37352F), onBackground = Color(0xFF37352F))
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val window = (view.context as? Activity)?.window
        if (window != null) WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = palette,
        shapes = Shapes(extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(8.dp), large = RoundedCornerShape(12.dp), extraLarge = RoundedCornerShape(16.dp)),
        content = content)
}
@Composable fun SettingCard(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, Line)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
            content()
        }
    }
}
@Composable fun Wheel(value: Int, max: Int, label: String, onChange: (Int) -> Unit) {
    val wheelColor = MaterialTheme.colorScheme.onSurface.toArgb()
    AndroidView(modifier = Modifier.width(120.dp).height(144.dp), factory = { context ->
        NumberPicker(context).apply {
            minValue = 0; maxValue = max; wrapSelectorWheel = true
            descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
            setFormatter { "%02d".format(it) }
            contentDescription = label
            textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 30f, resources.displayMetrics)
            textColor = wheelColor
            this.value = value
            setOnValueChangedListener { _, _, new -> onChange(new) }
        }
    }, update = { picker ->
        picker.textColor = wheelColor
        if (picker.value != value) picker.value = value
        picker.setOnValueChangedListener { _, _, new -> onChange(new) }
    })
}
fun dayLabel(days: Int): String = when(days) {
    0 -> "한 번만"; 31 -> "평일"; 96 -> "주말"; 127 -> "매일"
    else -> listOf("월","화","수","목","금","토","일").filterIndexed { i, _ -> days and (1 shl i) != 0 }.joinToString(" · ")
}
fun nextLabel(time: ZonedDateTime, now: ZonedDateTime, options: AppOptions = AppOptions()): String =
    alarmDateLabel(time, now) + " " + displayTime(time.toLocalTime(), options) + " · " + remainingLabel(time, now)
