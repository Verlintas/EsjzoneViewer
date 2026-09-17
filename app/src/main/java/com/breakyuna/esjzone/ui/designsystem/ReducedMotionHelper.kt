package com.breakyuna.esjzone.ui.designsystem

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Caches system animation scale lookup to avoid frequent Binder IPC
 * across SettingsProvider when rendering animated surfaces or Lottie icons.
 */
object ReducedMotionHelper {
    private const val CACHE_TTL_MS = 30_000L

    @Volatile
    private var cachedValue: Boolean? = null

    @Volatile
    private var lastCheckedTimestamp: Long = 0L

    fun isReducedMotion(context: Context): Boolean {
        val now = SystemClock.uptimeMillis()
        val cached = cachedValue
        if (cached != null && (now - lastCheckedTimestamp) < CACHE_TTL_MS) {
            return cached
        }
        val value = queryReducedMotion(context)
        cachedValue = value
        lastCheckedTimestamp = now
        return value
    }

    private fun queryReducedMotion(context: Context): Boolean {
        val resolver = context.contentResolver ?: return false
        return sequenceOf(
            Settings.Global.ANIMATOR_DURATION_SCALE,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            Settings.Global.WINDOW_ANIMATION_SCALE
        ).any { key ->
            runCatching {
                Settings.Global.getFloat(resolver, key, 1f) <= 0f
            }.getOrDefault(false)
        }
    }

    /** Clears cached animation scale, e.g. for testing. */
    fun clearCache() {
        cachedValue = null
        lastCheckedTimestamp = 0L
    }
}

/**
 * Rememberable Composable helper that reads whether the user or system has requested reduced motion.
 */
@Composable
fun rememberSystemReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        ReducedMotionHelper.isReducedMotion(context)
    }
}
