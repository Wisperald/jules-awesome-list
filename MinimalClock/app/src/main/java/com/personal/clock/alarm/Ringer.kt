package com.personal.clock.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Plays a system ringtone on the alarm stream (audible in silent mode, like any clock app)
 * with optional vibration and a gentle volume ramp. No audio files are bundled in the APK.
 */
class Ringer(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var volume = 1f

    private val audioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
    }

    private val ramp = object : Runnable {
        override fun run() {
            volume = (volume + RAMP_STEP).coerceAtMost(1f)
            player?.setVolume(volume, volume)
            if (volume < 1f) handler.postDelayed(this, RAMP_INTERVAL_MS)
        }
    }

    fun start(ringtone: Uri?, vibrate: Boolean, gradualVolume: Boolean) {
        stop()
        requestFocus()
        volume = if (gradualVolume) RAMP_START else 1f
        player = createPlayer(ringtone)?.apply {
            isLooping = true
            setVolume(volume, volume)
            start()
        }
        if (gradualVolume && player != null) handler.postDelayed(ramp, RAMP_INTERVAL_MS)
        if (vibrate || player == null) startVibration() // vibrate as fallback when no sound is available
    }

    fun stop() {
        handler.removeCallbacks(ramp)
        player?.run {
            try {
                stop()
            } catch (_: IllegalStateException) {
            }
            release()
        }
        player = null
        vibrator?.cancel()
        abandonFocus()
    }

    private fun createPlayer(preferred: Uri?): MediaPlayer? {
        val candidates = listOfNotNull(
            preferred,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        ).distinct()
        for (uri in candidates) {
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(audioAttributes)
                mp.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
                mp.setDataSource(context, uri)
                mp.prepare()
                return mp
            } catch (e: Exception) {
                // Missing/unreadable sound (e.g. removed file, or storage locked before first unlock).
                Log.w(TAG, "Cannot play $uri", e)
                mp.release()
            }
        }
        return null
    }

    private fun startVibration() {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(VIBRATION_PATTERN, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vib.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(effect, audioAttributes)
        }
    }

    private fun requestFocus() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(audioAttributes)
            .build()
        focusRequest = request
        audioManager.requestAudioFocus(request)
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private companion object {
        const val TAG = "Ringer"
        const val RAMP_START = 0.1f
        const val RAMP_STEP = 0.05f // 0.1 → 1.0 in ~36 s
        const val RAMP_INTERVAL_MS = 2_000L
        val VIBRATION_PATTERN = longArrayOf(0, 700, 700)
    }
}
