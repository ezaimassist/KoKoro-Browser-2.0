package com.cefrium.sample;

import android.app.Application;

import com.cefrium.Cefrium;
import org.chromium.base.ContextUtils;

/**
 * The Cefrium SDK initializes the engine automatically at app launch
 * (via CefriumInitProvider) — no init boilerplate is required. This Application
 * exists only to kick off the optional EasyList ad-block download once the engine
 * is up; an app that does not need ad-blocking needs no Application at all.
 */
public class SampleApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        if (!ContextUtils.getProcessName().contains(":")) {
            // Uncap Chromium frame rate limit and boost WebGL GPU rasterization
            try {
                Class<?> cmdLineClass = Class.forName("org.chromium.base.CommandLine");
                java.lang.reflect.Method isInit = cmdLineClass.getMethod("isInitialized");
                if (Boolean.TRUE.equals(isInit.invoke(null))) {
                    java.lang.reflect.Method getInstance = cmdLineClass.getMethod("getInstance");
                    Object instance = getInstance.invoke(null);
                    java.lang.reflect.Method appendSwitch = cmdLineClass.getMethod("appendSwitch", String.class);
                    appendSwitch.invoke(instance, "disable-frame-rate-limit");
                    appendSwitch.invoke(instance, "enable-gpu-rasterization");
                    appendSwitch.invoke(instance, "ignore-gpu-blocklist");
                }
            } catch (Throwable ignored) {
            }

            Cefrium.downloadAndLoadEasyList(this);
            Cefrium.loadYouTubeAdRules();
        }
    }
}
