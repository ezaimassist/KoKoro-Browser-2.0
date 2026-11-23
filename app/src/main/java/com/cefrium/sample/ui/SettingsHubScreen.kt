package com.cefrium.sample.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cefrium.Cefrium
import com.cefrium.sample.BuildConfig
import com.cefrium.sample.data.BrowserPreferences
import com.cefrium.sample.data.SearchEngine
import com.cefrium.sample.data.UserAgentPreset
import com.cefrium.sample.engine.HardwareTelemetry
import com.cefrium.sample.theme.LocalSamuraiPalette
import com.cefrium.sample.theme.SamuraiCard
import com.cefrium.sample.theme.SamuraiThemeId
import com.cefrium.sample.theme.seigaihaBackdrop
import kotlin.math.roundToInt

private enum class SettingsTab(val label: String, val icon: ImageVector) {
    AESTHETICS("Estetica", Icons.Rounded.Palette),
    GAMING("Gaming", Icons.Rounded.Bolt),
    PRIVACY_QOL("Privacy & QoL", Icons.Rounded.Shield),
    ENGINE("Motore", Icons.Rounded.Memory),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsHubScreen(
    prefs: BrowserPreferences,
    telemetry: HardwareTelemetry,
    currentHost: String,
    activeTabsCount: Int,
    onBack: () -> Unit,
    onUpdatePrefs: ((BrowserPreferences) -> BrowserPreferences) -> Unit,
    onToggleWhitelistHost: (String) -> Unit,
    onClearCache: () -> Unit,
    onClearHistory: () -> Unit,
    onTrimBackgroundTabs: () -> Unit,
    onReloadActiveTab: () -> Unit,
) {
    BackHandler { onBack() }

    val samurai = LocalSamuraiPalette.current
    var selectedTab by remember { mutableStateOf(SettingsTab.AESTHETICS) }
    var homepageDraft by remember(prefs.homepage) { mutableStateOf(prefs.homepage) }
    var customUaDraft by remember(prefs.customUserAgent) { mutableStateOf(prefs.customUserAgent) }
    var whitelistDraft by remember { mutableStateOf("") }
    var zoomDraft by remember(prefs.pageZoomPercent) { mutableIntStateOf(prefs.pageZoomPercent) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .seigaihaBackdrop(samurai.seigaihaStroke)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("settings_back_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Chiudi Impostazioni",
                        tint = samurai.goldAccent,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Impostazioni KoKoro",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "Personalizzazione Samurai · Gaming Boost · Privacy",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Category switcher bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SettingsTab.entries.forEach { tab ->
                    val active = tab == selectedTab
                    Surface(
                        onClick = { selectedTab = tab },
                        shape = RoundedCornerShape(12.dp),
                        color = if (active) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(
                            width = if (active) 1.25.dp else 1.dp,
                            color = if (active) samurai.goldAccent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_tab_${tab.name.lowercase()}"),
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = if (active) samurai.goldAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            )

            // Tab content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (selectedTab) {
                    SettingsTab.AESTHETICS -> {
                        SectionTitle("TEMI SAMURAI INTERCAMBIABILI")
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SamuraiThemeId.entries.forEach { theme ->
                                ThemePreviewCard(
                                    theme = theme,
                                    selected = prefs.theme == theme,
                                    onSelect = { onUpdatePrefs { it.copy(theme = theme) } },
                                )
                            }
                        }

                        SectionTitle("INTERFACCIA & ANIMAZIONI")
                        SamuraiCard {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SettingSwitchRow(
                                    title = "Barra di navigazione in basso",
                                    subtitle = "Ergonomia a una mano per dispositivi moderni",
                                    checked = prefs.bottomBar,
                                    onCheckedChange = { v -> onUpdatePrefs { it.copy(bottomBar = v) } },
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                SettingSwitchRow(
                                    title = "Barra compatta",
                                    subtitle = "Massimizza la superficie visibile di gioco e lettura",
                                    checked = prefs.compactBar,
                                    onCheckedChange = { v -> onUpdatePrefs { it.copy(compactBar = v) } },
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                SettingSwitchRow(
                                    title = "Animazioni Samurai fluide",
                                    subtitle = "Scia katana, aura animata KoKoro e transizioni spring",
                                    checked = prefs.samuraiAnimations,
                                    onCheckedChange = { v -> onUpdatePrefs { it.copy(samuraiAnimations = v) } },
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                SettingSwitchRow(
                                    title = "Emblema flottante KoKoro",
                                    subtitle = "Mostra la mascotte animata trascinabile sul bordo",
                                    checked = prefs.showFloatingKoKoro,
                                    onCheckedChange = { v -> onUpdatePrefs { it.copy(showFloatingKoKoro = v) } },
                                )
                            }
                        }
                    }

                    SettingsTab.GAMING -> {
                        SectionTitle("PROFILO SAMURAI GAMING BOOST")
                        SamuraiCard {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SettingSwitchRow(
                                    title = "Attiva Gaming Boost",
                                    subtitle = "Blocca refresh rate hardware, priorità thread e superficie",
                                    checked = prefs.gamingBoostEnabled,
                                    onCheckedChange = { v -> onUpdatePrefs { it.copy(gamingBoostEnabled = v) } },
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                Text(
                                    text = "Frequenza target display (supportate: ${telemetry.supportedHzOptions.joinToString(", ") { "${it}Hz" }})",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    listOf(0 to "Auto Max", 60 to "60 Hz", 90 to "90 Hz", 120 to "120 Hz").forEach { (hz, label) ->
                                        val selected = prefs.targetRefreshRate == hz
                                        Surface(
                                            onClick = { onUpdatePrefs { it.copy(targetRefreshRate = hz) } },
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (selected) samurai.boostActive.copy(alpha = 0.22f)
                                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                                            border = BorderStroke(
                                                1.dp,
                                                if (selected) samurai.goldAccent else MaterialTheme.colorScheme.outlineVariant,
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(38.dp),
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (selected) samurai.goldAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                SettingSwitchRow(
                                    title = "Touch Boost (Eventi Passivi)",
                                    subtitle = "Ottimizza touchmove/pointermove per ridurre la latenza in WebGL",
                                    checked = prefs.touchBoostEnabled,
                                    onCheckedChange = { v -> onUpdatePrefs { it.copy(touchBoostEnabled = v) } },
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                SettingSwitchRow(
                                    title = "Sustained Performance Mode",
                                    subtitle = "Richiede al sistema clock stabili per sessioni prolungate",
                                    checked = prefs.sustainedPerformance,
                                    onCheckedChange = { v -> onUpdatePrefs { it.copy(sustainedPerformance = v) } },
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                SettingSwitchRow(
                                    title = "Mantieni schermo attivo in Boost",
                                    subtitle = "Impedisce lo spegnimento automatico del display durante il gioco",
                                    checked = prefs.keepScreenOnInBoost,
                                    onCheckedChange = { v -> onUpdatePrefs { it.copy(keepScreenOnInBoost = v) } },
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                SettingSwitchRow(
                                    title = "Auto-Trim RAM all'attivazione",
                                    subtitle = "Sospende le schede in background per dedicare memoria al gioco",
                                    checked = prefs.autoTrimRamOnBoost,
                                    onCheckedChange = { v -> onUpdatePrefs { it.copy(autoTrimRamOnBoost = v) } },
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                SettingSwitchRow(
                                    title = "HUD Gaming live in sovrimpressione",
                                    subtitle = "Pillola compatta con Hz reali, FPS Choreographer, RAM e termica",
                                    checked = prefs.showGamingHudOverlay,
                                    onCheckedChange = { v -> onUpdatePrefs { it.copy(showGamingHudOverlay = v) } },
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                SettingSwitchRow(
                                    title = "Auto-Boost su domini di gioco",
                                    subtitle = "Attiva automaticamente il boost su /play e portali WebGL supportati",
                                    checked = prefs.autoBoostGameDomains,
                                    onCheckedChange = { v -> onUpdatePrefs { it.copy(autoBoostGameDomains = v) } },
                                )
                            }
                        }
                    }

                    SettingsTab.PRIVACY_QOL -> {
                        SectionTitle("SCUDO ANNUNCI & POPUP")
                        SamuraiCard {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SettingSwitchRow(
                                    title = "Scudo Annunci Avanzato",
                                    subtitle = "EasyList + regole YouTube integrate nel motore Cefrium",
                                    checked = prefs.adShieldEnabled,
                                    onCheckedChange = { v -> onUpdatePrefs { it.copy(adShieldEnabled = v) } },
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                SettingSwitchRow(
                                    title = "Blocco Popup Automatico",
                                    subtitle = "Intercetta window.open indesiderati con opzione Consenti",
                                    checked = prefs.popupBlockerEnabled,
                                    onCheckedChange = { v -> onUpdatePrefs { it.copy(popupBlockerEnabled = v) } },
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                Text("Whitelist domini (eccezioni Scudo)", style = MaterialTheme.typography.titleSmall)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = whitelistDraft,
                                        onValueChange = { whitelistDraft = it },
                                        placeholder = { Text(currentHost.ifBlank { "esempio.it" }) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Button(
                                        onClick = {
                                            val target = whitelistDraft.trim().ifBlank { currentHost }
                                            if (target.isNotBlank()) {
                                                onToggleWhitelistHost(target)
                                                whitelistDraft = ""
                                            }
                                        },
                                    ) {
                                        Icon(Icons.Rounded.Add, contentDescription = "Aggiungi dominio", modifier = Modifier.size(18.dp))
                                    }
                                }
                                if (prefs.adShieldWhitelist.isNotEmpty()) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        prefs.adShieldWhitelist.sorted().forEach { host ->
                                            Surface(
                                                onClick = { onToggleWhitelistHost(host) },
                                                shape = RoundedCornerShape(10.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Text(host, style = MaterialTheme.typography.labelSmall)
                                                    Spacer(Modifier.width(4.dp))
                                                    Icon(Icons.Rounded.Close, contentDescription = "Rimuovi", modifier = Modifier.size(13.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        SectionTitle("LETTURA, ZOOM & USER-AGENT")
                        SamuraiCard {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SettingSwitchRow(
                                    title = "Forza Tema Scuro Pagine Web",
                                    subtitle = "Dark mode nativo Chromium + fallback intelligente",
                                    checked = prefs.forceDarkWeb,
                                    onCheckedChange = { v -> onUpdatePrefs { it.copy(forceDarkWeb = v) } },
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                SettingSwitchRow(
                                    title = "Modalità Desktop Predefinita",
                                    subtitle = "Richiede la versione PC dei siti web",
                                    checked = prefs.desktopMode,
                                    onCheckedChange = { v ->
                                        onUpdatePrefs { it.copy(desktopMode = v) }
                                        onReloadActiveTab()
                                    },
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("Zoom Pagina Predefinito", style = MaterialTheme.typography.titleSmall)
                                    Text("$zoomDraft%", style = MaterialTheme.typography.labelLarge, color = samurai.goldAccent)
                                }
                                Slider(
                                    value = zoomDraft.toFloat(),
                                    onValueChange = { zoomDraft = ((it / 5f).roundToInt() * 5).coerceIn(75, 150) },
                                    onValueChangeFinished = { onUpdatePrefs { it.copy(pageZoomPercent = zoomDraft) } },
                                    valueRange = 75f..150f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = samurai.goldAccent,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                    ),
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                Text("Profilo User-Agent", style = MaterialTheme.typography.titleSmall)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    UserAgentPreset.entries.forEach { preset ->
                                        val selected = prefs.userAgentPreset == preset
                                        Surface(
                                            onClick = {
                                                onUpdatePrefs { it.copy(userAgentPreset = preset) }
                                                onReloadActiveTab()
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                                            border = BorderStroke(
                                                1.dp,
                                                if (selected) samurai.goldAccent else MaterialTheme.colorScheme.outlineVariant,
                                            ),
                                        ) {
                                            Text(
                                                text = preset.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                            )
                                        }
                                    }
                                }
                                if (prefs.userAgentPreset == UserAgentPreset.CUSTOM) {
                                    OutlinedTextField(
                                        value = customUaDraft,
                                        onValueChange = { customUaDraft = it },
                                        label = { Text("Stringa User-Agent personalizzata") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Button(
                                        onClick = {
                                            onUpdatePrefs { it.copy(customUserAgent = customUaDraft) }
                                            onReloadActiveTab()
                                        },
                                        modifier = Modifier.align(Alignment.End),
                                    ) {
                                        Text("Applica User-Agent")
                                    }
                                }
                            }
                        }

                        SectionTitle("RICERCA, HOMEPAGE & PULIZIA")
                        SamuraiCard {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Motore di Ricerca", style = MaterialTheme.typography.titleSmall)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    SearchEngine.entries.forEach { engine ->
                                        val selected = prefs.searchEngine == engine
                                        Surface(
                                            onClick = { onUpdatePrefs { it.copy(searchEngine = engine) } },
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                                            border = BorderStroke(
                                                1.dp,
                                                if (selected) samurai.goldAccent else MaterialTheme.colorScheme.outlineVariant,
                                            ),
                                        ) {
                                            Text(
                                                text = engine.displayName,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                            )
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value = homepageDraft,
                                    onValueChange = { homepageDraft = it },
                                    label = { Text("Homepage predefinita") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Button(
                                    onClick = { onUpdatePrefs { it.copy(homepage = homepageDraft) } },
                                    modifier = Modifier.align(Alignment.End),
                                ) {
                                    Text("Salva Homepage")
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                SettingSwitchRow(
                                    title = "Chiedi dove salvare ogni download",
                                    subtitle = "Usa il selettore documenti Android (SAF) per ogni file",
                                    checked = prefs.askWhereToSave,
                                    onCheckedChange = { v -> onUpdatePrefs { it.copy(askWhereToSave = v) } },
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = onClearCache,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(Icons.Rounded.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text("Cache", style = MaterialTheme.typography.labelMedium)
                                    }
                                    OutlinedButton(
                                        onClick = onClearHistory,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text("Cronologia", style = MaterialTheme.typography.labelMedium)
                                    }
                                    Button(
                                        onClick = onTrimBackgroundTabs,
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(Icons.Rounded.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text("RAM", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }

                    SettingsTab.ENGINE -> {
                        val chromiumVersion = remember {
                            runCatching { Cefrium.getChromiumVersion() }.getOrDefault("Cefrium SDK")
                        }
                        SectionTitle("DIAGNOSTICA HARDWARE & MOTORE LIVE")
                        SamuraiCard {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                DiagnosticRow("Browser", "KoKoro Browser v${BuildConfig.VERSION_NAME}")
                                DiagnosticRow("Motore Web", "Cefrium · Chromium $chromiumVersion")
                                DiagnosticRow("Android SDK", "API ${android.os.Build.VERSION.SDK_INT} (minSdk 31 · Android 12+)")
                                DiagnosticRow("Display Attivo", "${telemetry.currentHz} Hz (${telemetry.liveFps} FPS reali)")
                                DiagnosticRow("Modalità Pannello", telemetry.supportedHzOptions.joinToString(" · ") { "${it} Hz" })
                                DiagnosticRow(
                                    "Cronologia Hz",
                                    if (telemetry.recentHzHistory.isEmpty()) "${telemetry.currentHz} Hz"
                                    else telemetry.recentHzHistory.joinToString(" → ") { "$it" },
                                )
                                DiagnosticRow("Memoria RAM", "${telemetry.availRamMb} MB liberi su ${telemetry.totalRamMb} MB")
                                DiagnosticRow("Stato Termico", telemetry.thermalLevel.label)
                                DiagnosticRow("Schede Aperte", "$activeTabsCount")
                                DiagnosticRow("Package ID", BuildConfig.APPLICATION_ID)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = LocalSamuraiPalette.current.goldAccent,
        modifier = Modifier.padding(horizontal = 2.dp),
    )
}

@Composable
private fun ThemePreviewCard(
    theme: SamuraiThemeId,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val samurai = LocalSamuraiPalette.current
    val (bg, primary, accent) = when (theme) {
        SamuraiThemeId.MURASAKI_SHOGUN -> Triple(Color(0xFF0B0914), Color(0xFFA855F7), Color(0xFFF5C542))
        SamuraiThemeId.KUROGANE_BLADE -> Triple(Color(0xFF090A0D), Color(0xFFE63946), Color(0xFFD4AF37))
        SamuraiThemeId.WASHI_IVORY -> Triple(Color(0xFFF7F4EE), Color(0xFF5B21B6), Color(0xFFC28A17))
    }

    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            width = if (selected) 1.75.dp else 1.dp,
            color = if (selected) samurai.goldAccent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("theme_card_${theme.id}"),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(bg, primary.copy(alpha = 0.7f))))
                    .border(1.5.dp, accent, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Selezionato",
                        tint = accent,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = theme.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = theme.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
