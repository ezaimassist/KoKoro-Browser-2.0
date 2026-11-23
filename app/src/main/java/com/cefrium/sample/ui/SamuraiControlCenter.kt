package com.cefrium.sample.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cefrium.sample.R
import com.cefrium.sample.data.BrowserPreferences
import com.cefrium.sample.engine.HardwareTelemetry
import com.cefrium.sample.engine.ThermalLevel
import com.cefrium.sample.theme.LocalSamuraiPalette
import com.cefrium.sample.theme.SamuraiCard
import com.cefrium.sample.theme.seigaihaBackdrop

/**
 * Full graphical Samurai Command Hub replacing the plain overflow menu.
 * Combines KoKoro branding, live Gaming Boost dashboard, 2x4 QoL addons bento grid,
 * inline page zoom stepper, and quick dock actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SamuraiControlCenterSheet(
    prefs: BrowserPreferences,
    telemetry: HardwareTelemetry,
    zenReaderActive: Boolean,
    blockedAdsCount: Int,
    blockedPopupsCount: Int,
    activeTabsCount: Int,
    onDismiss: () -> Unit,
    onToggleGamingBoost: () -> Unit,
    onSelectTargetHz: (Int) -> Unit,
    onToggleTouchBoost: () -> Unit,
    onToggleSustainedPerf: () -> Unit,
    onToggleGamingHud: () -> Unit,
    onTrimRamNow: () -> Unit,
    onToggleAdShield: () -> Unit,
    onToggleZenReader: () -> Unit,
    onToggleDesktopMode: () -> Unit,
    onToggleForceDark: () -> Unit,
    onTogglePopupBlocker: () -> Unit,
    onChangeZoom: (Int) -> Unit,
    onQuickClean: () -> Unit,
    onNewIncognitoTab: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
    onShare: () -> Unit,
    onExportPdf: () -> Unit,
    onToggleImmersive: () -> Unit,
    onToggleBarPosition: () -> Unit,
    onOpenAssistant: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val samurai = LocalSamuraiPalette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .seigaihaBackdrop(samurai.seigaihaStroke)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Samurai handle bar
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(46.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(samurai.katanaPrimary, samurai.goldAccent, samurai.katanaPrimary),
                        ),
                    ),
            )

            // 1. Branded KoKoro Hero Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .border(
                            width = 2.dp,
                            brush = Brush.sweepGradient(
                                listOf(samurai.goldAccent, samurai.katanaPrimary, samurai.goldAccent),
                            ),
                            shape = CircleShape,
                        )
                        .clickable { onOpenAssistant() }
                        .testTag("control_center_mascot_btn"),
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_kokoro_mascot),
                        contentDescription = "KoKoro Mascotte",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "KoKoro Browser",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "${prefs.theme.displayName} · $activeTabsCount schede attive",
                        style = MaterialTheme.typography.bodySmall,
                        color = samurai.goldAccent,
                    )
                }
                Surface(
                    onClick = onOpenAssistant,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, samurai.goldAccent.copy(alpha = 0.55f)),
                    modifier = Modifier.testTag("control_center_open_assistant"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "Assistente KoKoro",
                            tint = samurai.goldAccent,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "KoKoro",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Chiudi Centro Controllo")
                }
            }

            // 2. Real Gaming Boost Dashboard Card
            SamuraiCard(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                borderColor = if (prefs.gamingBoostEnabled) samurai.boostActive.copy(alpha = 0.85f)
                else samurai.glassBorder.copy(alpha = 0.4f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (prefs.gamingBoostEnabled) samurai.boostActive.copy(alpha = 0.22f)
                                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bolt,
                                contentDescription = "Gaming Boost",
                                tint = if (prefs.gamingBoostEnabled) samurai.boostActive else samurai.goldAccent,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SAMURAI GAMING BOOST",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = if (telemetry.boostActive) {
                                    val lockLabel = if (telemetry.surfaceLocked) "Superficie bloccata" else "Finestra bloccata"
                                    "$lockLabel · ${telemetry.currentHz} Hz reali"
                                } else {
                                    "Modalità bilanciata di sistema · ${telemetry.currentHz} Hz"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = prefs.gamingBoostEnabled,
                            onCheckedChange = { onToggleGamingBoost() },
                            modifier = Modifier.testTag("gaming_boost_master_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = samurai.boostActive,
                            ),
                        )
                    }

                    // Target Refresh Rate Selector Chips (Auto, 60, 90, 120 Hz)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val hzChoices = listOf(0 to "AUTO", 60 to "60 Hz", 90 to "90 Hz", 120 to "120 Hz")
                        hzChoices.forEach { (hzVal, label) ->
                            val selected = prefs.targetRefreshRate == hzVal
                            Surface(
                                onClick = { onSelectTargetHz(hzVal) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) samurai.goldAccent.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(
                                    width = if (selected) 1.25.dp else 1.dp,
                                    color = if (selected) samurai.goldAccent else MaterialTheme.colorScheme.outlineVariant,
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("hz_chip_$hzVal"),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) samurai.goldAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    // Sub-boost quick toggles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MiniTogglePill(
                            label = "Touch Boost",
                            active = prefs.touchBoostEnabled,
                            onClick = onToggleTouchBoost,
                            modifier = Modifier.weight(1f),
                        )
                        MiniTogglePill(
                            label = "Sustained",
                            active = prefs.sustainedPerformance,
                            onClick = onToggleSustainedPerf,
                            modifier = Modifier.weight(1f),
                        )
                        MiniTogglePill(
                            label = "HUD Live",
                            active = prefs.showGamingHudOverlay,
                            onClick = onToggleGamingHud,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))

                    // Real-time Hardware Telemetry Readout + RAM Trim button
                    val thermalColor = when (telemetry.thermalLevel) {
                        ThermalLevel.OPTIMAL -> samurai.thermalOk
                        ThermalLevel.WARM -> samurai.thermalWarm
                        ThermalLevel.HOT, ThermalLevel.CRITICAL -> samurai.thermalHot
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TelemetryStat("DISPLAY", "${telemetry.currentHz} Hz", samurai.goldAccent)
                        TelemetryStat("FPS LIVE", "${telemetry.liveFps}", MaterialTheme.colorScheme.primary)
                        TelemetryStat("RAM LIBERA", "${telemetry.availRamMb} MB", MaterialTheme.colorScheme.onSurface)
                        TelemetryStat("TERMICA", telemetry.thermalLevel.label, thermalColor)
                        Surface(
                            onClick = onTrimRamNow,
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            border = BorderStroke(1.dp, samurai.goldAccent.copy(alpha = 0.45f)),
                            modifier = Modifier.testTag("trim_ram_button"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Memory,
                                    contentDescription = "Libera RAM",
                                    tint = samurai.goldAccent,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "RAM",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = samurai.goldAccent,
                                )
                            }
                        }
                    }
                }
            }

            // 3. Quality-of-Life Addons Bento Grid (2 rows x 4 tiles)
            Text(
                text = "ADDON QUALITY OF LIFE",
                style = MaterialTheme.typography.labelMedium,
                color = samurai.goldAccent,
                modifier = Modifier.padding(horizontal = 2.dp),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QolBentoTile(
                        icon = Icons.Rounded.Shield,
                        title = "Scudo Ads",
                        badge = if (blockedAdsCount > 0) "$blockedAdsCount" else if (prefs.adShieldEnabled) "ON" else "OFF",
                        active = prefs.adShieldEnabled,
                        tag = "qol_ad_shield",
                        onClick = onToggleAdShield,
                        modifier = Modifier.weight(1f),
                    )
                    QolBentoTile(
                        icon = Icons.Rounded.MenuBook,
                        title = "Lettura Zen",
                        badge = if (zenReaderActive) "ATTIVA" else "OFF",
                        active = zenReaderActive,
                        tag = "qol_zen_reader",
                        onClick = onToggleZenReader,
                        modifier = Modifier.weight(1f),
                    )
                    QolBentoTile(
                        icon = Icons.Rounded.DesktopWindows,
                        title = "Desktop",
                        badge = if (prefs.desktopMode) "PC" else "MOB",
                        active = prefs.desktopMode,
                        tag = "qol_desktop_mode",
                        onClick = onToggleDesktopMode,
                        modifier = Modifier.weight(1f),
                    )
                    QolBentoTile(
                        icon = Icons.Rounded.DarkMode,
                        title = "Dark Web",
                        badge = if (prefs.forceDarkWeb) "ON" else "OFF",
                        active = prefs.forceDarkWeb,
                        tag = "qol_dark_web",
                        onClick = onToggleForceDark,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QolBentoTile(
                        icon = Icons.Rounded.Block,
                        title = "Anti-Popup",
                        badge = if (blockedPopupsCount > 0) "$blockedPopupsCount" else if (prefs.popupBlockerEnabled) "ON" else "OFF",
                        active = prefs.popupBlockerEnabled,
                        tag = "qol_popup_blocker",
                        onClick = onTogglePopupBlocker,
                        modifier = Modifier.weight(1f),
                    )
                    QolBentoTile(
                        icon = Icons.Rounded.CleaningServices,
                        title = "Pulizia",
                        badge = "RAPIDA",
                        active = false,
                        tag = "qol_quick_clean",
                        onClick = onQuickClean,
                        modifier = Modifier.weight(1f),
                    )
                    QolBentoTile(
                        icon = Icons.Rounded.VisibilityOff,
                        title = "Privata",
                        badge = "NUOVA",
                        active = false,
                        tag = "qol_incognito",
                        onClick = onNewIncognitoTab,
                        modifier = Modifier.weight(1f),
                    )
                    QolBentoTile(
                        icon = Icons.Rounded.Fullscreen,
                        title = "Immersivo",
                        badge = "FULL",
                        active = false,
                        tag = "qol_immersive",
                        onClick = onToggleImmersive,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Inline Page Zoom Stepper + Bar Position Control
            SamuraiCard(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = "Zoom pagina",
                            tint = samurai.goldAccent,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Zoom Pagina",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onChangeZoom(prefs.pageZoomPercent - 10) },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Rounded.Remove, contentDescription = "Riduci zoom", modifier = Modifier.size(18.dp))
                        }
                        Text(
                            text = "${prefs.pageZoomPercent}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = samurai.goldAccent,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        )
                        IconButton(
                            onClick = { onChangeZoom(prefs.pageZoomPercent + 10) },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Aumenta zoom", modifier = Modifier.size(18.dp))
                        }
                    }
                    Surface(
                        onClick = onToggleBarPosition,
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SwapVert,
                                contentDescription = "Posizione barra",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (prefs.bottomBar) "Barra Giù" else "Barra Su",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }

            // 4. Utility & Navigation Dock
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DockActionChip(Icons.Rounded.Bookmarks, "Preferiti", "dock_bookmarks", onOpenBookmarks, Modifier.weight(1f))
                DockActionChip(Icons.Rounded.History, "Cronologia", "dock_history", onOpenHistory, Modifier.weight(1f))
                DockActionChip(Icons.Rounded.Download, "Download", "dock_downloads", onOpenDownloads, Modifier.weight(1f))
                DockActionChip(Icons.Rounded.Share, "Condividi", "dock_share", onShare, Modifier.weight(1f))
                DockActionChip(Icons.Rounded.PictureAsPdf, "PDF", "dock_pdf", onExportPdf, Modifier.weight(1f))
            }

            // 5. Full Custom Settings Hub CTA
            Surface(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, samurai.goldAccent.copy(alpha = 0.65f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_full_settings_button"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "Impostazioni Avanzate",
                        tint = samurai.goldAccent,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Centro Impostazioni Avanzate",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = "Temi Samurai, Gaming Boost, User-Agent, Scudo & Diagnostica",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                        )
                    }
                    Text(
                        text = "APRI →",
                        style = MaterialTheme.typography.labelMedium,
                        color = samurai.goldAccent,
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniTogglePill(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val samurai = LocalSamuraiPalette.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            width = 1.dp,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier.height(34.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) samurai.goldAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TelemetryStat(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor,
        )
    }
}

@Composable
private fun QolBentoTile(
    icon: ImageVector,
    title: String,
    badge: String,
    active: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val samurai = LocalSamuraiPalette.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
        else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            width = if (active) 1.25.dp else 1.dp,
            color = if (active) samurai.goldAccent.copy(alpha = 0.8f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
        modifier = modifier
            .height(86.dp)
            .testTag(tag),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (active) samurai.goldAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) samurai.goldAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DockActionChip(
    icon: ImageVector,
    label: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .height(60.dp)
            .testTag(tag),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
