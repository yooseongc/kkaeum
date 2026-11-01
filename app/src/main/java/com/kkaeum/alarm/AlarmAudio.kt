package com.kkaeum.alarm

import android.content.Context
import android.media.*
import android.net.Uri

class AlarmAudio(private val context: Context) {
    private var player: MediaPlayer? = null
    fun stop() { player?.run { runCatching { stop() }; release() }; player = null }
    fun play(uri: String, loop: Boolean = true, failed: () -> Unit = {}) {
        stop()
        val fallback = AlarmSounds.fallback(context)
        fun start(target: Uri, mayFallback: Boolean) {
            try {
                val media = MediaPlayer()
                player = media
                media.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                media.setOnErrorListener { _, _, _ ->
                    stop(); failed()
                    if (mayFallback) start(fallback, false)
                    true
                }
                media.setDataSource(context, target)
                media.isLooping = loop
                media.prepare(); media.start()
                android.util.Log.i("KkaeumAudio", "PLAY scheme=${target.scheme} unlocked=${context.getSystemService(android.os.UserManager::class.java).isUserUnlocked}")
            } catch (_: Exception) {
                stop(); failed()
                if (mayFallback) start(fallback, false)
            }
        }
        val target = runCatching {
            if (uri.isBlank()) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: fallback
            else AlarmSounds.resolve(context, uri)
        }.getOrElse { failed(); fallback }
        start(target, target != fallback)
    }
}
