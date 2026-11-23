package com.cefrium.sample

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cefrium.CefriumBrowser
import com.cefrium.compose.BackgroundMediaPolicy
import com.cefrium.compose.CefriumBridge
import com.cefrium.compose.CefriumNavigator
import com.cefrium.compose.CefriumView
import com.cefrium.compose.CefriumViewState
import com.cefrium.compose.DownloadEntry
import com.cefrium.compose.DownloadState
import com.cefrium.sample.data.SessionSnapshot
import com.cefrium.sample.engine.GamingBoostSession
import com.cefrium.sample.engine.KoKoroScripts
import com.cefrium.sample.engine.LiveFpsSampler
import com.cefrium.sample.engine.observeDisplayRefreshRate
import com.cefrium.sample.engine.querySupportedRefreshRates
import com.cefrium.sample.engine.readSystemSnapshot
import com.cefrium.sample.theme.KatanaSlashProgressBar
import com.cefrium.sample.theme.KoKoroTheme
import com.cefrium.sample.theme.LocalSamuraiPalette
import com.cefrium.sample.ui.BlockedPopupBanner
import com.cefrium.sample.ui.BookmarksSheet
import com.cefrium.sample.ui.DevicePickerState
import com.cefrium.sample.ui.DownloadsSheet
import com.cefrium.sample.ui.FloatingKoKoroEmblem
import com.cefrium.sample.ui.HistorySheet
import com.cefrium.sample.ui.KoKoroAssistantSheet
import com.cefrium.sample.ui.LiveGamingHudPill
import com.cefrium.sample.ui.PermissionPromptState
import com.cefrium.sample.ui.SamuraiContextMenuDialog
import com.cefrium.sample.ui.SamuraiControlCenterSheet
import com.cefrium.sample.ui.SamuraiDevicePickerDialog
import com.cefrium.sample.ui.SamuraiFindBar
import com.cefrium.sample.ui.SamuraiOmnibar
import com.cefrium.sample.ui.SamuraiPermissionDialog
import com.cefrium.sample.ui.SamuraiTabGridSheet
import com.cefrium.sample.ui.SamuraiTabStrip
import com.cefrium.sample.ui.SettingsHubScreen
import com.cefrium.sample.viewmodel.ActiveOverlay
import com.cefrium.sample.viewmodel.AssistantAction
import com.cefrium.sample.viewmodel.KoKoroViewModel
import java.io.File
import kotlinx.coroutines.delay

private const val EXTRA_SELECT_TAB = "com.aistudio.kokoro.browser.mediaSelectTab"

class BrowserTab(
    val id: Int,
    val state: CefriumViewState,
    val navigator: CefriumNavigator = CefriumNavigator(),
    val restoredTitle: String? = null,
    val openerId: Int? = null,
) {
    val adShowing = mutableStateOf(false)
    val discarded = mutableStateOf(false)
    val zenReaderActive = mutableStateOf(false)
    var window: CefriumBrowser? = null
    var lastUsed: Int = 0
}

class MainActivity : ComponentActivity() {

    private val viewModel: KoKoroViewModel by viewModels()

    private val pendingUrl = mutableStateOf<String?>(null)
    private val pendingSelectTabId = mutableStateOf<Int?>(null)
    val statusBarTopPx = mutableIntStateOf(0)
    val navBarBottomPx = mutableIntStateOf(0)
    var onTrimMemoryCb: ((Int) -> Unit)? = null

    private data class SafRequest(
        val id: Int,
        val name: String,
        val allow: (String?) -> Unit,
        val cancel: () -> Unit,
    )

    private var activeSafRequest: SafRequest? = null
    private val safTargets = HashMap<Int, Uri>()
    private val safTemp = HashMap<Int, String>()

