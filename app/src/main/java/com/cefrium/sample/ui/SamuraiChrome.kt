package com.cefrium.sample.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.cefrium.sample.BrowserTab
import com.cefrium.sample.data.BrowserPreferences
import com.cefrium.sample.engine.HardwareTelemetry
import com.cefrium.sample.engine.ThermalLevel
import com.cefrium.sample.theme.LocalSamuraiPalette
import com.cefrium.sample.viewmodel.BlockedPopupNotice
import kotlinx.coroutines.launch

@Composable
fun SamuraiTabStrip(
    tabs: List<BrowserTab>,
    activeTabId: Int,
    tabListState: LazyListState,
    onSelectTab: (Int) -> Unit,
    onCloseTab: (Int) -> Unit,
    onReorderTabs: (Int, Int) -> Unit,
    onAddTab: () -> Unit,
    onOpenTabGrid: () -> Unit,
) {
    val samurai = LocalSamuraiPalette.current
    val haptics = LocalHapticFeedback.current
    val dragScope = rememberCoroutineScope()
    var draggedTabId by remember { mutableStateOf<Int?>(null) }
    var draggedDx by remember { mutableFloatStateOf(0f) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            state = tabListState,
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            itemsIndexed(tabs, key = { _, tab -> tab.id }) { _, tab ->
                val isActive = tab.id == activeTabId
                val isDragged = tab.id == draggedTabId
                val isIncognito = tab.state.incognito
                val chipBorder = when {
                    isActive && isIncognito -> samurai.incognitoAccent
                    isActive -> samurai.goldAccent.copy(alpha = 0.78f)
                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                }
                val chipBg = when {
                    isActive && isIncognito -> samurai.incognitoSurface
                    isActive -> MaterialTheme.colorScheme.surfaceContainerHighest
                    else -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.65f)
                }

                Row(
                    modifier = Modifier
                        .zIndex(if (isDragged) 2f else 0f)
                        .then(
                            if (isDragged) Modifier.graphicsLayer { translationX = draggedDx }
                            else if (draggedTabId != null) Modifier.animateItem()
                            else Modifier,
                        )
                        .pointerInput(tab.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggedTabId = tab.id
                                    draggedDx = 0f
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragEnd = {
                                    val settling = draggedTabId
                                    dragScope.launch {
                                        animate(draggedDx, 0f, animationSpec = tween(160)) { v, _ ->
                                            if (draggedTabId == settling) draggedDx = v
                                        }
                                        if (draggedTabId == settling) {
                                            draggedTabId = null
                                            draggedDx = 0f
                                        }
                                    }
                                },
                                onDragCancel = {
                                    draggedTabId = null
                                    draggedDx = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    draggedDx += amount.x
                                    val info = tabListState.layoutInfo
                                    val cur = info.visibleItemsInfo.firstOrNull { it.key == draggedTabId }
                                        ?: return@detectDragGesturesAfterLongPress
                                    val middle = cur.offset + draggedDx + cur.size / 2f
                                    val target = info.visibleItemsInfo.firstOrNull {
                                        it.key != draggedTabId &&
                                            middle >= it.offset &&
                                            middle <= it.offset + it.size
                                    }
                                    if (target != null) {
                                        val from = tabs.indexOfFirst { it.id == draggedTabId }
                                        val to = tabs.indexOfFirst { it.id == target.key }
                                        if (from in tabs.indices && to in tabs.indices && from != to) {
                                            onReorderTabs(from, to)
                                            draggedDx += (cur.offset - target.offset)
                                        }
                                    }
                                },
                            )
                        }
                        .clip(RoundedCornerShape(10.dp))
                        .background(chipBg)
                        .border(1.dp, chipBorder, RoundedCornerShape(10.dp))
                        .clickable { onSelectTab(tab.id) }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isIncognito) {
                        Icon(
                            imageVector = Icons.Rounded.VisibilityOff,
                            contentDescription = "Scheda privata",
                            tint = samurai.incognitoAccent,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    } else if (isActive) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(samurai.goldAccent),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = (tab.state.pageTitle?.takeIf { it.isNotBlank() && it != "about:blank" }
                            ?: tab.restoredTitle?.takeIf { it.isNotBlank() }
                            ?: "Nuova scheda").take(16),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .clickable {
                                draggedTabId = null
                                draggedDx = 0f
                                onCloseTab(tab.id)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Chiudi scheda",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(4.dp))

        // Tab count badge -> opens full Samurai Tab Grid Switcher
        Surface(
            onClick = onOpenTabGrid,
            shape = RoundedCornerShape(9.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, samurai.goldAccent.copy(alpha = 0.55f)),
            modifier = Modifier
                .size(width = 36.dp, height = 34.dp)
                .testTag("tab_switcher_button"),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = tabs.size.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = samurai.goldAccent,
                )
            }
        }

        Spacer(Modifier.width(2.dp))

        IconButton(
            onClick = onAddTab,
            modifier = Modifier
                .size(40.dp)
                .testTag("new_tab_button"),
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "Nuova scheda",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
fun SamuraiOmnibar(
    urlText: String,
    editingUrl: Boolean,
    incognito: Boolean,
    bookmarked: Boolean,
    zenReaderActive: Boolean,
    showFind: Boolean,
    prefs: BrowserPreferences,
    telemetry: HardwareTelemetry,
    onEditingChange: (Boolean) -> Unit,
    onCommitUrl: (String) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleFind: () -> Unit,
    onOpenControlCenter: () -> Unit,
) {
    val samurai = LocalSamuraiPalette.current
    val pageInk = MaterialTheme.colorScheme.onSurface
    val fieldFill = MaterialTheme.colorScheme.surfaceContainerHighest
    var urlEditText by remember { mutableStateOf<EditText?>(null) }

    LaunchedEffect(editingUrl) {
        if (editingUrl) {
            urlEditText?.post {
                urlEditText?.requestFocus()
                (urlEditText?.context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                    ?.showSoftInput(urlEditText, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    val barBackground = if (incognito) samurai.incognitoSurface else MaterialTheme.colorScheme.surface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(barBackground)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SamuraiNavButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            desc = "Indietro",
            tag = "nav_back_button",
            onClick = onBack,
        )
        if (!prefs.compactBar) {
            SamuraiNavButton(
                icon = Icons.AutoMirrored.Rounded.ArrowForward,
                desc = "Avanti",
                tag = "nav_forward_button",
                onClick = onForward,
            )
            SamuraiNavButton(
                icon = Icons.Rounded.Refresh,
                desc = "Ricarica",
                tag = "nav_reload_button",
                onClick = onReload,
            )
        }

        if (editingUrl) {
            AndroidView(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .testTag("omnibar_edit_input"),
                factory = { ctx ->
                    EditText(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                        setSingleLine(true)
                        setTextColor(pageInk.toArgb())
                        setHintTextColor(pageInk.copy(alpha = 0.55f).toArgb())
                        hint = "Cerca o inserisci un indirizzo"
                        textSize = 13.5f
                        setPadding(34, 22, 34, 22)
                        inputType = InputType.TYPE_CLASS_TEXT or
                            InputType.TYPE_TEXT_VARIATION_URI or
                            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                        imeOptions = EditorInfo.IME_ACTION_GO
                        background = GradientDrawable().apply {
                            cornerRadius = 28f
                            setColor(fieldFill.toArgb())
                            setStroke(2, samurai.goldAccent.copy(alpha = 0.6f).toArgb())
                        }
                        setText(urlText)
                        val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        setOnEditorActionListener { _, actionId, _ ->
                            if (actionId == EditorInfo.IME_ACTION_GO) {
                                val entered = text.toString()
                                onCommitUrl(entered)
                                imm?.hideSoftInputFromWindow(windowToken, 0)
                                onEditingChange(false)
                                true
                            } else {
                                false
                            }
                        }
                        setOnFocusChangeListener { _, hasFocus ->
                            if (hasFocus) selectAll() else onEditingChange(false)
                        }
                        urlEditText = this
                    }
                },
            )
        } else {
            val isSecure = urlText.startsWith("https://")
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(
                        width = 1.dp,
                        color = when {
                            telemetry.boostActive -> samurai.boostActive.copy(alpha = 0.65f)
                            incognito -> samurai.incognitoAccent.copy(alpha = 0.6f)
                            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        },
                        shape = RoundedCornerShape(14.dp),
                    )
                    .clickable { onEditingChange(true) }
                    .padding(horizontal = 10.dp, vertical = 10.dp)
                    .testTag("omnibar_display_pill"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = when {
                        incognito -> Icons.Rounded.VisibilityOff
                        prefs.adShieldEnabled -> Icons.Rounded.Shield
                        isSecure -> Icons.Rounded.Lock
                        else -> Icons.Rounded.Search
                    },
                    contentDescription = "Stato pagina",
                    tint = when {
                        incognito -> samurai.incognitoAccent
                        prefs.adShieldEnabled -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = urlText.ifEmpty { "Cerca o inserisci un indirizzo" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (urlText.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                )

                // Active QoL / Boost status micro-pills inside omnibar
                if (zenReaderActive) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.MenuBook,
                        contentDescription = "Lettura Zen attiva",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(14.dp),
                    )
                }
                if (prefs.desktopMode) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.DesktopWindows,
                        contentDescription = "Modalità Desktop attiva",
                        tint = samurai.goldAccent,
                        modifier = Modifier.size(14.dp),
                    )
                }
                if (telemetry.boostActive) {
                    Spacer(Modifier.width(5.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = samurai.boostActive.copy(alpha = 0.18f),
                        border = BorderStroke(0.75.dp, samurai.boostActive.copy(alpha = 0.7f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bolt,
                                contentDescription = "Gaming Boost",
                                tint = samurai.boostActive,
                                modifier = Modifier.size(11.dp),
                            )
                            Text(
                                text = "${telemetry.currentHz}Hz",
                                style = MaterialTheme.typography.labelSmall,
                                color = samurai.boostActive,
                            )
                        }
                    }
                }
            }
        }

        if (!prefs.compactBar) {
            SamuraiNavButton(
                icon = if (bookmarked) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                desc = "Preferito",
                tint = if (bookmarked) samurai.goldAccent else MaterialTheme.colorScheme.onSurface,
                tag = "bookmark_toggle_button",
                onClick = onToggleBookmark,
            )
            SamuraiNavButton(
                icon = if (showFind) Icons.Rounded.SearchOff else Icons.Rounded.Search,
                desc = "Trova nella pagina",
                tag = "find_in_page_button",
                onClick = onToggleFind,
            )
        }

        // Control Center (⋮) trigger with subtle Samurai gold accent
        Surface(
            onClick = onOpenControlCenter,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, samurai.goldAccent.copy(alpha = 0.45f)),
            modifier = Modifier
                .size(40.dp)
                .testTag("control_center_button"),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Centro Controllo KoKoro",
                    tint = samurai.goldAccent,
                    modifier = Modifier.size(21.dp),
                )
            }
        }
    }
}

@Composable
fun SamuraiFindBar(
    query: String,
    activeMatch: Int,
    totalMatches: Int,
    onQueryChange: (String) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val samurai = LocalSamuraiPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .testTag("find_bar_input"),
            singleLine = true,
            placeholder = { Text("Trova nella pagina", style = MaterialTheme.typography.bodyMedium) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                focusedIndicatorColor = samurai.goldAccent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            textStyle = LocalTextStyle.current.copy(fontSize = 13.5.sp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onNext() }),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (totalMatches > 0) "$activeMatch/$totalMatches" else "0/0",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SamuraiNavButton(Icons.Rounded.KeyboardArrowUp, "Precedente", tag = "find_prev_btn", onClick = onPrev)
        SamuraiNavButton(Icons.Rounded.KeyboardArrowDown, "Successivo", tag = "find_next_btn", onClick = onNext)
        SamuraiNavButton(Icons.Rounded.Close, "Chiudi ricerca", tag = "find_close_btn", onClick = onClose)
    }
}

