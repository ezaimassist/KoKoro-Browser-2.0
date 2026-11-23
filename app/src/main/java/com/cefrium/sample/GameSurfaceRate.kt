package com.cefrium.sample

import android.graphics.PixelFormat
import android.os.Build
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver

/**
 * High-performance Android 12+ frame rate controller for KoKoro Gaming Boost.
 * Locks all active rendering surfaces to the chosen target refresh rate (60/90/120 Hz)
 * using FIXED_SOURCE + CHANGE_FRAME_RATE_ALWAYS and API 35+ frame-rate category hints
 * to prevent dynamic LTPO governors from dropping frame pacing mid-session.
 */
internal class GameSurfaceRate(
    private val root: View,
    private val targetHz: Float,
) : SurfaceHolder.Callback, ViewTreeObserver.OnGlobalLayoutListener {

    private val trackedHolders = mutableSetOf<SurfaceHolder>()
    private var isRunning = false

    @Volatile
    var requested: Boolean = false
        private set

    private val framePump = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (isRunning) {
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        root.viewTreeObserver.addOnGlobalLayoutListener(this)
        scanAndVote()
        Choreographer.getInstance().postFrameCallback(framePump)
    }

    private fun findAllSurfaces(view: View, out: MutableList<SurfaceView>) {
        if (!view.isShown && view !== root) return
        if (view is SurfaceView) {
            out.add(view)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findAllSurfaces(view.getChildAt(i), out)
            }
        }
    }

    private fun vote(target: SurfaceHolder, rate: Float): Boolean {
        val surface = target.surface
        if (!surface.isValid) return false
        return try {
            surface.setFrameRate(
                rate,
                Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                Surface.CHANGE_FRAME_RATE_ALWAYS,
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun scanAndVote() {
        val surfaces = mutableListOf<SurfaceView>()
        findAllSurfaces(root, surfaces)

        var anyVoted = false
        for (sv in surfaces) {
            if (Build.VERSION.SDK_INT >= 35) {
                runCatching {
                    val method = View::class.java.getMethod(
                        "setFrameRateCategory",
                        Int::class.javaPrimitiveType,
                    )
                    method.invoke(sv, 2) // FRAME_RATE_CATEGORY_HIGH
                }
                runCatching { sv.requestedFrameRate = targetHz }
            }

            val holder = sv.holder
            runCatching { holder.setFormat(PixelFormat.OPAQUE) }
            if (holder !in trackedHolders) {
                trackedHolders.add(holder)
                holder.addCallback(this)
            }
            if (vote(holder, targetHz)) {
                anyVoted = true
            }
        }
        if (anyVoted) {
            requested = true
        }
    }

    override fun onGlobalLayout() {
        if (isRunning) scanAndVote()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (vote(holder, targetHz)) requested = true
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (vote(holder, targetHz)) requested = true
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        holder.removeCallback(this)
        trackedHolders.remove(holder)
        if (trackedHolders.isEmpty()) requested = false
    }

    private fun detach() {
        for (holder in trackedHolders) {
            runCatching {
                holder.removeCallback(this)
                if (holder.surface.isValid) {
                    holder.surface.setFrameRate(
                        0f,
                        Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                        Surface.CHANGE_FRAME_RATE_ALWAYS,
                    )
                }
            }
        }
        trackedHolders.clear()
        requested = false
    }

    fun stop() {
        isRunning = false
        if (root.viewTreeObserver.isAlive) {
            root.viewTreeObserver.removeOnGlobalLayoutListener(this)
        }
        detach()
    }
}
