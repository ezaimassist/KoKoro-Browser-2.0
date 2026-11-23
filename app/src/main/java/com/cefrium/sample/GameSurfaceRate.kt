package com.cefrium.sample

import android.os.Build
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.annotation.RequiresApi

/**
 * High-performance frame rate controller for games.
 * Locks the display and all rendering surfaces to the target high refresh rate (e.g., 120 Hz).
 * Uses FIXED_SOURCE compatibility and CHANGE_FRAME_RATE_ALWAYS to prevent Android/RedMagic
 * dynamic LTPO governors from dropping to 24-30 Hz when the user is idle/not touching the screen.
 */
@RequiresApi(30)
internal class GameSurfaceRate(private val root: View, private val hz: Float) :
    SurfaceHolder.Callback, ViewTreeObserver.OnGlobalLayoutListener {

    private val trackedHolders = mutableSetOf<SurfaceHolder>()
    private var isRunning = false

    var requested = false
        private set

    // Active VSYNC heartbeat to ensure Choreographer stays warm at 120 Hz even when idle
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
            if (Build.VERSION.SDK_INT >= 31) {
                surface.setFrameRate(
                    rate,
                    Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                    Surface.CHANGE_FRAME_RATE_ALWAYS
                )
            } else {
                surface.setFrameRate(
                    rate,
                    Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE
                )
            }
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
                try {
                    sv.setFrameRateCategory(View.FRAME_RATE_CATEGORY_HIGH)
                    sv.requestedFrameRate = hz
                } catch (_: Throwable) {}
            }

            val holder = sv.holder
            if (holder !in trackedHolders) {
                trackedHolders.add(holder)
                holder.addCallback(this)
            }
            if (vote(holder, hz)) {
                anyVoted = true
            }
        }
        if (anyVoted) {
            requested = true
        }
    }

    override fun onGlobalLayout() {
        if (!isRunning) return
        scanAndVote()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (vote(holder, hz)) requested = true
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (vote(holder, hz)) requested = true
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        holder.removeCallback(this)
        trackedHolders.remove(holder)
        if (trackedHolders.isEmpty()) {
            requested = false
        }
    }

    private fun detach() {
        for (holder in trackedHolders) {
            try {
                holder.removeCallback(this)
                if (holder.surface.isValid) {
                    if (Build.VERSION.SDK_INT >= 31) {
                        holder.surface.setFrameRate(
                            0f,
                            Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                            Surface.CHANGE_FRAME_RATE_ALWAYS
                        )
                    } else {
                        holder.surface.setFrameRate(0f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
                    }
                }
            } catch (_: Exception) {}
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
