package com.cefrium.sample.engine

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.view.Choreographer
import android.view.Display
import android.view.View
import android.view.WindowManager
import com.cefrium.sample.GameSurfaceRate
import kotlin.math.roundToInt

enum class ThermalLevel(val label: String) {
    OPTIMAL("Ottimale"),
    WARM("Tiepido"),
    HOT("Caldo"),
    CRITICAL("Critico");

    companion object {
        fun fromPowerManager(status: Int): ThermalLevel = when (status) {
            PowerManager.THERMAL_STATUS_NONE,
            PowerManager.THERMAL_STATUS_LIGHT -> OPTIMAL
            PowerManager.THERMAL_STATUS_MODERATE -> WARM
            PowerManager.THERMAL_STATUS_SEVERE -> HOT
            else -> CRITICAL
        }
    }
}

data class HardwareTelemetry(
    val currentHz: Int = 60,
    val supportedHzOptions: List<Int> = listOf(60),
    val recentHzHistory: List<Int> = emptyList(),
    val liveFps: Int = 0,
    val usedRamMb: Long = 0L,
    val totalRamMb: Long = 0L,
    val availRamMb: Long = 0L,
    val lowMemory: Boolean = false,
    val thermalLevel: ThermalLevel = ThermalLevel.OPTIMAL,
    val surfaceLocked: Boolean = false,
    val boostActive: Boolean = false,
    val activeBoostHz: Int = 0,
)

/**
 * Resolves the current Android 12+ display without using deprecated defaultDisplay.
 */
fun Activity.currentDisplayCompat(): Display? =
    this.display ?: getSystemService(DisplayManager::class.java)
        ?.getDisplay(Display.DEFAULT_DISPLAY)

/**
 * Returns real hardware refresh rates supported at the panel's active physical resolution.
 */
fun Activity.querySupportedRefreshRates(): List<Int> {
    val d = currentDisplayCompat() ?: return listOf(60)
    val current = d.mode
    val matching = d.supportedModes
        .filter { mode ->
            (mode.physicalWidth == current.physicalWidth && mode.physicalHeight == current.physicalHeight) ||
                (mode.physicalWidth == current.physicalHeight && mode.physicalHeight == current.physicalWidth)
        }
        .map { it.refreshRate.roundToInt() }
        .filter { it > 0 }
        .distinct()
        .sorted()
    return matching.ifEmpty { listOf(d.refreshRate.roundToInt().coerceAtLeast(60)) }
}

/**
 * Reads live system memory & thermal status from Android system services (zero mock values).
 */
fun Context.readSystemSnapshot(): SystemHealthSnapshot {
    val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    am.getMemoryInfo(memInfo)
    val totalMb = memInfo.totalMem / (1024L * 1024L)
    val availMb = memInfo.availMem / (1024L * 1024L)
    val usedMb = (totalMb - availMb).coerceAtLeast(0L)

    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
    val thermal = ThermalLevel.fromPowerManager(pm.currentThermalStatus)

    return SystemHealthSnapshot(
        usedRamMb = usedMb,
        totalRamMb = totalMb,
        availRamMb = availMb,
        lowMemory = memInfo.lowMemory,
        thermalLevel = thermal,
    )
}

data class SystemHealthSnapshot(
    val usedRamMb: Long,
    val totalRamMb: Long,
    val availRamMb: Long,
    val lowMemory: Boolean,
    val thermalLevel: ThermalLevel,
)

/**
 * Measures real rendered frames per second via Choreographer over a rolling 500 ms window.
 */
class LiveFpsSampler(private val onFpsMeasured: (Int) -> Unit) : Choreographer.FrameCallback {
    private var running = false
    private var windowStartNs = 0L
    private var frameCount = 0

    fun start() {
        if (running) return
        running = true
        windowStartNs = 0L
        frameCount = 0
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        if (windowStartNs == 0L) {
            windowStartNs = frameTimeNanos
            frameCount = 0
        } else {
            frameCount++
            val elapsedNs = frameTimeNanos - windowStartNs
            if (elapsedNs >= 500_000_000L) {
                val fps = ((frameCount * 1_000_000_000.0) / elapsedNs).roundToInt()
                onFpsMeasured(fps)
                windowStartNs = frameTimeNanos
                frameCount = 0
            }
        }
        Choreographer.getInstance().postFrameCallback(this)
    }
}

/**
 * Applies real Android 12+ Gaming Boost optimizations to the Activity window and SurfaceViews,
 * returning a cleanup handle that cleanly restores previous window parameters.
 */
