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
                    appendSwitch.invoke(instance, "disable-frame-rate-limit")
                    appendSwitch.invoke(instance, "enable-gpu-rasterization")
                    appendSwitch.invoke(instance, "ignore-gpu-blocklist")
                }
            } catch (_: Throwable) {
            }

            Cefrium.downloadAndLoadEasyList(this)
            Cefrium.loadYouTubeAdRules()
        }
    }
}
