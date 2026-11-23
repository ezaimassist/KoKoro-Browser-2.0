package com.cefrium.sample.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cefrium.CefriumBrowser
import com.cefrium.compose.CefriumNavigator
import com.cefrium.compose.ContextMenuInfo
import com.cefrium.compose.DownloadEntry
import com.cefrium.compose.DownloadState
import com.cefrium.sample.BrowserTab
import com.cefrium.sample.DownloadRegistry
import com.cefrium.sample.data.BookmarkItem
import com.cefrium.sample.data.HistoryItem
import com.cefrium.sample.formatFileSize
import com.cefrium.sample.theme.LocalSamuraiPalette
import com.cefrium.sample.theme.SamuraiCard
import com.cefrium.sample.theme.seigaihaBackdrop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SamuraiTabGridSheet(
    tabs: List<BrowserTab>,
    activeTabId: Int,
    onSelectTab: (Int) -> Unit,
    onCloseTab: (Int) -> Unit,
    onNewTab: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val samurai = LocalSamuraiPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .seigaihaBackdrop(samurai.seigaihaStroke)
                .padding(16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Schede Aperte (${tabs.size})",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = { onNewTab(true); onDismiss() },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Rounded.VisibilityOff, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Privata", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.width(6.dp))
                Button(
                    onClick = { onNewTab(false); onDismiss() },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Nuova", style = MaterialTheme.typography.labelSmall)
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 440.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(tabs, key = { it.id }) { tab ->
                    val active = tab.id == activeTabId
                    val incognito = tab.state.incognito
                    val title = tab.state.pageTitle?.takeIf { it.isNotBlank() && it != "about:blank" }
                        ?: tab.restoredTitle?.takeIf { it.isNotBlank() }
                        ?: "Nuova scheda"
                    val url = tab.state.lastLoadedUrl ?: tab.state.initialUrl ?: ""

                    Surface(
                        onClick = { onSelectTab(tab.id); onDismiss() },
                        shape = RoundedCornerShape(16.dp),
                        color = if (incognito) samurai.incognitoSurface else MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(
                            width = if (active) 1.75.dp else 1.dp,
                            color = when {
                                active -> samurai.goldAccent
                                incognito -> samurai.incognitoAccent.copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            },
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (incognito) Icons.Rounded.VisibilityOff else Icons.Rounded.Public,
                                    contentDescription = null,
                                    tint = if (incognito) samurai.incognitoAccent else samurai.goldAccent,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = { onCloseTab(tab.id) },
                                    modifier = Modifier.size(26.dp),
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Chiudi", modifier = Modifier.size(15.dp))
                                }
                            }
                            Text(
                                text = url.ifBlank { "about:blank" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (tab.discarded.value) {
                                Text(
                                    text = "RAM RISPARMIATA",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            } else if (active) {
                                Text(
                                    text = "ATTIVA",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = samurai.goldAccent,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksSheet(
    bookmarks: List<BookmarkItem>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var filter by remember { mutableStateOf("") }
    val filtered = remember(bookmarks, filter) {
        if (filter.isBlank()) bookmarks
        else bookmarks.filter {
            it.title.contains(filter, ignoreCase = true) || it.url.contains(filter, ignoreCase = true)
        }
    }
    SamuraiLibrarySheet(
        title = "Preferiti",
        icon = Icons.Rounded.Bookmarks,
        searchQuery = filter,
        onSearchChange = { filter = it },
        emptyMessage = "Nessun preferito salvato. Tocca la stella nella barra per aggiungerne uno.",
        onClearAll = null,
        onDismiss = onDismiss,
    ) {
        if (filtered.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered, key = { it.url }) { item ->
                    SamuraiCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(item.url); onDismiss() }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { onDelete(item.url) }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Elimina preferito")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySheet(
    history: List<HistoryItem>,
    onOpen: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    var filter by remember { mutableStateOf("") }
    val filtered = remember(history, filter) {
        if (filter.isBlank()) history
        else history.filter {
            it.title.contains(filter, ignoreCase = true) || it.url.contains(filter, ignoreCase = true)
        }
    }
    SamuraiLibrarySheet(
        title = "Cronologia",
        icon = Icons.Rounded.History,
        searchQuery = filter,
        onSearchChange = { filter = it },
        emptyMessage = "La cronologia di navigazione è vuota.",
        onClearAll = if (history.isNotEmpty()) onClearAll else null,
        onDismiss = onDismiss,
    ) {
        if (filtered.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered, key = { "${it.url}_${it.timestamp}" }) { item ->
                    SamuraiCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(item.url); onDismiss() }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsSheet(
    downloads: List<DownloadEntry>,
    onOpen: (DownloadEntry) -> Unit,
    onClearCompleted: () -> Unit,
    onDismiss: () -> Unit,
) {
    val samurai = LocalSamuraiPalette.current
    val hasClearable = downloads.any { it.state == DownloadState.COMPLETE || it.state == DownloadState.CANCELED }

    SamuraiLibrarySheet(
        title = "Download Manager",
        icon = Icons.Rounded.Download,
        searchQuery = null,
        onSearchChange = {},
        emptyMessage = "Nessun download in questa sessione.",
        onClearAll = if (hasClearable) onClearCompleted else null,
        onDismiss = onDismiss,
    ) {
        if (downloads.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(downloads, key = { it.id }) { entry ->
                    SamuraiCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = entry.state == DownloadState.COMPLETE) {
                                    onOpen(entry)
                                    onDismiss()
                                }
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val statusText = when (entry.state) {
                                        DownloadState.COMPLETE -> "Completato · ${formatFileSize(entry.totalBytes.coerceAtLeast(entry.receivedBytes))}"
                                        DownloadState.CANCELED -> "Annullato"
                                        DownloadState.PAUSED -> "In pausa · ${entry.percent}%"
                                        else -> "${entry.percent}% · ${formatFileSize(entry.receivedBytes)} / ${formatFileSize(entry.totalBytes)}"
                                    }
                                    Text(statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (entry.state == DownloadState.IN_PROGRESS || entry.state == DownloadState.STARTED) {
                                    IconButton(onClick = { DownloadRegistry.control(entry.id, 1) }) {
                                        Icon(Icons.Rounded.Pause, contentDescription = "Pausa")
                                    }
                                    IconButton(onClick = { DownloadRegistry.control(entry.id, 0) }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Annulla")
                                    }
                                } else if (entry.state == DownloadState.PAUSED) {
                                    IconButton(onClick = { DownloadRegistry.control(entry.id, 2) }) {
                                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Riprendi", tint = samurai.goldAccent)
                                    }
                                    IconButton(onClick = { DownloadRegistry.control(entry.id, 0) }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Annulla")
                                    }
                                }
                            }
                            if (entry.state == DownloadState.IN_PROGRESS || entry.state == DownloadState.STARTED || entry.state == DownloadState.PAUSED) {
                                LinearProgressIndicator(
                                    progress = { (entry.percent.coerceIn(0, 100)) / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(CircleShape),
                                    color = samurai.goldAccent,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SamuraiLibrarySheet(
    title: String,
    icon: ImageVector,
    searchQuery: String?,
    onSearchChange: (String) -> Unit,
    emptyMessage: String,
    onClearAll: (() -> Unit)?,
    onDismiss: () -> Unit,
    listContent: @Composable () -> Unit,
) {
    val samurai = LocalSamuraiPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .seigaihaBackdrop(samurai.seigaihaStroke)
                .padding(16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = title, tint = samurai.goldAccent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                if (onClearAll != null) {
                    TextButton(onClick = onClearAll) {
                        Text("Svuota", color = MaterialTheme.colorScheme.error)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Chiudi")
                }
            }

            if (searchQuery != null) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    placeholder = { Text("Filtra $title…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                listContent()
            }

            // Friendly fallback note if list has zero height is handled by caller or shown nicely
            Text(
                text = emptyMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            )
        }
    }
}

private const val MENU_ID_OPEN_IN_NEW_TAB = 1

@Composable
fun SamuraiContextMenuDialog(
    menu: ContextMenuInfo,
    navigator: CefriumNavigator,
    activeIncognito: Boolean,
    onOpenInNewTab: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val samurai = LocalSamuraiPalette.current
    val items = menu.menuItems.split("\n").mapNotNull { line ->
        val parts = line.split("|", limit = 2)
        parts[0].toIntOrNull()?.let { it to (parts.getOrNull(1) ?: "") }
    }
    AlertDialog(
        onDismissRequest = { navigator.contextMenuCommand(-1); onDismiss() },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { navigator.contextMenuCommand(-1); onDismiss() }) {
                Text("Annulla", color = samurai.goldAccent)
            }
        },
        title = {
            Text(
                text = menu.url.take(52),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items.forEach { (id, label) ->
                    val display = if (id == MENU_ID_OPEN_IN_NEW_TAB && activeIncognito) {
                        "Apri in scheda privata"
                    } else {
                        label
                    }
                    Text(
                        text = display,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                if (id == MENU_ID_OPEN_IN_NEW_TAB && menu.url.isNotEmpty()) {
                                    navigator.contextMenuCommand(-1)
                                    onOpenInNewTab(menu.url, activeIncognito)
                                } else {
                                    navigator.contextMenuCommand(id)
                                }
                                onDismiss()
                            }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                }
                if (!activeIncognito && menu.url.isNotEmpty() && items.any { it.first == MENU_ID_OPEN_IN_NEW_TAB }) {
                    Text(
                        text = "Apri in scheda privata",
                        style = MaterialTheme.typography.bodyLarge,
                        color = samurai.incognitoAccent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                navigator.contextMenuCommand(-1)
                                onOpenInNewTab(menu.url, true)
                                onDismiss()
                            }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                }
            }
        },
    )
}

class PermissionPromptState(
    val origin: String,
    val permissions: Int,
    val callback: CefriumBrowser.PermissionCallback,
)

class DevicePickerState(val session: CefriumBrowser.DeviceChooserSession) {
    val devices = androidx.compose.runtime.mutableStateListOf<CefriumBrowser.ChooserDevice>()
    fun add(d: CefriumBrowser.ChooserDevice) {
        val i = devices.indexOfFirst { it.id == d.id }
        if (i >= 0) devices[i] = d else devices.add(d)
    }
    fun remove(id: String) {
        devices.removeAll { it.id == id }
    }
    fun typeName(): String = when (session.type) {
        CefriumBrowser.DEVICE_BLUETOOTH -> "Bluetooth"
        CefriumBrowser.DEVICE_USB -> "USB"
        CefriumBrowser.DEVICE_SERIAL -> "Seriale"
        else -> "dispositivo"
    }
}

@Composable
fun SamuraiPermissionDialog(
    prompt: PermissionPromptState,
    onResult: (Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onResult(false) },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("Richiesta autorizzazione", style = MaterialTheme.typography.titleLarge) },
        text = { Text("${prompt.origin} richiede accesso ai permessi (${prompt.permissions}).") },
        confirmButton = {
            Button(onClick = { onResult(true) }, modifier = Modifier.testTag("permission_allow_btn")) {
                Text("Consenti")
            }
        },
        dismissButton = {
            TextButton(onClick = { onResult(false) }, modifier = Modifier.testTag("permission_deny_btn")) {
                Text("Nega")
            }
        },
    )
}

@Composable
fun SamuraiDevicePickerDialog(
    picker: DevicePickerState,
    onSelect: (String) -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("Seleziona dispositivo ${picker.typeName()}", style = MaterialTheme.typography.titleLarge) },
        text = {
            if (picker.devices.isEmpty()) {
                Text("Ricerca dispositivi in corso…", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                    items(picker.devices, key = { it.id }) { d ->
                        Text(
                            text = if (d.name.isNullOrEmpty()) d.id else "${d.name}\n${d.id}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(d.id) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Annulla") }
        },
    )
}
