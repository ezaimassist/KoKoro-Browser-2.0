package com.cefrium.sample

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.cefrium.compose.DownloadEntry
import com.cefrium.compose.DownloadState
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Routes notification-action taps (Cancel/Pause/Resume) to the tab that owns the
 * download, keyed by download id. Populated by the browser when a download
 * starts (see MainActivity onDownloadEvent) and cleared when it finishes.
 */
object DownloadRegistry {
    // action: 0=cancel, 1=pause, 2=resume.
    private val controllers = ConcurrentHashMap<Int, (Int) -> Unit>()

    fun register(id: Int, control: (Int) -> Unit) { controllers[id] = control }
    fun unregister(id: Int) { controllers.remove(id) }
    fun control(id: Int, action: Int) { controllers[id]?.invoke(action) }
}

/** Handles Cancel/Pause/Resume actions from a download notification. */
class DownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_DOWNLOAD_ID, -1)
        val action = intent.getIntExtra(EXTRA_DOWNLOAD_ACTION, -1)
        if (id >= 0 && action >= 0) {
            DownloadRegistry.control(id, action)
        }
    }

    companion object {
        const val ACTION_DOWNLOAD = "com.cefrium.browser.DOWNLOAD_ACTION"
        const val EXTRA_DOWNLOAD_ID = "com.cefrium.browser.download_id"
        const val EXTRA_DOWNLOAD_ACTION = "com.cefrium.browser.download_action"
    }
}

/**
 * Posts a system notification per download (start -> progress -> complete),
 * like other browsers. The notification id is the download id, so concurrent
 * downloads get their own line and update in place. Tapping a completed
 * download opens the file. No-ops silently if POST_NOTIFICATIONS is not granted
 * (the download still happens and still shows in the Downloads list).
 */
class DownloadNotifier(private val context: Context) {

    private val nm =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Download progress and completion" }
            )
        }
    }

    private fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun update(entry: DownloadEntry) {
        if (!canPost()) return
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setContentTitle(entry.name)

        when (entry.state) {
            DownloadState.STARTED, DownloadState.IN_PROGRESS -> {
                val indeterminate = entry.totalBytes <= 0L
                b.setSmallIcon(android.R.drawable.stat_sys_download)
                    .setOngoing(true)
                    .setContentText(
                        if (indeterminate) "Downloading..."
                        else "${entry.percent}%  ${sizeStr(entry.receivedBytes)} / ${sizeStr(entry.totalBytes)}"
                    )
                    .setProgress(100, entry.percent.coerceIn(0, 100), indeterminate)
                    .addAction(actionPause(entry.id))
                    .addAction(actionCancel(entry.id))
            }
            DownloadState.PAUSED -> {
                b.setSmallIcon(android.R.drawable.stat_sys_download)
                    .setOngoing(false)
                    .setContentText("Paused  ${entry.percent}%")
                    .setProgress(100, entry.percent.coerceIn(0, 100), false)
                    .addAction(actionResume(entry.id))
                    .addAction(actionCancel(entry.id))
            }
            DownloadState.COMPLETE -> {
                b.setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .setContentText("Download complete")
                openIntent(entry)?.let { b.setContentIntent(it) }
            }
            DownloadState.CANCELED -> {
                nm.cancel(entry.id)
                return
            }
        }
        nm.notify(entry.id, b.build())
    }

    private fun openIntent(entry: DownloadEntry): PendingIntent? {
        val intent = openFileIntent(context, entry.path, entry.mimeType) ?: return null
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getActivity(context, entry.id, intent, flags)
    }

    private fun actionCancel(id: Int) =
        NotificationCompat.Action(0, "Cancel", actionIntent(id, 0))

    private fun actionPause(id: Int) =
        NotificationCompat.Action(0, "Pause", actionIntent(id, 1))

    private fun actionResume(id: Int) =
        NotificationCompat.Action(0, "Resume", actionIntent(id, 2))

    // action: 0=cancel, 1=pause, 2=resume. Unique requestCode per (id,action) so
    // PendingIntents don't collide.
    private fun actionIntent(id: Int, action: Int): PendingIntent {
        val intent = Intent(context, DownloadActionReceiver::class.java).apply {
            this.action = DownloadActionReceiver.ACTION_DOWNLOAD
            putExtra(DownloadActionReceiver.EXTRA_DOWNLOAD_ID, id)
            putExtra(DownloadActionReceiver.EXTRA_DOWNLOAD_ACTION, action)
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(context, id * 8 + action, intent, flags)
    }

    private fun sizeStr(bytes: Long): String = when {
        bytes <= 0 -> "0 B"
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }

    companion object {
        private const val CHANNEL = "cefrium_downloads"
    }
}

/** Builds an ACTION_VIEW intent for a downloaded file via FileProvider. */
fun openFileIntent(context: Context, path: String, mimeType: String): Intent? {
    if (path.isBlank()) return null
    val file = File(path)
    if (!file.exists()) return null
    val uri: Uri = try {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (e: IllegalArgumentException) {
        return null
    }
    val mime = mimeType.takeIf { it.isNotBlank() }
        ?: MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
    return Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/** Opens a downloaded file, or shows nothing if it cannot be opened. */
fun openDownloadedFile(activity: Activity, path: String, mimeType: String) {
    val intent = openFileIntent(activity, path, mimeType) ?: return
    runCatching { activity.startActivity(intent) }
}

/**
 * A readable content:// URI for a local file (via FileProvider). Loading this in
 * a tab works from the sandboxed renderer -- a file:// path does not, since the
 * renderer has no storage access. Returns null if the file is gone or outside
 * the provider's configured paths.
 */
fun contentUriFor(context: Context, path: String): Uri? {
    if (path.isBlank()) return null
    val file = File(path)
    if (!file.exists()) return null
    return try {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (e: IllegalArgumentException) {
        null
    }
}

/**
 * True if a browser tab can render this file itself (text, HTML, PDF, image), so
 * the download can open in Cefrium instead of an external app.
 */
fun isBrowserViewable(mimeType: String, name: String): Boolean {
    val mime = mimeType.takeIf { it.isNotBlank() }
        ?: MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
        ?: ""
    return mime.startsWith("text/") ||
        mime.startsWith("image/") ||
        mime == "application/pdf" ||
        mime == "application/xhtml+xml"
}