    // Modern ActivityResultContracts.CreateDocument (replaces deprecated startActivityForResult)
    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("*/*"),
    ) { uri ->
        val req = activeSafRequest
        activeSafRequest = null
        if (req == null) return@registerForActivityResult
        if (uri != null) {
            val dir = File(cacheDir, "saf_dl").apply { mkdirs() }
            val tmp = File(dir, "${req.id}_${req.name}")
            safTargets[req.id] = uri
            safTemp[req.id] = tmp.absolutePath
            req.allow(tmp.absolutePath)
        } else {
            req.cancel()
        }
    }

    private val runtimePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ -> }

    fun askWhereToSave(
        id: Int,
        name: String,
        allow: (String?) -> Unit,
        cancel: () -> Unit,
    ) {
        activeSafRequest?.cancel?.invoke()
        activeSafRequest = SafRequest(id, name, allow, cancel)
        runCatching { createDocumentLauncher.launch(name) }.onFailure {
            activeSafRequest = null
            allow(null)
        }
    }

    fun finishSafDownload(id: Int): Uri? {
        val uri = safTargets.remove(id) ?: return null
        val tmp = safTemp.remove(id)
        if (tmp != null) {
            runCatching {
                contentResolver.openOutputStream(uri)?.use { out ->
                    File(tmp).inputStream().use { it.copyTo(out) }
                }
            }
            runCatching { File(tmp).delete() }
        }
        return uri
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        onTrimMemoryCb?.invoke(level)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestStartupPermissions()

        // Preserve system bars while allowing CEF SurfaceView to respect exact decor insets.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets ->
            statusBarTopPx.intValue = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            navBarBottomPx.intValue = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            insets
        }

        val initialIntentUrl = intentUrlFor(intent)
        intent?.getIntExtra(EXTRA_SELECT_TAB, -1)?.takeIf { it >= 0 }?.let {
            pendingSelectTabId.value = it
        }

        setContent {
            val prefs by viewModel.preferences.collectAsStateWithLifecycle()
            LaunchedEffect(prefs.theme.isDark) {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    show(WindowInsetsCompat.Type.systemBars())
                    isAppearanceLightStatusBars = !prefs.theme.isDark
                    isAppearanceLightNavigationBars = !prefs.theme.isDark
                }
            }

            KoKoroTheme(themeId = prefs.theme) {
                KoKoroBrowserScreen(
                    activity = this,
                    viewModel = viewModel,
                    intentUrl = initialIntentUrl,
                    pendingUrl = pendingUrl,
                    pendingSelectTabId = pendingSelectTabId,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentUrlFor(intent)?.let { pendingUrl.value = it }
        intent.getIntExtra(EXTRA_SELECT_TAB, -1).takeIf { it >= 0 }?.let {
            pendingSelectTabId.value = it
        }
    }

    private fun intentUrlFor(intent: Intent?): String? {
        val data = intent?.data ?: return null
        if (data.scheme == "file") {
            val path = data.path ?: return data.toString()
            return contentUriFor(this, path)?.toString() ?: data.toString()
        }
        return data.toString()
    }

    private fun requestStartupPermissions() {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) {
            runtimePermissionsLauncher.launch(needed.toTypedArray())
        }
    }
}

