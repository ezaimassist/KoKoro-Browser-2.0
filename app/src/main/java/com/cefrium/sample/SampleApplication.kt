package com.cefrium.sample

import android.app.Application
import com.cefrium.Cefrium

/**
 * KoKoro Browser Application bootstrap.
 * Configures high-performance Chromium command-line switches for GPU rasterization
 * and low-latency input on Android 12+ and initializes Cefrium EasyList & YouTube rules.
 */
class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val procName = getProcessName().orEmpty()
        if (!procName.contains(":")) {
            configureChromiumPerformanceSwitches()
            runCatching { Cefrium.downloadAndLoadEasyList(this) }
            runCatching { Cefrium.loadYouTubeAdRules() }
        }
    }

    private fun configureChromiumPerformanceSwitches() {
        try {
            val cmdLineClass = Class.forName("org.chromium.base.CommandLine")
            val isInit = cmdLineClass.getMethod("isInitialized")
            if (java.lang.Boolean.TRUE == isInit.invoke(null)) {
                val instance = cmdLineClass.getMethod("getInstance").invoke(null)
                val appendSwitch = cmdLineClass.getMethod("appendSwitch", String::class.java)
                val appendSwitchWithValue = cmdLineClass.getMethod(
                    "appendSwitchWithValue",
                    String::class.java,
                    String::class.java,
                )

                // Uncap frame rates and remove internal vsync pipeline stalls
                appendSwitch.invoke(instance, "disable-frame-rate-limit")
                appendSwitch.invoke(instance, "disable-gpu-vsync")
                appendSwitchWithValue.invoke(instance, "max-gum-fps", "120")

                // Hardware GPU rasterization and zero-copy direct buffers
                appendSwitch.invoke(instance, "enable-gpu-rasterization")
                appendSwitch.invoke(instance, "enable-oop-rasterization")
                appendSwitch.invoke(instance, "ignore-gpu-blocklist")
                appendSwitch.invoke(instance, "enable-zero-copy")
                appendSwitch.invoke(instance, "enable-native-gpu-memory-buffers")
                appendSwitch.invoke(instance, "enable-surface-control")
                appendSwitch.invoke(instance, "enable-accelerated-2d-canvas")
                appendSwitch.invoke(instance, "enable-threaded-compositing")
                appendSwitch.invoke(instance, "disable-low-end-device-mode")
                appendSwitch.invoke(instance, "disable-gpu-watchdog")

                // Multi-threaded rasterization & lean MSAA
                appendSwitchWithValue.invoke(instance, "num-raster-threads", "4")
                appendSwitchWithValue.invoke(instance, "gpu-rasterization-msaa-sample-count", "0")

                // High-frequency touch & scroll resampling
                appendSwitchWithValue.invoke(
                    instance,
                    "enable-features",
                    "ResamplingInputEvents,ResamplingScrollEvents,PassiveDocumentEventListeners,CanvasOopRasterization,UseSkiaRenderer",
                )
            }
        } catch (_: Throwable) {
            // Engine already initialized without mutable CommandLine; safe no-op.
        }
    }
}
