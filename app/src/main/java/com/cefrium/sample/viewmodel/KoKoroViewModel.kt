package com.cefrium.sample.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cefrium.sample.data.BookmarkItem
import com.cefrium.sample.data.BrowserPreferences
import com.cefrium.sample.data.HistoryItem
import com.cefrium.sample.data.KoKoroRepository
import com.cefrium.sample.data.SearchEngine
import com.cefrium.sample.data.SessionSnapshot
import com.cefrium.sample.data.UserAgentPreset
import com.cefrium.sample.engine.HardwareTelemetry
import com.cefrium.sample.engine.SystemHealthSnapshot
import com.cefrium.sample.theme.SamuraiThemeId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ActiveOverlay {
    NONE,
    CONTROL_CENTER,
    SETTINGS,
    ASSISTANT,
    TABS_GRID,
    BOOKMARKS,
    HISTORY,
    DOWNLOADS,
}

data class BlockedPopupNotice(
    val url: String,
    val timestamp: Long = System.currentTimeMillis(),
)

data class AssistantEntry(
    val id: Long = System.nanoTime(),
    val fromUser: Boolean,
    val text: String,
)

sealed interface AssistantAction {
    data class Navigate(val urlOrQuery: String) : AssistantAction
    data class OpenNewTab(val incognito: Boolean, val url: String? = null) : AssistantAction
    data object ToggleZenReader : AssistantAction
    data object TrimMemoryAndCache : AssistantAction
    data object OpenFindInPage : AssistantAction
    data object ReloadActiveTab : AssistantAction
    data class OpenOverlay(val overlay: ActiveOverlay) : AssistantAction
}

class KoKoroViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = KoKoroRepository(application)

    val preferences: StateFlow<BrowserPreferences> = repository.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = BrowserPreferences(),
    )

    val bookmarks: StateFlow<List<BookmarkItem>> = repository.bookmarksFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = emptyList(),
    )

    val history: StateFlow<List<HistoryItem>> = repository.historyFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = emptyList(),
    )

    private val _telemetry = MutableStateFlow(HardwareTelemetry())
    val telemetry: StateFlow<HardwareTelemetry> = _telemetry.asStateFlow()

    private val _activeOverlay = MutableStateFlow(ActiveOverlay.NONE)
    val activeOverlay: StateFlow<ActiveOverlay> = _activeOverlay.asStateFlow()

    private val _blockedPopupsCount = MutableStateFlow(0)
    val blockedPopupsCount: StateFlow<Int> = _blockedPopupsCount.asStateFlow()

    private val _blockedAdsCount = MutableStateFlow(0)
    val blockedAdsCount: StateFlow<Int> = _blockedAdsCount.asStateFlow()

    private val _lastBlockedPopup = MutableStateFlow<BlockedPopupNotice?>(null)
    val lastBlockedPopup: StateFlow<BlockedPopupNotice?> = _lastBlockedPopup.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _assistantLog = MutableStateFlow<List<AssistantEntry>>(emptyList())
    val assistantLog: StateFlow<List<AssistantEntry>> = _assistantLog.asStateFlow()

    suspend fun loadInitialPreferences(): BrowserPreferences = repository.loadInitialPreferences()

    suspend fun loadSession(): SessionSnapshot = repository.loadSession()

    fun saveSession(snapshot: SessionSnapshot) {
        viewModelScope.launch { repository.saveSession(snapshot) }
    }

    fun clearSavedSession() {
        viewModelScope.launch { repository.clearSession() }
    }

    fun openOverlay(overlay: ActiveOverlay) {
        _activeOverlay.value = overlay
    }

    fun closeOverlay() {
        _activeOverlay.value = ActiveOverlay.NONE
    }

    fun showStatus(message: String) {
        _statusBanner.value = message
        viewModelScope.launch {
            delay(2_600L)
            if (_statusBanner.value == message) {
                _statusBanner.value = null
            }
        }
    }

    fun updatePreferences(transform: (BrowserPreferences) -> BrowserPreferences) {
        viewModelScope.launch { repository.updatePreferences(transform) }
    }

    fun setTheme(theme: SamuraiThemeId) = updatePreferences { it.copy(theme = theme) }

    fun toggleBottomBar() = updatePreferences { it.copy(bottomBar = !it.bottomBar) }

    fun toggleCompactBar() = updatePreferences { it.copy(compactBar = !it.compactBar) }

    fun toggleGamingBoost() = updatePreferences { it.copy(gamingBoostEnabled = !it.gamingBoostEnabled) }

    fun setTargetRefreshRate(hz: Int) = updatePreferences { it.copy(targetRefreshRate = hz) }

    fun toggleAdShield() = updatePreferences { it.copy(adShieldEnabled = !it.adShieldEnabled) }

    fun toggleWhitelistDomain(host: String) {
        val normalized = host.lowercase().trim()
        if (normalized.isBlank()) return
        updatePreferences { prefs ->
            val next = prefs.adShieldWhitelist.toMutableSet()
            if (!next.add(normalized)) next.remove(normalized)
            prefs.copy(adShieldWhitelist = next)
        }
    }

    fun togglePopupBlocker() = updatePreferences { it.copy(popupBlockerEnabled = !it.popupBlockerEnabled) }

    fun toggleForceDarkWeb() = updatePreferences { it.copy(forceDarkWeb = !it.forceDarkWeb) }

    fun toggleDesktopMode() = updatePreferences { it.copy(desktopMode = !it.desktopMode) }

    fun setPageZoom(percent: Int) = updatePreferences {
        it.copy(pageZoomPercent = percent.coerceIn(75, 150))
    }

    fun setUserAgentPreset(preset: UserAgentPreset) = updatePreferences {
        it.copy(userAgentPreset = preset)
    }

    fun setCustomUserAgent(ua: String) = updatePreferences {
        it.copy(customUserAgent = ua.trim())
    }

    fun setSearchEngine(engine: SearchEngine) = updatePreferences {
        it.copy(searchEngine = engine)
    }

    fun setHomepage(url: String) = updatePreferences {
        it.copy(homepage = url.trim().ifBlank { "https://duckduckgo.com/" })
    }

    fun toggleBookmark(url: String, title: String) {
        viewModelScope.launch { repository.toggleBookmark(url, title) }
    }

    fun removeBookmark(url: String) {
        viewModelScope.launch { repository.removeBookmark(url) }
    }

    fun recordHistory(url: String, title: String) {
        viewModelScope.launch { repository.addHistory(url, title) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            showStatus("Cronologia cancellata")
        }
    }

    fun recordPopupBlocked(url: String) {
        _blockedPopupsCount.update { it + 1 }
        _lastBlockedPopup.value = BlockedPopupNotice(url = url)
    }

    fun dismissPopupNotice() {
        _lastBlockedPopup.value = null
    }

    fun incrementBlockedAds() {
        _blockedAdsCount.update { it + 1 }
    }

    fun updateSupportedModes(modes: List<Int>) {
        _telemetry.update { it.copy(supportedHzOptions = modes) }
    }

    fun updateDisplayRate(hz: Int) {
        _telemetry.update { cur ->
            val history = if (cur.recentHzHistory.lastOrNull() != hz) {
                (cur.recentHzHistory + hz).takeLast(6)
            } else {
                cur.recentHzHistory
            }
            cur.copy(currentHz = hz, recentHzHistory = history)
        }
    }

    fun updateLiveFps(fps: Int) {
        _telemetry.update { it.copy(liveFps = fps) }
    }

    fun updateSystemHealth(snapshot: SystemHealthSnapshot) {
        _telemetry.update {
            it.copy(
                usedRamMb = snapshot.usedRamMb,
                totalRamMb = snapshot.totalRamMb,
                availRamMb = snapshot.availRamMb,
                lowMemory = snapshot.lowMemory,
                thermalLevel = snapshot.thermalLevel,
            )
        }
    }

    fun updateBoostTelemetry(active: Boolean, appliedHz: Int, surfaceLocked: Boolean) {
        _telemetry.update {
            it.copy(
                boostActive = active,
                activeBoostHz = appliedHz,
                surfaceLocked = surfaceLocked,
            )
        }
    }

    fun resolveNavigationUrl(rawInput: String): String {
        val trimmed = rawInput.trim()
        val prefs = preferences.value
        return when {
            trimmed.isEmpty() -> prefs.homepage
            trimmed.contains("://") -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> prefs.searchEngine.buildUrl(trimmed)
        }
    }

    fun hostOf(url: String?): String =
        runCatching { Uri.parse(url.orEmpty()).host.orEmpty() }.getOrDefault("")

    /**
     * Ultra-fast local deterministic command interpreter for the animated KoKoro assistant.
     * Executes real browser/hardware commands with zero latency and zero mock responses.
     */
    fun handleAssistantCommand(
        rawCommand: String,
        activeTabCount: Int,
        onAction: (AssistantAction) -> Unit,
    ) {
        val input = rawCommand.trim()
        if (input.isEmpty()) return
        val lower = input.lowercase()
        val t = telemetry.value
        val p = preferences.value

        appendAssistant(fromUser = true, text = input)

        val reply: String = when {
            lower.contains("120") && (lower.contains("boost") || lower.contains("hz") || lower.contains("gaming")) -> {
                updatePreferences { it.copy(gamingBoostEnabled = true, targetRefreshRate = 120) }
                "Gaming Boost armato a 120 Hz: superficie bloccata, prestazioni sostenute e touch passivo attivi."
            }
            lower.contains("90") && (lower.contains("boost") || lower.contains("hz") || lower.contains("gaming")) -> {
                updatePreferences { it.copy(gamingBoostEnabled = true, targetRefreshRate = 90) }
                "Gaming Boost impostato a 90 Hz per bilanciare fluidità e termica."
            }
            lower.contains("60") && (lower.contains("boost") || lower.contains("hz")) -> {
                updatePreferences { it.copy(gamingBoostEnabled = true, targetRefreshRate = 60) }
                "Frequenza bloccata a 60 Hz."
            }
            lower.contains("disattiva boost") || lower.contains("stop boost") || lower.contains("boost off") -> {
                updatePreferences { it.copy(gamingBoostEnabled = false) }
                "Gaming Boost disattivato. Il display torna alla gestione di sistema."
            }
            lower.contains("boost") || lower.contains("gaming") -> {
                val next = !p.gamingBoostEnabled
                updatePreferences { it.copy(gamingBoostEnabled = next) }
                if (next) {
                    "Gaming Boost attivo (${if (p.targetRefreshRate == 0) "Auto Max" else "${p.targetRefreshRate} Hz"}). HUD pronto."
                } else {
                    "Gaming Boost messo a riposo."
                }
            }
            lower.contains("hud") -> {
                val next = !p.showGamingHudOverlay
                updatePreferences { it.copy(showGamingHudOverlay = next) }
                if (next) "HUD Gaming live visibile in sovrimpressione (${t.currentHz} Hz · ${t.liveFps} FPS)."
                else "HUD Gaming live nascosto."
            }
            lower.contains("zen") || lower.contains("lettura") || lower.contains("reader") -> {
                onAction(AssistantAction.ToggleZenReader)
                "Modalità Lettura Zen commutata sulla scheda attiva."
            }
            lower.contains("desktop") -> {
                val next = !p.desktopMode
                updatePreferences { it.copy(desktopMode = next) }
                onAction(AssistantAction.ReloadActiveTab)
                if (next) "Modalità Desktop attivata e pagina ricaricata."
                else "Modalità Mobile ripristinata e pagina ricaricata."
            }
            lower.contains("scuro") || lower.contains("dark") -> {
                val next = !p.forceDarkWeb
                updatePreferences { it.copy(forceDarkWeb = next) }
                if (next) "Forza Tema Scuro Web attivo."
                else "Forza Tema Scuro Web disattivato."
            }
            lower.contains("kurogane") || lower.contains("rosso") || lower.contains("cremisi") -> {
                setTheme(SamuraiThemeId.KUROGANE_BLADE)
                "Tema Kurogane Blade forgiato: ossidiana, cremisi e oro."
            }
            lower.contains("murasaki") || lower.contains("viola") || lower.contains("shogun") -> {
                setTheme(SamuraiThemeId.MURASAKI_SHOGUN)
                "Tema Murasaki Shogun attivo: lacca notte, ametista e oro."
            }
            lower.contains("washi") || lower.contains("chiaro") || lower.contains("light") -> {
                setTheme(SamuraiThemeId.WASHI_IVORY)
                "Tema Washi Ivory diurno attivato."
            }
            lower.contains("pulisci") || lower.contains("ram") || lower.contains("cache") || lower.contains("clean") -> {
                onAction(AssistantAction.TrimMemoryAndCache)
                "Cache svuotata e schede in background rilasciate. RAM libera: ${t.availRamMb} MB."
            }
            lower.contains("privata") || lower.contains("incognito") -> {
                onAction(AssistantAction.OpenNewTab(incognito = true))
                "Nuova scheda privata aperta. Nessuna traccia salvata in cronologia."
            }
            lower.contains("nuova scheda") || lower.contains("new tab") -> {
                onAction(AssistantAction.OpenNewTab(incognito = false))
                "Nuova scheda aperta."
            }
            lower.contains("preferiti") || lower.contains("bookmark") -> {
                onAction(AssistantAction.OpenOverlay(ActiveOverlay.BOOKMARKS))
                "Apro i tuoi Preferiti (${bookmarks.value.size} salvati)."
            }
            lower.contains("cronologia") || lower.contains("history") -> {
                onAction(AssistantAction.OpenOverlay(ActiveOverlay.HISTORY))
                "Apro la Cronologia (${history.value.size} voci recenti)."
            }
            lower.contains("download") -> {
                onAction(AssistantAction.OpenOverlay(ActiveOverlay.DOWNLOADS))
                "Apro il Download Manager."
            }
            lower.contains("impostazioni") || lower.contains("settings") -> {
                onAction(AssistantAction.OpenOverlay(ActiveOverlay.SETTINGS))
                "Apro il Centro Impostazioni KoKoro."
            }
            lower.contains("trova") || lower.contains("find") -> {
                onAction(AssistantAction.OpenFindInPage)
                "Barra di ricerca nella pagina attivata."
            }
            lower.contains("stato") || lower.contains("diagnostica") || lower.contains("fps") -> {
                "Stato reale: ${t.currentHz} Hz · ${t.liveFps} FPS · RAM libera ${t.availRamMb}/${t.totalRamMb} MB · Termica ${t.thermalLevel.label} · Schede $activeTabCount · Popup bloccati ${_blockedPopupsCount.value}."
            }
            lower.startsWith("cerca ") || lower.startsWith("search ") -> {
                val q = input.substringAfter(" ").trim()
                onAction(AssistantAction.Navigate(q))
                "Cerco \"$q\" con ${p.searchEngine.displayName}."
            }
            lower.startsWith("apri ") || lower.startsWith("vai ") || lower.startsWith("open ") -> {
                val target = input.substringAfter(" ").trim()
                onAction(AssistantAction.Navigate(target))
                "Apro $target nella scheda attiva."
            }
            lower.contains(".") && !lower.contains(" ") -> {
                onAction(AssistantAction.Navigate(input))
                "Navigo verso $input."
            }
            else -> {
                onAction(AssistantAction.Navigate(input))
                "Avvio la ricerca \"$input\" su ${p.searchEngine.displayName}."
            }
        }

        appendAssistant(fromUser = false, text = reply)
    }

    private fun appendAssistant(fromUser: Boolean, text: String) {
        _assistantLog.update { current ->
            (current + AssistantEntry(fromUser = fromUser, text = text)).takeLast(24)
        }
    }
}
