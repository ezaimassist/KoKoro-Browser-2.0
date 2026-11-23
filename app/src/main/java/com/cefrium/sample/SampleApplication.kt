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
            // Uncap Chromium frame rate limit, enable V8 WASM tier-up, and boost WebGL GPU rasterization
            try {
                val cmdLineClass = Class.forName("org.chromium.base.CommandLine")
                val isInit = cmdLineClass.getMethod("isInitialized")
                if (java.lang.Boolean.FALSE == isInit.invoke(null)) {
                    try {
                        val initMethod = cmdLineClass.getMethod("init", Array<String>::class.java)
                        initMethod.invoke(null, arrayOf<String>("pulsebrowser"))
                    } catch (_: Throwable) {}
                }

                val getInstance = cmdLineClass.getMethod("getInstance")
                val instance = getInstance.invoke(null)
                if (instance != null) {
                    val appendSwitch = cmdLineClass.getMethod("appendSwitch", String::class.java)
                    val appendSwitchWithValue = cmdLineClass.getMethod(
                        "appendSwitchWithValue",
                        String::class.java,
                        String::class.java
                    )

                    // 1. Uncap Blink/V8 frame rate limit and set max input sampling to 144Hz
                    appendSwitch.invoke(instance, "disable-frame-rate-limit")
                    appendSwitchWithValue.invoke(instance, "max-gum-fps", "144")

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

                    // 3. Massive 2GB GPU memory pool & 512MB shader cache (stops texture eviction stutter in Ultra)
                    appendSwitchWithValue.invoke(instance, "force-gpu-mem-available-mb", "2048")
                    appendSwitchWithValue.invoke(instance, "max-total-gpu-memory-mb", "2048")
                    appendSwitchWithValue.invoke(instance, "gpu-program-cache-size-kb", "524288")

                    // 4. Multi-threaded rasterization across Snapdragon high-performance cores
                    appendSwitchWithValue.invoke(instance, "num-raster-threads", "4")
                    appendSwitchWithValue.invoke(instance, "gpu-rasterization-msaa-sample-count", "0")

                    // 5. V8 WASM tier-up for instant native execution speed
                    appendSwitchWithValue.invoke(instance, "js-flags", "--wasm-tier-up")

                    // 6. Input Resampling & Modern Graphics Pipeline: Prevents 1000Hz touch digitizer from choking JavaScript
                    appendSwitchWithValue(
                        instance,
                        "enable-features",
                        "ResamplingInputEvents,ResamplingScrollEvents,PassiveDocumentEventListeners,CanvasOopRasterization,UseSkiaRenderer,WebAssemblyThreads,WebAssemblySimd"
                    )
                }
            } catch (_: Throwable) {
            }

            Cefrium.downloadAndLoadEasyList(this)
            Cefrium.loadYouTubeAdRules()
        }
    }
}
