package com.cefrium.sample

import android.app.Application
import com.cefrium.Cefrium
import org.chromium.base.ContextUtils

/**
 * The Cefrium SDK initializes the engine automatically at app launch
 * (via CefriumInitProvider) — no init boilerplate is required. This Application
 * exists only to kick off the optional EasyList ad-block download once the engine
 * is up; an app that does not need ad-blocking needs no Application at all.
 */
class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!ContextUtils.getProcessName().contains(":")) {
            // Uncap Chromium frame rate limit and boost WebGL GPU rasterization
            try {
                val cmdLineClass = Class.forName("org.chromium.base.CommandLine")
                val isInit = cmdLineClass.getMethod("isInitialized")
                if (java.lang.Boolean.TRUE == isInit.invoke(null)) {
                    val getInstance = cmdLineClass.getMethod("getInstance")
                    val instance = getInstance.invoke(null)
                    val appendSwitch = cmdLineClass.getMethod("appendSwitch", String::class.java)
                    val appendSwitchWithValue = cmdLineClass.getMethod(
                        "appendSwitchWithValue",
                        String::class.java,
                        String::class.java
                    )

                    // 1. Uncap frame rates & eliminate internal vsync pipeline stalls
                    appendSwitch.invoke(instance, "disable-frame-rate-limit")
                    appendSwitch.invoke(instance, "disable-gpu-vsync")
                    appendSwitchWithValue.invoke(instance, "max-gum-fps", "120")

                    // 2. High performance hardware GPU rasterization & zero-copy direct buffers
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

                    // 3. Multi-threaded rasterization across Snapdragon high-performance cores
                    appendSwitchWithValue.invoke(instance, "num-raster-threads", "4")
                    // Disable heavy MSAA resolve on camera movements to save 2-3ms frame time
                    appendSwitchWithValue.invoke(instance, "gpu-rasterization-msaa-sample-count", "0")

                    // 4. Input & Touch Resampling: Prevents 1000Hz touch digitizer from choking JavaScript during camera rotation
                    appendSwitchWithValue(
                        instance,
                        "enable-features",
                        "ResamplingInputEvents,ResamplingScrollEvents,PassiveDocumentEventListeners,CanvasOopRasterization,UseSkiaRenderer"
                    )
                }
            } catch (_: Throwable) {
            }

            Cefrium.downloadAndLoadEasyList(this)
            Cefrium.loadYouTubeAdRules()
        }
    }
}