class GamingBoostSession(
    private val activity: Activity,
    private val targetHz: Int,
    private val sustainedPerformance: Boolean,
    private val keepScreenOn: Boolean,
) {
    private var surfaceRate: GameSurfaceRate? = null
    private var oldModeId: Int = 0
    private var oldRefreshRate: Float = 0f
    private var oldViewFrameRate: Float = 0f
    private var engaged: Boolean = false

    var appliedHz: Int = 0
        private set

    val isSurfaceLocked: Boolean
        get() = surfaceRate?.requested == true

    fun start() {
        if (engaged) return
        val display = activity.currentDisplayCompat() ?: return
        val window = activity.window
        val current = display.mode

        oldModeId = window.attributes.preferredDisplayModeId
        oldRefreshRate = window.attributes.preferredRefreshRate
        if (Build.VERSION.SDK_INT >= 35) {
            oldViewFrameRate = runCatching { window.decorView.requestedFrameRate }.getOrDefault(0f)
        }

        val matchingModes = display.supportedModes.filter { mode ->
            (mode.physicalWidth == current.physicalWidth && mode.physicalHeight == current.physicalHeight) ||
                (mode.physicalWidth == current.physicalHeight && mode.physicalHeight == current.physicalWidth)
        }
        val bestMode = if (targetHz <= 0) {
            matchingModes.maxByOrNull { it.refreshRate }
        } else {
            matchingModes
                .filter { it.refreshRate <= targetHz + 1.5f }
                .maxByOrNull { it.refreshRate }
                ?: matchingModes.minByOrNull { kotlin.math.abs(it.refreshRate - targetHz) }
        } ?: current

        appliedHz = bestMode.refreshRate.roundToInt()
        engaged = true

        surfaceRate = GameSurfaceRate(window.decorView, bestMode.refreshRate).also { it.start() }

        window.attributes = window.attributes.apply {
            preferredDisplayModeId = bestMode.modeId
            applyOemRefreshFields(this, bestMode.refreshRate)
        }

        if (Build.VERSION.SDK_INT >= 35) {
            setFrameCategoryCompat(window.decorView, 2)
            runCatching { window.decorView.requestedFrameRate = bestMode.refreshRate }
        }

        if (keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        if (sustainedPerformance) {
            runCatching { window.setSustainedPerformanceMode(true) }
        }
        runCatching {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)
        }
    }

    fun stop() {
        surfaceRate?.stop()
        surfaceRate = null
        if (!engaged) return
        engaged = false

        val window = activity.window
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        runCatching { window.setSustainedPerformanceMode(false) }

        if (Build.VERSION.SDK_INT >= 35) {
            setFrameCategoryCompat(window.decorView, 0)
            runCatching { window.decorView.requestedFrameRate = oldViewFrameRate }
        }

        window.attributes = window.attributes.apply {
            preferredDisplayModeId = oldModeId
            applyOemRefreshFields(this, oldRefreshRate)
        }
    }

    private fun applyOemRefreshFields(params: WindowManager.LayoutParams, rate: Float) {
        params.preferredRefreshRate = rate
        runCatching {
            params.javaClass.getField("preferredMinDisplayRefreshRate").setFloat(params, rate)
        }
        runCatching {
            params.javaClass.getField("preferredMaxDisplayRefreshRate").setFloat(params, rate)
        }
    }

    private fun setFrameCategoryCompat(view: View, category: Int) {
        if (Build.VERSION.SDK_INT >= 35) {
            runCatching {
                val m = View::class.java.getMethod("setFrameRateCategory", Int::class.javaPrimitiveType)
                m.invoke(view, category)
            }
        }
    }
}

/**
 * Registers a DisplayListener on the main looper without deprecated Display APIs.
 */
fun Activity.observeDisplayRefreshRate(onRateChanged: (Int) -> Unit): () -> Unit {
    val manager = getSystemService(DisplayManager::class.java) ?: return {}
    val displayId = currentDisplayCompat()?.displayId ?: Display.DEFAULT_DISPLAY

    fun emit() {
        val rate = manager.getDisplay(displayId)?.refreshRate ?: return
        val hz = rate.roundToInt()
        if (hz > 0) onRateChanged(hz)
    }

    val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(changedId: Int) {
            if (changedId == displayId) emit()
        }
        override fun onDisplayAdded(id: Int) = Unit
        override fun onDisplayRemoved(id: Int) = Unit
    }

    emit()
    manager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
    return { manager.unregisterDisplayListener(listener) }
}
