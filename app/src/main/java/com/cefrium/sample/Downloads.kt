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
 * Routes notification and sheet actions (Cancel/Pause/Resume) to the owning tab by download id.
 */
object DownloadRegistry {
    private val controllers = ConcurrentHashMap<Int, (Int) -> Unit>()

    fun register(id: Int, control: (Int) -> Unit) {
        controllers[id] = control
    }

    fun unregister(id: Int) {
        controllers.remove(id)
    }

    fun control(id: Int, action: Int) {
        controllers[id]?.invoke(action)
    }
}

class DownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_DOWNLOAD_ID, -1)
        val action = intent.getIntExtra(EXTRA_DOWNLOAD_ACTION, -1)
        if (id >= 0 && action >= 0) {
            DownloadRegistry.control(id, action)
        }
    }

    companion object {
        const val ACTION_DOWNLOAD = "com.aistudio.kokoro.browser.DOWNLOAD_ACTION"
        const val EXTRA_DOWNLOAD_ID = "com.aistudio.kokoro.browser.download_id"
        const val EXTRA_DOWNLOAD_ACTION = "com.aistudio.kokoro.browser.download_action"
    }
}

class DownloadNotifier(private val context: Context) {

    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                "Download KoKoro",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Avanzamento e completamento dei download di KoKoro Browser"
            },
        )
    }

    private fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun update(entry: DownloadEntry) {
        if (!canPost()) return
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setContentTitle(entry.name)

        when (entry.state) {
            DownloadState.STARTED, DownloadState.IN_PROGRESS -> {
                val indeterminate = entry.totalBytes <= 0L
                builder.setSmallIcon(android.R.drawable.stat_sys_download)
                    .setOngoing(true)
                    .setContentText(
                        if (indeterminate) "Download in corso…"
                        else "${entry.percent}% · ${formatFileSize(entry.receivedBytes)} / ${formatFileSize(entry.totalBytes)}",
                    )
                    .setProgress(100, entry.percent.coerceIn(0, 100), indeterminate)
                    .addAction(actionPause(entry.id))
                    .addAction(actionCancel(entry.id))
            }
            DownloadState.PAUSED -> {
                builder.setSmallIcon(android.R.drawable.stat_sys_download)
                    .setOngoing(false)
                    .setContentText("In pausa · ${entry.percent}%")
                    .setProgress(100, entry.percent.coerceIn(0, 100), false)
                    .addAction(actionResume(entry.id))
                    .addAction(actionCancel(entry.id))
            }
            DownloadState.COMPLETE -> {
                builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .setContentText("Download completato")
                openIntent(entry)?.let { builder.setContentIntent(it) }
            }
            DownloadState.CANCELED -> {
                nm.cancel(entry.id)
                return
            }
        }
        nm.notify(entry.id, builder.build())
    }

    private fun openIntent(entry: DownloadEntry): PendingIntent? {
        val intent = openFileIntent(context, entry.path, entry.mimeType) ?: return null
        return PendingIntent.getActivity(
            context,
            entry.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun actionCancel(id: Int) = NotificationCompat.Action(0, "Annulla", actionIntent(id, 0))
    private fun actionPause(id: Int) = NotificationCompat.Action(0, "Pausa", actionIntent(id, 1))
    private fun actionResume(id: Int) = NotificationCompat.Action(0, "Riprendi", actionIntent(id, 2))

    private fun actionIntent(id: Int, action: Int): PendingIntent {
        val intent = Intent(context, DownloadActionReceiver::class.java).apply {
            this.action = DownloadActionReceiver.ACTION_DOWNLOAD
            putExtra(DownloadActionReceiver.EXTRA_DOWNLOAD_ID, id)
            putExtra(DownloadActionReceiver.EXTRA_DOWNLOAD_ACTION, action)
        }
        return PendingIntent.getBroadcast(
            context,
            id * 8 + action,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val CHANNEL = "kokoro_downloads"
    }
}

fun formatFileSize(bytes: Long): String = when {
    bytes <= 0 -> "0 B"
    bytes >= 1_000_000_000 -> "%.2f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}

fun openFileIntent(context: Context, path: String, mimeType: String): Intent? {
    if (path.isBlank()) return null
    val file = File(path)
    if (!file.exists()) return null
    val uri: Uri = try {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (_: IllegalArgumentException) {
        return null
    }
    val mime = mimeType.takeIf { it.isNotBlank() }
        ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
        ?: "*/*"
    return Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

fun openDownloadedFile(activity: Activity, path: String, mimeType: String) {
    val intent = openFileIntent(activity, path, mimeType) ?: return
    runCatching { activity.startActivity(intent) }
}

fun contentUriFor(context: Context, path: String): Uri? {
    if (path.isBlank()) return null
    val file = File(path)
    if (!file.exists()) return null
    return try {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (_: IllegalArgumentException) {
        null
    }
}

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
