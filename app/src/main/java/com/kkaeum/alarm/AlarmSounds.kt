package com.kkaeum.alarm

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

data class BuiltinSound(val id: String, val title: String, val description: String, val resource: Int) {
    val uri get() = "kkaeum:$id"
}
object AlarmSounds {
    val builtins = listOf(
        BuiltinSound("pulse", "또렷한 비프", "짧고 반복적인 전자음", R.raw.pulse),
        BuiltinSound("alternating", "교차 신호", "높낮이가 번갈아 바뀌는 신호음", R.raw.alternating),
        BuiltinSound("bell", "밝은 종소리", "여러 배음이 겹치는 선명한 종소리", R.raw.bell),
        BuiltinSound("rhythm", "빠른 리듬", "짧은 음을 점점 촘촘하게 반복", R.raw.rhythm),
        BuiltinSound("morning", "아침 계단", "차례로 올라가는 부드러운 멜로디", R.raw.morning),
    )
    fun fallback(context: Context) = Uri.parse("android.resource://${context.packageName}/${R.raw.pulse}")
    fun resolve(context: Context, value: String): Uri {
        builtins.firstOrNull { it.uri == value }?.let { return Uri.parse("android.resource://${context.packageName}/${it.resource}") }
        if (value.startsWith("kkaeum-file:")) {
            val name = value.removePrefix("kkaeum-file:")
            require(name.matches(Regex("[a-f0-9-]+\\.audio")))
            return Uri.fromFile(File(AlarmStorage.context(context).filesDir, "sounds/$name"))
        }
        return Uri.parse(value)
    }
    /** Keep a private copy so document providers and first-unlock storage are not required at alarm time. */
    fun import(context: Context, uri: Uri): String {
        val dir = File(AlarmStorage.context(context).filesDir, "sounds").apply { mkdirs() }
        val name = "${UUID.randomUUID()}.audio"
        val file = File(dir, name)
        try {
            context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { output ->
                val buffer = ByteArray(8192); var total = 0
                while (true) {
                    val count = input.read(buffer); if (count < 0) break
                    total += count; require(total <= 25 * 1024 * 1024) { "25MB 이하의 음원을 선택해 주세요." }
                    output.write(buffer, 0, count)
                }
                require(total > 0) { "빈 파일입니다." }
            } }
            return "kkaeum-file:$name"
        } catch (e: Exception) { file.delete(); throw e }
    }
}