@Composable
private fun KoKoroBrowserScreen(
    activity: MainActivity,
    viewModel: KoKoroViewModel,
    intentUrl: String?,
    pendingUrl: androidx.compose.runtime.MutableState<String?>,
    pendingSelectTabId: androidx.compose.runtime.MutableState<Int?>,
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val overlay by viewModel.activeOverlay.collectAsStateWithLifecycle()
    val blockedAds by viewModel.blockedAdsCount.collectAsStateWithLifecycle()
    val blockedPopups by viewModel.blockedPopupsCount.collectAsStateWithLifecycle()
    val popupNotice by viewModel.lastBlockedPopup.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val assistantLog by viewModel.assistantLog.collectAsStateWithLifecycle()

    var tabs by remember { mutableStateOf<List<BrowserTab>>(emptyList()) }
    var nextId by remember { mutableIntStateOf(1) }
    var tabUseSeq by remember { mutableIntStateOf(0) }
    var activeTabId by remember { mutableIntStateOf(0) }
    var sessionReady by remember { mutableStateOf(false) }

    // Async DataStore session & homepage bootstrap
    LaunchedEffect(Unit) {
        val initialPrefs = viewModel.loadInitialPreferences()
        val snap = viewModel.loadSession()
        val initialUrls = when {
            intentUrl != null -> listOf(intentUrl)
            snap.urls.isNotEmpty() -> snap.urls
            else -> listOf(initialPrefs.homepage)
        }
        val initialTitles = if (intentUrl == null && snap.urls.isNotEmpty()) snap.titles else emptyList()
        val initialNavs = if (intentUrl == null && snap.urls.isNotEmpty()) snap.navStates else emptyList()

        val hydrated = initialUrls.mapIndexed { i, url ->
            val blob = initialNavs.getOrNull(i)?.takeIf { it.isNotBlank() }?.let {
                runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull()
            }
            BrowserTab(
                id = i,
                state = CefriumViewState(url, navigationState = blob),
                restoredTitle = initialTitles.getOrNull(i)?.takeIf { it.isNotBlank() },
            )
        }
        tabs = hydrated
        nextId = hydrated.size
        activeTabId = if (intentUrl == null) snap.activeIndex.coerceIn(0, (hydrated.size - 1).coerceAtLeast(0)) else 0
        sessionReady = true
    }

    val activeTab = tabs.find { it.id == activeTabId }
    val tabListState = rememberLazyListState()

    var urlText by remember { mutableStateOf("") }
    var editingUrl by remember { mutableStateOf(false) }
    var showFind by remember { mutableStateOf(false) }
    var findText by remember { mutableStateOf("") }
    var immersive by remember(activeTabId) { mutableStateOf(false) }
    var isResumed by remember { mutableStateOf(true) }

    val appDownloads = remember { mutableStateListOf<DownloadEntry>() }
    val downloadNotifier = remember { DownloadNotifier(activity) }
    val safOpenUri = remember { mutableMapOf<Int, Uri>() }

    var permissionPrompt by remember { mutableStateOf<PermissionPromptState?>(null) }
    var devicePicker by remember { mutableStateOf<DevicePickerState?>(null) }

    fun addTab(url: String = prefs.homepage, incognito: Boolean = false) {
        val tab = BrowserTab(nextId, CefriumViewState(url, incognito = incognito))
        tabs = tabs + tab
        activeTabId = tab.id
        nextId++
    }

    fun closeTab(id: Int) {
        val closing = tabs.find { it.id == id }
        closing?.state?.close()
        val remaining = tabs.filter { it.id != id }
        tabs = remaining
        val opener = remaining.find { it.id == closing?.openerId }
        when {
            remaining.isEmpty() -> addTab()
            activeTabId == id && opener != null -> activeTabId = opener.id
            activeTabId == id -> activeTabId = remaining.last().id
        }
    }

    fun openWindowTab(opener: BrowserTab, popup: CefriumBrowser, url: String) {
        val tab = BrowserTab(
            id = nextId,
            state = CefriumViewState.forWindow(popup, url, opener.state.incognito),
            openerId = opener.id,
        )
        tab.window = popup
        val at = (tabs.indexOfFirst { it.id == opener.id } + 1).coerceIn(0, tabs.size)
        tabs = tabs.toMutableList().apply { add(at, tab) }
        activeTabId = tab.id
        nextId++
    }

    fun isPlayingMediaAudio(): Boolean {
        val am = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return am.activePlaybackConfigurations.any {
            it.audioAttributes.usage == AudioAttributes.USAGE_MEDIA
        }
    }

    fun discardBackgroundTabs(keepWarm: Int) {
        if (isPlayingMediaAudio()) return
        val victims = tabs
            .filter {
                it.id != activeTabId && !it.state.incognito && !it.discarded.value && !it.navigator.isPlayingAudio()
            }
            .sortedByDescending { it.lastUsed }
            .drop(keepWarm)
            .map { it.id }
            .toSet()
        if (victims.isEmpty()) return
        tabs = tabs.map { tab ->
            if (tab.id !in victims) tab
            else {
                val blob = tab.navigator.serializeNavigationState()
                val url = tab.state.lastLoadedUrl ?: tab.state.initialUrl ?: prefs.homepage
                val title = tab.state.pageTitle?.takeIf { it.isNotBlank() && it != "about:blank" } ?: tab.restoredTitle
                tab.state.close()
                BrowserTab(
                    id = tab.id,
                    state = CefriumViewState(url, navigationState = blob),
                    restoredTitle = title,
                    openerId = tab.openerId,
                ).also {
                    it.discarded.value = true
                    it.lastUsed = tab.lastUsed
                }
            }
        }
    }

    fun mediaTapIntent(tabId: Int): PendingIntent {
        val intent = Intent(activity, activity.javaClass).apply {
            putExtra(EXTRA_SELECT_TAB, tabId)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            activity,
            tabId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun wireHandlers(tab: BrowserTab) {
        val ua = KoKoroScripts.resolveUserAgent(
            preset = prefs.userAgentPreset,
            desktopOverride = prefs.desktopMode,
            customUserAgent = prefs.customUserAgent,
        )
        tab.navigator.setUserAgentOverride(ua)
        tab.navigator.setMediaSessionEnabled(true)
        tab.navigator.setMediaSessionActivity(mediaTapIntent(tab.id))
        tab.navigator.setPullToRefreshEnabled(true)
        tab.navigator.setDarkMode(prefs.forceDarkWeb)
        tab.navigator.setPermissionHandler { origin, permissions, cb ->
            activity.runOnUiThread { permissionPrompt = PermissionPromptState(origin, permissions, cb) }
        }
        tab.navigator.setDeviceChooserHandler(object : CefriumBrowser.DeviceChooserHandler {
            override fun onShowDeviceChooser(session: CefriumBrowser.DeviceChooserSession) {
                activity.runOnUiThread { devicePicker = DevicePickerState(session) }
            }
            override fun onDeviceAdded(session: CefriumBrowser.DeviceChooserSession, device: CefriumBrowser.ChooserDevice) {
                activity.runOnUiThread {
                    devicePicker?.let { if (it.session === session) it.add(device) }
                }
            }
            override fun onDeviceRemoved(session: CefriumBrowser.DeviceChooserSession, deviceId: String) {
                activity.runOnUiThread {
                    devicePicker?.let { if (it.session === session) it.remove(deviceId) }
                }
            }
            override fun onDeviceChooserClosed(session: CefriumBrowser.DeviceChooserSession) {
                activity.runOnUiThread {
                    if (devicePicker?.session === session) devicePicker = null
                }
            }
        })
    }

    fun navigateTo(input: String) {
        val resolved = viewModel.resolveNavigationUrl(input)
        activeTab?.navigator?.loadUrl(resolved)
    }

    fun toggleZenReaderOnActive() {
        val tab = activeTab ?: return
        val next = !tab.zenReaderActive.value
        tab.zenReaderActive.value = next
        tab.navigator.evaluateJavaScript(KoKoroScripts.zenReaderJs(next, prefs.theme.isDark))
        viewModel.showStatus(if (next) "Lettura Zen attiva" else "Lettura Zen disattivata")
    }

    // Hierarchical BackHandler
    BackHandler {
        when {
            overlay != ActiveOverlay.NONE -> viewModel.closeOverlay()
            immersive -> immersive = false
            editingUrl -> editingUrl = false
            showFind -> {
                showFind = false
                activeTab?.navigator?.stopFinding()
            }
            activeTab?.zenReaderActive?.value == true -> toggleZenReaderOnActive()
            activeTab?.state?.isFullscreen == true -> activeTab.navigator.exitFullscreen()
            activeTab?.navigator?.canGoBack == true -> activeTab.navigator.navigateBack()
            activeTab != null && tabs.any { it.id == activeTab.openerId } -> closeTab(activeTab.id)
            else -> activity.moveTaskToBack(true)
        }
    }

    // Lifecycle & memory pressure management
    DisposableEffect(activity) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> discardBackgroundTabs(keepWarm = 2)
                Lifecycle.Event.ON_RESUME -> isResumed = true
                Lifecycle.Event.ON_PAUSE -> isResumed = false
                else -> Unit
            }
        }
        activity.lifecycle.addObserver(obs)
        activity.onTrimMemoryCb = { level ->
            when {
                level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE -> discardBackgroundTabs(keepWarm = 0)
                level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
                    level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> discardBackgroundTabs(keepWarm = 1)
            }
        }
        onDispose {
            activity.lifecycle.removeObserver(obs)
            activity.onTrimMemoryCb = null
        }
    }

    DisposableEffect(Unit) {
        onDispose { tabs.forEach { it.state.close() } }
    }

    // Real Display Hz, Live Choreographer FPS & System Health (RAM + Thermal) monitors
    DisposableEffect(activity, isResumed) {
        viewModel.updateSupportedModes(activity.querySupportedRefreshRates())
        val stopDisplay = if (isResumed) {
            activity.observeDisplayRefreshRate { hz -> viewModel.updateDisplayRate(hz) }
        } else {
            {}
        }
        val fpsSampler = LiveFpsSampler { fps -> viewModel.updateLiveFps(fps) }
        if (isResumed) fpsSampler.start()
        onDispose {
            stopDisplay()
            fpsSampler.stop()
        }
    }

    LaunchedEffect(isResumed) {
        while (isResumed) {
            viewModel.updateSystemHealth(activity.readSystemSnapshot())
            delay(1_500L)
        }
    }

    // Real Gaming Boost window + surface controller
    val currentUrl = activeTab?.state?.lastLoadedUrl ?: activeTab?.state?.initialUrl ?: ""
    val currentHost = viewModel.hostOf(currentUrl)
    val isGamePage = remember(currentUrl) {
        val uri = Uri.parse(currentUrl)
        val host = uri.host.orEmpty().lowercase()
        host.contains("lasthavens.com") || uri.path?.trimEnd('/') == "/play"
    }
    val shouldEngageBoost = isResumed && (prefs.gamingBoostEnabled || (prefs.autoBoostGameDomains && isGamePage))

    DisposableEffect(
        activity,
        shouldEngageBoost,
        prefs.targetRefreshRate,
        prefs.sustainedPerformance,
        prefs.keepScreenOnInBoost,
        activeTabId,
    ) {
        var session: GamingBoostSession? = null
        if (shouldEngageBoost) {
            if (prefs.autoTrimRamOnBoost) discardBackgroundTabs(keepWarm = 0)
            session = GamingBoostSession(
                activity = activity,
                targetHz = prefs.targetRefreshRate,
                sustainedPerformance = prefs.sustainedPerformance,
                keepScreenOn = prefs.keepScreenOnInBoost,
            ).also {
                it.start()
                viewModel.updateBoostTelemetry(
                    active = true,
                    appliedHz = it.appliedHz,
                    surfaceLocked = it.isSurfaceLocked,
                )
            }
        } else {
            viewModel.updateBoostTelemetry(active = false, appliedHz = 0, surfaceLocked = false)
        }
        onDispose {
            session?.stop()
            viewModel.updateBoostTelemetry(active = false, appliedHz = 0, surfaceLocked = false)
        }
    }

    // Keep active tab LRU & scroll into view
    LaunchedEffect(activeTabId) {
        activeTab?.let {
            it.lastUsed = ++tabUseSeq
            it.discarded.value = false
        }
    }

    val activeFullscreen = activeTab?.state?.isFullscreen == true
    LaunchedEffect(activeTabId, tabs.size, activeFullscreen) {
        if (activeFullscreen) return@LaunchedEffect
        val idx = tabs.indexOfFirst { it.id == activeTabId }
        if (idx >= 0) {
            withFrameNanos {}
            val info = tabListState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == idx }
            val visible = item != null &&
                item.offset >= info.viewportStartOffset &&
                item.offset + item.size <= info.viewportEndOffset
            if (!visible) tabListState.animateScrollToItem(idx)
        }
    }

    // Wire download events per tab
    LaunchedEffect(tabs) {
        tabs.forEach { tab ->
            tab.state.onBeforeDownload = { entry, allow, cancel ->
                if (prefs.askWhereToSave) {
                    activity.askWhereToSave(entry.id, entry.name, allow, cancel)
                } else {
                    allow(null)
                }
            }
            tab.state.onDownloadEvent = { entry ->
                val i = appDownloads.indexOfFirst { it.id == entry.id }
                if (i >= 0) appDownloads[i] = entry else appDownloads.add(0, entry)
                downloadNotifier.update(entry)
                when (entry.state) {
                    DownloadState.STARTED -> {
                        DownloadRegistry.register(entry.id) { action ->
                            when (action) {
                                0 -> tab.state.cancelDownload(entry.id)
                                1 -> tab.state.pauseDownload(entry.id)
                                2 -> tab.state.resumeDownload(entry.id)
                            }
                        }
                        Toast.makeText(activity, "Download avviato: ${entry.name}", Toast.LENGTH_SHORT).show()
                    }
                    DownloadState.COMPLETE -> {
                        DownloadRegistry.unregister(entry.id)
                        activity.finishSafDownload(entry.id)?.let { safOpenUri[entry.id] = it }
                    }
                    DownloadState.CANCELED -> {
                        DownloadRegistry.unregister(entry.id)
                        activity.finishSafDownload(entry.id)
                    }
                    else -> Unit
                }
            }
        }
    }

    // Sync URL bar & record history
    LaunchedEffect(activeTabId, activeTab?.state?.lastLoadedUrl) {
        val loaded = activeTab?.state?.lastLoadedUrl
        val shown = loaded ?: activeTab?.state?.initialUrl ?: ""
        urlText = if (shown == "about:blank") "" else shown
        activeTab?.zenReaderActive?.value = false
        if (loaded != null && loaded != "about:blank" && activeTab?.state?.incognito != true) {
            viewModel.recordHistory(loaded, activeTab?.state?.pageTitle ?: loaded)
        }
    }

    // Re-apply Cefrium handlers & QoL JS scripts when tab loads or preferences change
    LaunchedEffect(
        activeTabId,
        activeTab?.state?.lastLoadedUrl,
        activeTab?.state?.isLoading,
        prefs.forceDarkWeb,
        prefs.desktopMode,
        prefs.userAgentPreset,
        prefs.customUserAgent,
        prefs.pageZoomPercent,
        prefs.touchBoostEnabled,
        shouldEngageBoost,
    ) {
        val tab = activeTab ?: return@LaunchedEffect
        wireHandlers(tab)
        if (tab.state.isLoading == false) {
            val loaded = tab.state.lastLoadedUrl.orEmpty()
            val whitelisted = currentHost.lowercase() in prefs.adShieldWhitelist
            if (loaded.contains("youtube.com") && prefs.adShieldEnabled && !whitelisted) {
                tab.navigator.evaluateJavaScript(KoKoroScripts.YT_AD_OBSERVER_JS)
            } else {
                tab.adShowing.value = false
            }
            if (prefs.touchBoostEnabled && (shouldEngageBoost || isGamePage)) {
                tab.navigator.evaluateJavaScript(KoKoroScripts.PASSIVE_TOUCH_BOOST_JS)
            }
            tab.navigator.evaluateJavaScript(KoKoroScripts.pageZoomJs(prefs.pageZoomPercent))
            tab.navigator.evaluateJavaScript(KoKoroScripts.forceDarkFallbackJs(prefs.forceDarkWeb))
        }
    }

    // Deep links & media tab switch
    LaunchedEffect(pendingUrl.value) {
        pendingUrl.value?.let { url ->
            activeTab?.navigator?.loadUrl(url)
            pendingUrl.value = null
        }
    }

    LaunchedEffect(pendingSelectTabId.value, tabs) {
        pendingSelectTabId.value?.let { id ->
            if (tabs.any { it.id == id }) {
                activeTabId = id
                pendingSelectTabId.value = null
            }
        }
    }

    // Recover renderer termination upon foreground return
    LaunchedEffect(activeTabId, isResumed) {
        if (isResumed && activeTab?.state?.rendererTermination != null) {
            activeTab.navigator.reload()
        }
    }

    // Persist session to DataStore
    LaunchedEffect(sessionReady, tabs, activeTabId, tabs.map { it.state.lastLoadedUrl }, tabs.map { it.state.pageTitle }) {
        if (!sessionReady) return@LaunchedEffect
        val persistable = tabs.filter { !it.state.incognito }
        val urls = persistable.map { it.state.lastLoadedUrl ?: it.state.initialUrl ?: "about:blank" }
        val titles = persistable.map {
            it.state.pageTitle?.takeIf { t -> t.isNotBlank() && t != "about:blank" } ?: it.restoredTitle ?: ""
        }
        val navStates = persistable.map {
            (it.navigator.serializeNavigationState() ?: it.state.navigationState)?.let { b ->
                Base64.encodeToString(b, Base64.NO_WRAP)
            } ?: ""
        }
        viewModel.saveSession(
            SessionSnapshot(
                urls = urls,
                titles = titles,
                navStates = navStates,
                activeIndex = persistable.indexOfFirst { it.id == activeTabId }.coerceAtLeast(0),
            ),
        )
    }

    // Fullscreen system bars visibility
    val fullscreen = immersive || activeFullscreen
    LaunchedEffect(fullscreen) {
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            if (fullscreen) {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            } else {
                show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val density = LocalDensity.current
    val statusBarTopDp = with(density) { activity.statusBarTopPx.intValue.toDp() }
    val navBarBottomDp = with(density) { activity.navBarBottomPx.intValue.toDp() }

    val controlsBelowPage = remember {
        object : Arrangement.Vertical {
            override fun Density.arrange(totalSize: Int, sizes: IntArray, outPositions: IntArray) {
                if (sizes.isEmpty()) return
                var top = sizes.last()
                for (i in 0 until sizes.lastIndex) {
                    outPositions[i] = top
                    top += sizes[i]
                }
                outPositions[sizes.lastIndex] = 0
            }
        }
    }

    val isBookmarked = remember(bookmarks, activeTab?.state?.lastLoadedUrl) {
        val u = activeTab?.state?.lastLoadedUrl
        u != null && bookmarks.any { it.url == u }
    }

    val samurai = LocalSamuraiPalette.current

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = if (prefs.bottomBar) controlsBelowPage else Arrangement.Top,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .then(
                    if (fullscreen) Modifier
                    else Modifier.padding(top = statusBarTopDp, bottom = navBarBottomDp),
                ),
        ) {
            if (!fullscreen) {
                SamuraiTabStrip(
                    tabs = tabs,
                    activeTabId = activeTabId,
                    tabListState = tabListState,
                    onSelectTab = { activeTabId = it },
                    onCloseTab = { closeTab(it) },
                    onReorderTabs = { from, to ->
                        tabs = tabs.toMutableList().apply { add(to, removeAt(from)) }
                    },
                    onAddTab = {
                        addTab("about:blank")
                        urlText = ""
                        editingUrl = true
                    },
                    onOpenTabGrid = { viewModel.openOverlay(ActiveOverlay.TABS_GRID) },
                )

                SamuraiOmnibar(
                    urlText = urlText,
                    editingUrl = editingUrl,
                    incognito = activeTab?.state?.incognito == true,
                    bookmarked = isBookmarked,
                    zenReaderActive = activeTab?.zenReaderActive?.value == true,
                    showFind = showFind,
                    prefs = prefs,
                    telemetry = telemetry,
                    onEditingChange = { editingUrl = it },
                    onCommitUrl = { entered ->
                        urlText = entered
                        navigateTo(entered)
                    },
                    onBack = { activeTab?.navigator?.navigateBack() },
                    onForward = { activeTab?.navigator?.navigateForward() },
                    onReload = { activeTab?.navigator?.reload() },
                    onToggleBookmark = {
                        val u = activeTab?.state?.lastLoadedUrl ?: return@SamuraiOmnibar
                        viewModel.toggleBookmark(u, activeTab.state.pageTitle ?: u)
                        viewModel.showStatus(if (isBookmarked) "Rimosso dai preferiti" else "Salvato nei preferiti")
                    },
                    onToggleFind = {
                        showFind = !showFind
                        if (!showFind) activeTab?.navigator?.stopFinding()
                    },
                    onOpenControlCenter = { viewModel.openOverlay(ActiveOverlay.CONTROL_CENTER) },
                )

                if (showFind) {
                    SamuraiFindBar(
                        query = findText,
                        activeMatch = activeTab?.state?.findActiveMatch ?: 0,
                        totalMatches = activeTab?.state?.findMatchCount ?: 0,
                        onQueryChange = { q ->
                            findText = q
                            activeTab?.navigator?.find(q, forward = true, findNext = false)
                        },
                        onPrev = { activeTab?.navigator?.find(findText, forward = false, findNext = true) },
                        onNext = { activeTab?.navigator?.find(findText, forward = true, findNext = true) },
                        onClose = {
                            showFind = false
                            activeTab?.navigator?.stopFinding()
                        },
                    )
                }

                KatanaSlashProgressBar(
                    enabled = activeTab?.state?.isLoading == true,
                    animationsEnabled = prefs.samuraiAnimations,
                )
            }

            // Cefrium Web Viewport + Live Overlays
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                activeTab?.let { tab ->
                    key(tab.id) {
                        CefriumView(
                            state = tab.state,
                            onCreateWindow = { popup, url ->
                                if (prefs.popupBlockerEnabled && url.isNotBlank()) {
                                    viewModel.recordPopupBlocked(url)
                                    false
                                } else {
                                    openWindowTab(tab, popup, url)
                                    true
                                }
                            },
                            onCloseWindow = { popup ->
                                tabs.find { it.window === popup }?.let { closeTab(it.id) }
                            },
                            modifier = Modifier.fillMaxSize(),
                            navigator = tab.navigator,
                            backgroundMediaPolicy = BackgroundMediaPolicy.NONE,
                            bridge = CefriumBridge(
                                CefriumBrowser.QueryOriginPolicy { url ->
                                    val u = Uri.parse(url)
                                    val host = u.host ?: ""
                                    u.scheme == "https" && (host == "youtube.com" || host.endsWith(".youtube.com"))
                                },
                            ) { request, _, callback ->
                                when (request) {
                                    "ad:1" -> {
                                        if (!tab.adShowing.value) viewModel.incrementBlockedAds()
                                        tab.adShowing.value = true
                                        callback.success("")
                                        true
                                    }
                                    "ad:0" -> {
                                        tab.adShowing.value = false
                                        callback.success("")
                                        true
                                    }
                                    else -> false
                                }
                            },
                        )
                    }

                    // YouTube ad shielded badge
                    if (tab.adShowing.value) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = samurai.glassSurface,
                            border = BorderStroke(1.dp, samurai.goldAccent.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(13.dp),
                                    strokeWidth = 2.dp,
                                    color = samurai.goldAccent,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Annuncio schermato", style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                        }
                    }
                }

                // Live Hardware Gaming HUD Overlay Pill
                LiveGamingHudPill(
                    visible = prefs.showGamingHudOverlay,
                    telemetry = telemetry,
                    onClick = { viewModel.openOverlay(ActiveOverlay.ASSISTANT) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                )

                // Floating Animated KoKoro Brand Assistant Mascot
                if (prefs.showFloatingKoKoro && !fullscreen) {
                    FloatingKoKoroEmblem(
                        boostActive = telemetry.boostActive,
                        incognito = activeTab?.state?.incognito == true,
                        zenReaderActive = activeTab?.zenReaderActive?.value == true,
                        animationsEnabled = prefs.samuraiAnimations,
                        onClick = { viewModel.openOverlay(ActiveOverlay.ASSISTANT) },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 14.dp, bottom = 18.dp),
                    )
                }

                // Popup Blocked Samurai Banner
                BlockedPopupBanner(
                    notice = popupNotice,
                    onAllow = { url ->
                        viewModel.dismissPopupNotice()
                        addTab(url, incognito = activeTab?.state?.incognito == true)
                    },
                    onDismiss = { viewModel.dismissPopupNotice() },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )

                // Subtle Samurai Status Toast Pill
                androidx.compose.animation.AnimatedVisibility(
                    visible = statusBanner != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp),
                ) {
                    statusBanner?.let { msg ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = samurai.glassSurface,
                            border = BorderStroke(1.dp, samurai.goldAccent.copy(alpha = 0.7f)),
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }

        // Full-Screen Settings Hub & Modal Overlays
        when (overlay) {
            ActiveOverlay.CONTROL_CENTER -> SamuraiControlCenterSheet(
                prefs = prefs,
                telemetry = telemetry,
                zenReaderActive = activeTab?.zenReaderActive?.value == true,
                blockedAdsCount = blockedAds,
                blockedPopupsCount = blockedPopups,
                activeTabsCount = tabs.size,
                onDismiss = { viewModel.closeOverlay() },
                onToggleGamingBoost = { viewModel.toggleGamingBoost() },
                onSelectTargetHz = { hz -> viewModel.setTargetRefreshRate(hz) },
                onToggleTouchBoost = { viewModel.updatePreferences { it.copy(touchBoostEnabled = !it.touchBoostEnabled) } },
                onToggleSustainedPerf = { viewModel.updatePreferences { it.copy(sustainedPerformance = !it.sustainedPerformance) } },
                onToggleGamingHud = { viewModel.updatePreferences { it.copy(showGamingHudOverlay = !it.showGamingHudOverlay) } },
                onTrimRamNow = {
                    discardBackgroundTabs(keepWarm = 0)
                    viewModel.showStatus("RAM ottimizzata: schede in background sospese")
                },
                onToggleAdShield = {
                    viewModel.toggleAdShield()
                    viewModel.showStatus(if (!prefs.adShieldEnabled) "Scudo Annunci attivo" else "Scudo Annunci disattivato")
                },
                onToggleZenReader = {
                    viewModel.closeOverlay()
                    toggleZenReaderOnActive()
                },
                onToggleDesktopMode = {
                    viewModel.toggleDesktopMode()
                    activeTab?.navigator?.reload()
                    viewModel.showStatus(if (!prefs.desktopMode) "Modalità Desktop attiva" else "Modalità Mobile attiva")
                },
                onToggleForceDark = { viewModel.toggleForceDarkWeb() },
                onTogglePopupBlocker = { viewModel.togglePopupBlocker() },
                onChangeZoom = { nextZoom -> viewModel.setPageZoom(nextZoom) },
                onQuickClean = {
                    activeTab?.navigator?.clearCache()
                    discardBackgroundTabs(keepWarm = 0)
                    viewModel.closeOverlay()
                    viewModel.showStatus("Pulizia rapida completata (cache + RAM)")
                },
                onNewIncognitoTab = {
                    viewModel.closeOverlay()
                    addTab("about:blank", incognito = true)
                    urlText = ""
                    editingUrl = true
                },
                onOpenBookmarks = { viewModel.openOverlay(ActiveOverlay.BOOKMARKS) },
                onOpenHistory = { viewModel.openOverlay(ActiveOverlay.HISTORY) },
                onOpenDownloads = { viewModel.openOverlay(ActiveOverlay.DOWNLOADS) },
                onShare = {
                    viewModel.closeOverlay()
                    shareUrl(activity, activeTab?.state?.lastLoadedUrl, activeTab?.state?.pageTitle)
                },
                onExportPdf = {
                    viewModel.closeOverlay()
                    val out = File(activity.getExternalFilesDir(null), "kokoro_page.pdf").absolutePath
                    activeTab?.navigator?.printToPdf(out) { _, _ -> }
                    viewModel.showStatus("Esportazione PDF avviata")
                },
                onToggleImmersive = {
                    viewModel.closeOverlay()
                    immersive = !immersive
                },
                onToggleBarPosition = { viewModel.toggleBottomBar() },
                onOpenAssistant = { viewModel.openOverlay(ActiveOverlay.ASSISTANT) },
                onOpenSettings = { viewModel.openOverlay(ActiveOverlay.SETTINGS) },
            )

            ActiveOverlay.SETTINGS -> SettingsHubScreen(
                prefs = prefs,
                telemetry = telemetry,
                currentHost = currentHost,
                activeTabsCount = tabs.size,
                onBack = { viewModel.closeOverlay() },
                onUpdatePrefs = { viewModel.updatePreferences(it) },
                onToggleWhitelistHost = { viewModel.toggleWhitelistDomain(it) },
                onClearCache = {
                    activeTab?.navigator?.clearCache()
                    viewModel.showStatus("Cache del motore svuotata")
                },
                onClearHistory = { viewModel.clearHistory() },
                onTrimBackgroundTabs = {
                    discardBackgroundTabs(keepWarm = 0)
                    viewModel.showStatus("Schede in background sospese")
                },
                onReloadActiveTab = { activeTab?.navigator?.reload() },
            )

            ActiveOverlay.ASSISTANT -> KoKoroAssistantSheet(
                prefs = prefs,
                telemetry = telemetry,
                entries = assistantLog,
                activePageTitle = activeTab?.state?.pageTitle ?: "",
                activeTabsCount = tabs.size,
                onExecuteCommand = { cmd ->
                    viewModel.handleAssistantCommand(cmd, tabs.size) { action ->
                        when (action) {
                            is AssistantAction.Navigate -> {
                                navigateTo(action.urlOrQuery)
                                viewModel.closeOverlay()
                            }
                            is AssistantAction.OpenNewTab -> {
                                addTab(action.url ?: "about:blank", incognito = action.incognito)
                                if (action.url == null) {
                                    urlText = ""
                                    editingUrl = true
                                }
                                viewModel.closeOverlay()
                            }
                            AssistantAction.ToggleZenReader -> {
                                toggleZenReaderOnActive()
                                viewModel.closeOverlay()
                            }
                            AssistantAction.TrimMemoryAndCache -> {
                                activeTab?.navigator?.clearCache()
                                discardBackgroundTabs(keepWarm = 0)
                            }
                            AssistantAction.OpenFindInPage -> {
                                showFind = true
                                viewModel.closeOverlay()
                            }
                            AssistantAction.ReloadActiveTab -> activeTab?.navigator?.reload()
                            is AssistantAction.OpenOverlay -> viewModel.openOverlay(action.overlay)
                        }
                    }
                },
                onDismiss = { viewModel.closeOverlay() },
            )

            ActiveOverlay.TABS_GRID -> SamuraiTabGridSheet(
                tabs = tabs,
                activeTabId = activeTabId,
                onSelectTab = { activeTabId = it },
                onCloseTab = { closeTab(it) },
                onNewTab = { incognito ->
                    addTab("about:blank", incognito = incognito)
                    urlText = ""
                    editingUrl = true
                },
                onDismiss = { viewModel.closeOverlay() },
            )

            ActiveOverlay.BOOKMARKS -> BookmarksSheet(
                bookmarks = bookmarks,
                onOpen = { activeTab?.navigator?.loadUrl(it) },
                onDelete = { viewModel.removeBookmark(it) },
                onDismiss = { viewModel.closeOverlay() },
            )

            ActiveOverlay.HISTORY -> HistorySheet(
                history = history,
                onOpen = { activeTab?.navigator?.loadUrl(it) },
                onClearAll = { viewModel.clearHistory() },
                onDismiss = { viewModel.closeOverlay() },
            )

            ActiveOverlay.DOWNLOADS -> DownloadsSheet(
                downloads = appDownloads,
                onOpen = { entry ->
                    val mime = entry.mimeType
                    val saf = safOpenUri[entry.id]
                    when {
                        saf != null && isBrowserViewable(mime, entry.name) -> addTab(saf.toString())
                        saf != null -> runCatching {
                            activity.startActivity(
                                Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(saf, mime.ifBlank { "*/*" })
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                },
                            )
                        }
                        else -> {
                            val uri = contentUriFor(activity, entry.path)
                            if (uri != null && isBrowserViewable(mime, entry.name)) addTab(uri.toString())
                            else openDownloadedFile(activity, entry.path, mime)
                        }
                    }
                },
                onClearCompleted = {
                    appDownloads.removeAll { it.state == DownloadState.COMPLETE || it.state == DownloadState.CANCELED }
                },
                onDismiss = { viewModel.closeOverlay() },
            )

            ActiveOverlay.NONE -> Unit
        }

        // Engine-driven dialogs (Context Menu, Permissions, Web Bluetooth/USB/Serial Chooser)
        activeTab?.state?.contextMenu?.let { menu ->
            SamuraiContextMenuDialog(
                menu = menu,
                navigator = activeTab.navigator,
                activeIncognito = activeTab.state.incognito,
                onOpenInNewTab = { url, incognito -> addTab(url, incognito = incognito) },
                onDismiss = { activeTab.state.contextMenu = null },
            )
        }

        permissionPrompt?.let { prompt ->
            SamuraiPermissionDialog(
                prompt = prompt,
                onResult = { granted ->
                    prompt.callback.respond(granted)
                    permissionPrompt = null
                },
            )
        }

        devicePicker?.let { picker ->
            SamuraiDevicePickerDialog(
                picker = picker,
                onSelect = { id ->
                    picker.session.select(id)
                    devicePicker = null
                },
                onCancel = {
                    picker.session.cancel()
                    devicePicker = null
                },
            )
        }
    }
}

private fun shareUrl(activity: Activity, url: String?, title: String?) {
    if (url.isNullOrEmpty()) return
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, url)
    if (!title.isNullOrEmpty()) send.putExtra(Intent.EXTRA_SUBJECT, title)
    activity.startActivity(Intent.createChooser(send, title))
}