/**
 * Real-time Gaming HUD pill overlay (zero mock numbers: live Hz, Choreographer FPS, ActivityManager RAM, Thermal).
 */
@Composable
fun LiveGamingHudPill(
    visible: Boolean,
    telemetry: HardwareTelemetry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val samurai = LocalSamuraiPalette.current
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
        modifier = modifier,
    ) {
        val thermalColor = when (telemetry.thermalLevel) {
            ThermalLevel.OPTIMAL -> samurai.thermalOk
            ThermalLevel.WARM -> samurai.thermalWarm
            ThermalLevel.HOT, ThermalLevel.CRITICAL -> samurai.thermalHot
        }
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            color = samurai.glassSurface,
            border = BorderStroke(1.dp, samurai.goldAccent.copy(alpha = 0.6f)),
            modifier = Modifier.testTag("live_gaming_hud_pill"),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = "HUD Gaming",
                    tint = samurai.boostActive,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "${telemetry.currentHz}Hz",
                    style = MaterialTheme.typography.labelMedium,
                    color = samurai.goldAccent,
                )
                Text(
                    text = "${telemetry.liveFps} FPS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${telemetry.availRamMb}MB",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(thermalColor),
                )
            }
        }
    }
}

@Composable
fun BlockedPopupBanner(
    notice: BlockedPopupNotice?,
    onAllow: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val samurai = LocalSamuraiPalette.current
    AnimatedVisibility(
        visible = notice != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        if (notice != null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = samurai.glassSurface,
                border = BorderStroke(1.dp, samurai.goldAccent.copy(alpha = 0.6f)),
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = "Popup bloccato",
                        tint = samurai.goldAccent,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Popup bloccato dallo Scudo KoKoro",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = notice.url.take(44),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(
                        onClick = { onAllow(notice.url) },
                        modifier = Modifier.testTag("allow_blocked_popup_button"),
                    ) {
                        Text("Consenti", color = samurai.goldAccent)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Chiudi", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SamuraiNavButton(
    icon: ImageVector,
    desc: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    tag: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .testTag(tag),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = desc,
            tint = tint,
            modifier = Modifier.size(21.dp),
        )
    }
}
