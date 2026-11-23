package com.cefrium.sample

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.cefrium.Cefrium
import com.cefrium.CefriumBrowser
import com.cefrium.compose.BackgroundMediaPolicy
import com.cefrium.compose.CefriumNavigator
import com.cefrium.compose.CefriumBridge
import com.cefrium.compose.CefriumView
import com.cefrium.compose.CefriumViewState

/**
 * Cefrium reference browser, built entirely on the Compose Tier A API
 * (CefriumView / CefriumViewState / CefriumNavigator). Migrated from the Java
 * View-based SurfaceActivity without feature regression: tabs, find-in-page,
 * fullscreen, dark mode, media session, downloads, bookmarks, history,
 * settings, context menu, permissions, and the frontier device chooser
 * (Web Bluetooth/USB/Serial). Data layers (BookmarkManager/HistoryManager/
 * SettingsManager) are reused unchanged.
 */
class MainActivity : ComponentActivity() {

    // A VIEW-intent URL (deep link / `am start -d`) to load into the active tab.
    private val pendingUrl = mutableStateOf<String?>(null)

    // Tab id to activate, from a media-control tap (open the tab that is playing).
    private val pendingSelectTabId = mutableStateOf<Int?>(null)

    // Status-bar inset height (px), read from the decor view because the CEF content
    // layer consumes Compose's WindowInsets. Used to pad the toolbar under the status bar.
    val statusBarTopPx = mutableStateOf(0)

    // Navigation-bar inset height (px), same reason: pad the content bottom so a
    // page's bottom UI (e.g. YouTube's controls) is not hidden under the Android
    // navigation bar.
    val navBarBottomPx = mutableStateOf(0)

    // Set by BrowserScreen; receives the trim level and sheds background tabs.
    var onTrimMemoryCb: ((Int) -> Unit)? = null

    // --- "Ask where to save" (SAF) download support ---
    private class SafReq(
        val id: Int, val name: String,
        val allow: (String?) -> Unit, val cancel: () -> Unit,
    )
    private val safRequests = HashMap<Int, SafReq>()          // requestCode -> req
    private val safTargets = HashMap<Int, android.net.Uri>()  // downloadId -> chosen uri
    private val safTemp = HashMap<Int, String>()              // downloadId -> temp path
    private var safNextRc = 40000

    // Called from BrowserScreen.onBeforeDownload when the setting is on: pop the
    // SAF picker; on pick, download to a temp file and copy it to the chosen
    // location when complete (CEF writes to a path, SAF gives a content:// uri).
    fun askWhereToSave(
        id: Int, name: String, mime: String,
        allow: (String?) -> Unit, cancel: () -> Unit,
    ) {
        val rc = safNextRc++
        safRequests[rc] = SafReq(id, name, allow, cancel)
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mime.ifBlank { "application/octet-stream" }
            putExtra(Intent.EXTRA_TITLE, name)
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(intent, rc)
        } catch (e: Exception) {
            safRequests.remove(rc)
            allow(null)  // no document picker available -> fall back to auto-save
        }
    }

    // Called from BrowserScreen when a download completes. If it was a SAF
    // "save as", copy the temp file to the chosen uri and return that uri (so the
    // list/notification opens the real file); else null.
    fun finishSafDownload(id: Int): android.net.Uri? {
        val uri = safTargets.remove(id) ?: return null
        val tmp = safTemp.remove(id)
        if (tmp != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { out ->
                    java.io.File(tmp).inputStream().use { it.copyTo(out) }
                }
            } catch (e: Exception) {
                // Leave the temp file for the user; nothing else to do.
            }
            runCatching { java.io.File(tmp).delete() }
        }
        return uri
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val req = safRequests.remove(requestCode)
        if (req != null) {
            val uri = if (resultCode == RESULT_OK) data?.data else null
            if (uri != null) {
                val dir = java.io.File(cacheDir, "saf_dl").apply { mkdirs() }
                val tmp = java.io.File(dir, "${req.id}_${req.name}")
                safTargets[req.id] = uri
                safTemp[req.id] = tmp.absolutePath
                req.allow(tmp.absolutePath)
            } else {
                req.cancel()
            }
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        onTrimMemoryCb?.invoke(level)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureRuntimePermissions()

        // Edge-to-edge (forced on Android 15+), but keep the system bars visible -- the
        // Chromium BrowserUI theme otherwise leaves the status bar hidden. The CEF content
        // layer consumes Compose's WindowInsets, so capture the status-bar height from the
        // decor view and pad the toolbar with it (statusBarTopPx). HTML5 fullscreen
        // hides/shows the bars from Compose (see LaunchedEffect(fullscreen)).
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            isAppearanceLightStatusBars = false
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets ->
            statusBarTopPx.value =
                insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top
            navBarBottomPx.value =
                insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom
            insets
        }

        val settings = SettingsManager(this)
        val bookmarks = BookmarkManager(this)
        val history = HistoryManager(this)
        // A deep link (VIEW intent) opens its URL; otherwise the previous session is restored.
        val intentUrl = intentUrlFor(intent)
        // A media-control tap (cold start) targets the tab that is playing.
        intent?.getIntExtra(EXTRA_SELECT_TAB, -1)?.takeIf { it >= 0 }?.let {
            pendingSelectTabId.value = it
        }

        // Notification permission (Android 13+) so download notifications can show.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 42)
        }

        setContent {
            val prefs = remember { getSharedPreferences("pulse_ui", 0) }
            var theme by remember { mutableStateOf(prefs.getInt("theme", 0)) }
            val dark = theme == 2 || (theme == 0 && androidx.compose.foundation.isSystemInDarkTheme())
            val palette = if (dark) darkColorScheme(primary = Color(0xFF67E7C5)) else lightColorScheme(primary = Color(0xFF006A56))
            MaterialTheme(colorScheme = palette) {
                BrowserScreen(this, settings, bookmarks, history, intentUrl, pendingUrl, pendingSelectTabId,
                    theme, { theme = (theme + 1) % 3; prefs.edit().putInt("theme", theme).apply() })
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

    // The URL to open for a VIEW intent. http/https/content/data pass through; a
    // file:// path is rewritten to a readable content:// URI (the sandboxed
    // renderer cannot read file://), so opening a local file actually works.
    private fun intentUrlFor(intent: Intent?): String? {
        val data = intent?.data ?: return null
        if (data.scheme == "file") {
            val path = data.path ?: return data.toString()
            return contentUriFor(this, path)?.toString() ?: data.toString()
        }
        return data.toString()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun ensureRuntimePermissions() {
        val perms = mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.POST_NOTIFICATIONS,
            android.Manifest.permission.READ_CONTACTS,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += android.Manifest.permission.BLUETOOTH_SCAN
            perms += android.Manifest.permission.BLUETOOTH_CONNECT
        }
        val missing = perms.filter {
            checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1001)
    }
}

private class Tab(
    val id: Int,
    val state: CefriumViewState,
    val navigator: CefriumNavigator = CefriumNavigator(),
    // Title restored from a saved session, shown in the tab strip until the page
    // reloads and the live pageTitle takes over (avoids a "New Tab" flash on restore).
    val restoredTitle: String? = null,
    // The tab whose page opened this one as a window (window.open, a
    // target=_blank link): closing this tab, or Back with no history left,
    // returns there.
    val openerId: Int? = null,
) {
    // True while a YouTube ad is playing (hidden/silenced by the ad blocker) --
    // drives the "ad hidden" badge. Set from the page via the JS bridge.
    val adShowing = androidx.compose.runtime.mutableStateOf(false)
    // True while this tab's renderer is discarded to save memory: its browser is
    // closed, and the tab's CefriumViewState carries the serialized nav state so it
    // restores (URL + back/forward stack) when the tab is next selected.
    val discarded = androidx.compose.runtime.mutableStateOf(false)
    // Set when this tab is a window a page opened: window.close() finds it.
    var window: CefriumBrowser? = null
    // Monotonic last-activated order for LRU discarding (higher = more recently used).
    var lastUsed: Int = 0
}

private val CHROME_UA: String
    get() = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/${Cefrium.getChromiumVersion()} Mobile Safari/537.36"
// Injected into YouTube pages: the ad blocker silences the ad so the player just
// goes black -- this reports ad-playing state to the host via window.cefriumQuery
// so we can show an "ad hidden" badge. Idempotent; survives SPA navigation and
// player recreation via a poll + periodic re-attach.
// Intent extra: tab id to activate when a media-control notification is tapped, so the
// tap routes to the exact tab that is playing (not just the app's current screen).
private const val EXTRA_SELECT_TAB = "com.cefrium.sample.mediaSelectTab"

private const val YT_AD_OBSERVER_JS = """
(function(){
  if(window.__cefAdObs)return; window.__cefAdObs=1;
  var last=null;
  function send(v){try{window.cefriumQuery({request:'ad:'+(v?1:0),persistent:false,onSuccess:function(){},onFailure:function(){}});}catch(e){}}
  function check(){
    var p=document.querySelector('.html5-video-player');
    var ad=!!(p&&(p.classList.contains('ad-showing')||p.classList.contains('ad-interrupting')));
    if(ad!==last){last=ad;send(ad);}
  }
  var mo=new MutationObserver(check);
  function attach(){var p=document.querySelector('.html5-video-player');if(p){try{mo.observe(p,{attributes:true,attributeFilter:['class']});}catch(e){}}check();setTimeout(attach,3000);}
  attach(); setInterval(check,1000);
})();
"""

private const val LAST_HAVENS_OPT_JS = """
(function(){
  if(window.__lhFastCam)return; window.__lhFastCam=1;
  var origAdd=EventTarget.prototype.addEventListener;
  EventTarget.prototype.addEventListener=function(type,fn,opt){
    if(type==='touchmove'||type==='pointermove'){
      if(typeof opt==='boolean')opt={capture:opt,passive:true};
      else if(typeof opt==='object'&&opt!==null&&opt.passive===undefined)opt=Object.assign({},opt,{passive:true});
      else if(opt===undefined)opt={passive:true};
    }
    return origAdd.call(this,type,fn,opt);
  };
})();
"""

// Dialog routing for the overflow menu and engine-driven prompts.
private enum class Sheet { NONE, OVERFLOW, BOOKMARKS, HISTORY, SETTINGS, DOWNLOADS, ABOUT }

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun BrowserScreen(
    activity: Activity,
    settings: SettingsManager,
    bookmarks: BookmarkManager,
    history: HistoryManager,
    intentUrl: String?,
    pendingUrl: androidx.compose.runtime.MutableState<String?>,
    pendingSelectTabId: androidx.compose.runtime.MutableState<Int?>,
    theme: Int, changeTheme: () -> Unit,
) {
    // Initial tabs: a deep link opens its URL; else restore the saved session; else home.
    val restored = remember {
        when {
            intentUrl != null -> listOf(intentUrl)
            settings.sessionUrls.isNotEmpty() -> settings.sessionUrls.toList()
            else -> listOf(settings.homepage)
        }
    }
    val restoredTitles = remember {
        if (intentUrl == null && settings.sessionUrls.isNotEmpty()) settings.sessionTitles.toList()
        else emptyList()
    }
    val restoredNavStates = remember {
        if (intentUrl == null && settings.sessionUrls.isNotEmpty()) settings.sessionNavStates.toList()
        else emptyList()
    }
    var tabs by remember {
        mutableStateOf(restored.mapIndexed { i, u ->
            // A saved back/forward stack (base64) restores full history; else just the URL.
            val navBlob = restoredNavStates.getOrNull(i)?.takeIf { it.isNotBlank() }?.let {
                runCatching { android.util.Base64.decode(it, android.util.Base64.NO_WRAP) }.getOrNull()
            }
            Tab(i, CefriumViewState(u, navigationState = navBlob),
                restoredTitle = restoredTitles.getOrNull(i)?.takeIf { it.isNotBlank() })
        })
    }
    var nextId by remember { mutableIntStateOf(restored.size) }
    // Monotonic counter stamped onto a tab each time it becomes active -> LRU order.
    var tabUseSeq by remember { mutableIntStateOf(0) }

    // Long-press drag-to-reorder state for the tab strip.
    val tabListState = rememberLazyListState()
    var draggedTabId by remember { mutableStateOf<Int?>(null) }
    var draggedDx by remember { mutableFloatStateOf(0f) }
    val haptics = LocalHapticFeedback.current
    val dragScope = rememberCoroutineScope()
    var activeTabId by remember {
        mutableIntStateOf(
            if (intentUrl == null) settings.sessionActive.coerceIn(0, restored.size - 1) else 0
        )
    }
    var urlText by remember { mutableStateOf(restored.getOrElse(activeTabId) { restored.first() }) }
    var showFind by remember { mutableStateOf(false) }
    var findText by remember { mutableStateOf("") }
    var sheet by remember { mutableStateOf(Sheet.NONE) }

    // Downloads: app-level list across all tabs (one entry per download id, updated
    // in place) + system notifications (start/progress/complete, tap-to-open).
    val appDownloads = remember { androidx.compose.runtime.mutableStateListOf<com.cefrium.compose.DownloadEntry>() }
    val downloadNotifier = remember { DownloadNotifier(activity) }
    // For "ask where to save" downloads: id -> the SAF uri to open (the file was
    // copied there on completion; its DownloadEntry.path is a temp file).
    val safOpenUri = remember { mutableMapOf<Int, android.net.Uri>() }

    // Two-mode address bar (like real browsers): editingUrl=false shows a drag-to-scroll,
    // non-selectable label (pan to read a long URL, no selection magnifier); editingUrl=true
    // swaps in a native EditText to type. urlEditText holds the live EditText.
    var urlEditText by remember { mutableStateOf<android.widget.EditText?>(null) }
    var editingUrl by remember { mutableStateOf(false) }

    // Engine-driven prompts (Compose state).
    var permissionPrompt by remember { mutableStateOf<PermissionPrompt?>(null) }
    var devicePicker by remember { mutableStateOf<DevicePickerState?>(null) }

    val activeTab = tabs.find { it.id == activeTabId }

    // Stamp the newly-active tab as most-recently-used (drives LRU discarding) and,
    // if it was discarded, clear the flag so its CefriumView recreates + restores.
    LaunchedEffect(activeTabId) {
        activeTab?.let {
            it.lastUsed = ++tabUseSeq
            it.discarded.value = false
        }
    }

    // Keep the active tab visible in the strip. A newly opened tab is appended at the
    // end and is off-screen when the strip is full, so scroll it into view (only when
    // it is not already fully visible, so selecting a visible tab does not re-scroll).
    // Also re-run when leaving HTML5 fullscreen: the strip (a LazyRow inside the
    // `if (!fullscreen)` block) is removed while fullscreen and re-enters on restore, and
    // the active tab can land outside the visible window -- neither activeTabId nor the
    // tab count changed, so without keying on fullscreen this effect would not re-fire and
    // the focused tab would stay scrolled off-screen.
    val activeFullscreen = activeTab?.state?.isFullscreen == true
    LaunchedEffect(activeTabId, tabs.size, activeFullscreen) {
        if (activeFullscreen) return@LaunchedEffect
        val idx = tabs.indexOfFirst { it.id == activeTabId }
        if (idx >= 0) {
            // On fullscreen restore the LazyRow may not be laid out yet this frame; wait
            // one frame so layoutInfo reflects the re-composed strip before measuring.
            withFrameNanos {}
            val info = tabListState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == idx }
            val fullyVisible = item != null &&
                item.offset >= info.viewportStartOffset &&
                item.offset + item.size <= info.viewportEndOffset
            if (!fullyVisible) tabListState.animateScrollToItem(idx)
        }
    }


    // Wire every tab's download events into the shared app-level list + system
    // notifications. Download ids are process-global, so entries never collide
    // across tabs. Re-runs when the tab set changes (new/restored tabs).
    LaunchedEffect(tabs) {
        tabs.forEach { tab ->
            // "Ask where to save": pop the SAF picker; else auto-save.
            tab.state.onBeforeDownload = { entry, allow, cancel ->
                if (settings.isAskWhereToSave) {
                    val act = activity as? MainActivity
                    if (act != null) {
                        act.askWhereToSave(entry.id, entry.name, entry.mimeType, allow, cancel)
                    } else {
                        allow(null)
                    }
                } else {
                    allow(null)
                }
            }
            tab.state.onDownloadEvent = { entry ->
                val i = appDownloads.indexOfFirst { it.id == entry.id }
                if (i >= 0) appDownloads[i] = entry else appDownloads.add(0, entry)
                downloadNotifier.update(entry)
                when (entry.state) {
                    com.cefrium.compose.DownloadState.STARTED -> {
                        // Route notification Cancel/Pause/Resume back to this tab.
                        DownloadRegistry.register(entry.id) { action ->
                            when (action) {
                                0 -> tab.state.cancelDownload(entry.id)
                                1 -> tab.state.pauseDownload(entry.id)
                                2 -> tab.state.resumeDownload(entry.id)
                            }
                        }
                        // Immediate in-app cue (the notification is the primary,
                        // persistent indicator; this confirms the tap took effect).
                        android.widget.Toast.makeText(
                            activity, "Downloading ${entry.name}",
                            android.widget.Toast.LENGTH_SHORT).show()
                    }
                    com.cefrium.compose.DownloadState.COMPLETE -> {
                        DownloadRegistry.unregister(entry.id)
                        // If this was a "save as" download, copy the temp file to
                        // the chosen location and remember its uri for opening.
                        (activity as? MainActivity)?.finishSafDownload(entry.id)?.let {
                            safOpenUri[entry.id] = it
                        }
                    }
                    com.cefrium.compose.DownloadState.CANCELED -> {
                        DownloadRegistry.unregister(entry.id)
                        (activity as? MainActivity)?.finishSafDownload(entry.id)
                    }
                    else -> {}
                }
            }
        }
    }

    fun resolveUrl(input: String): String = when {
        input.contains("://") -> input
        input.contains(".") && !input.contains(" ") -> "https://$input"
        else -> settings.getSearchUrl(input)
    }

    fun navigateTo(input: String) {
        val url = resolveUrl(input.trim())
        activeTab?.navigator?.loadUrl(url)
    }

    // A PendingIntent that re-launches this activity carrying the tab id, so tapping the
    // media-control notification opens the exact tab that is playing (not just the app).
    fun mediaTapIntent(tabId: Int): android.app.PendingIntent {
        val intent = Intent(activity, activity.javaClass).apply {
            putExtra(EXTRA_SELECT_TAB, tabId)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return android.app.PendingIntent.getActivity(
            activity, tabId, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun wireHandlers(tab: Tab) {
        tab.navigator.setUserAgentOverride(CHROME_UA)
        tab.navigator.setMediaSessionEnabled(true)
        tab.navigator.setMediaSessionActivity(mediaTapIntent(tab.id))
        tab.navigator.setPullToRefreshEnabled(true)
        tab.navigator.setPermissionHandler { origin, permissions, cb ->
            activity.runOnUiThread { permissionPrompt = PermissionPrompt(origin, permissions, cb) }
        }
        tab.navigator.setDeviceChooserHandler(object : CefriumBrowser.DeviceChooserHandler {
            override fun onShowDeviceChooser(session: CefriumBrowser.DeviceChooserSession) {
                activity.runOnUiThread { devicePicker = DevicePickerState(session) }
            }
            override fun onDeviceAdded(
                session: CefriumBrowser.DeviceChooserSession,
                device: CefriumBrowser.ChooserDevice,
            ) {
                activity.runOnUiThread {
                    devicePicker?.let { if (it.session === session) it.add(device) }
                }
            }
            override fun onDeviceRemoved(
                session: CefriumBrowser.DeviceChooserSession, deviceId: String,
            ) {
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

    fun addTab(url: String = settings.homepage, incognito: Boolean = false) {
        val tab = Tab(nextId, CefriumViewState(url, incognito = incognito))
        tabs = tabs + tab
        activeTabId = nextId
        nextId++
    }

    fun closeTab(id: Int) {
        // Clear any in-flight drag state: removing an item while a chip is still
        // picked up (translationX/zIndex applied) would leave it visually detached.
        draggedTabId = null; draggedDx = 0f
        val closing = tabs.find { it.id == id }
        closing?.state?.close()
        tabs = tabs.filter { it.id != id }
        val opener = tabs.find { it.id == closing?.openerId }
        when {
            tabs.isEmpty() -> addTab()
            activeTabId == id && opener != null -> activeTabId = opener.id
            activeTabId == id -> activeTabId = tabs.last().id
        }
    }

    // A window the page opened becomes a tab next to its opener, like Chrome:
    // it has its own URL, history and fullscreen, and the page keeps
    // window.opener (sign-in and payment flows wait on it).
    fun openWindowTab(opener: Tab, popup: CefriumBrowser, url: String) {
        val tab = Tab(nextId, CefriumViewState.forWindow(popup, url, opener.state.incognito),
            openerId = opener.id)
        tab.window = popup
        val at = tabs.indexOfFirst { it.id == opener.id } + 1
        tabs = tabs.toMutableList().apply { add(at.coerceIn(0, size), tab) }
        activeTabId = nextId
        nextId++
    }

    val prefs = remember { activity.getSharedPreferences("pulse_ui", 0) }
    var bottomBar by remember { mutableStateOf(prefs.getBoolean("bottomBar", false)) }
    var compact by remember { mutableStateOf(prefs.getBoolean("compact", false)) }
    var gaming by remember { mutableStateOf(prefs.getBoolean("gaming", false)) }
    var immersive by remember(activeTabId) { mutableStateOf(false) }

    // Android system back: dismiss transient UI first, then walk the active tab's
    // page history, and only minimize the app (keep it alive -- background audio)
    // once there is nothing left to go back to. Standard browser behaviour.
    BackHandler {
        when {
            immersive -> immersive = false
            editingUrl -> editingUrl = false
            showFind -> showFind = false
            activeTab?.state?.isFullscreen == true -> activeTab.navigator.exitFullscreen()
            activeTab?.navigator?.canGoBack == true -> activeTab.navigator.navigateBack()
            // A window with nothing to go back to: close it, back to its opener.
            activeTab != null && tabs.any { it.id == activeTab.openerId } ->
                closeTab(activeTab.id)
            else -> activity.moveTaskToBack(true)
        }
    }

    // True while this app has an active media (USAGE_MEDIA) audio track -- the reliable
    // system-level "is sound playing" signal, stable across the ON_STOP/discard moment.
    fun isPlayingMediaAudio(): Boolean {
        val am = activity.getSystemService(android.content.Context.AUDIO_SERVICE)
            as android.media.AudioManager
        return am.activePlaybackConfigurations.any {
            it.audioAttributes.usage == android.media.AudioAttributes.USAGE_MEDIA
        }
    }

    // Tab discarding, LRU + keep-warm (like a desktop browser). Keeps the active tab
    // plus the `keepWarm` most-recently-used background tabs live; discards the rest
    // oldest-first -- serialize its nav state, close the browser (frees the renderer),
    // and swap in a fresh CefriumViewState that restores on reselect. Incognito and
    // already-discarded tabs are never touched. Called with a big keepWarm on plain
    // backgrounding (recent tabs stay instant) and a small one under memory pressure.
    fun discardBackgroundTabs(keepWarm: Int) {
        // Never reclaim tabs while the app is actively playing media audio. The system
        // audio state (AudioManager, USAGE_MEDIA active) is the reliable "sound is
        // playing" signal -- Chromium's per-tab media-session signal races and briefly
        // reads "not playing" on background, so it cannot be trusted to protect the
        // source tab here. We cannot attribute the audio to one tab reliably, and a
        // browser serving audio runs a media foreground service (foreground-important,
        // so the LMK won't target it anyway) -- so skip the whole pass. Normal
        // discarding resumes once playback stops.
        if (isPlayingMediaAudio()) return
        // Never discard the active tab, incognito tabs, already-discarded tabs, or a
        // tab whose media-session reports it playing (best-effort per-tab guard).
        val victims = tabs
            .filter {
                it.id != activeTabId && !it.state.incognito && !it.discarded.value &&
                    !it.navigator.isPlayingAudio()
            }
            .sortedByDescending { it.lastUsed }
            .drop(keepWarm)
            .map { it.id }
            .toSet()
        if (victims.isEmpty()) return
        tabs = tabs.map { tab ->
            if (tab.id !in victims) {
                tab
            } else {
                val blob = tab.navigator.serializeNavigationState()
                val url = tab.state.lastLoadedUrl ?: tab.state.initialUrl ?: settings.homepage
                val title = tab.state.pageTitle?.takeIf { it.isNotBlank() && it != "about:blank" }
                    ?: tab.restoredTitle
                tab.state.close()
                Tab(tab.id, CefriumViewState(url, navigationState = blob),
                    CefriumNavigator(), restoredTitle = title, openerId = tab.openerId)
                    .also { it.discarded.value = true; it.lastUsed = tab.lastUsed }
            }
        }
    }

    // Keep the URL bar synced + record history as the active tab navigates.
    LaunchedEffect(activeTabId, activeTab?.state?.lastLoadedUrl) {
        val u = activeTab?.state?.lastLoadedUrl
        val shown = u ?: activeTab?.state?.initialUrl ?: ""
        // A blank new tab shows an empty bar, ready to type; never recorded.
        urlText = if (shown == "about:blank") "" else shown
        // Incognito navigations are never written to history (private browsing).
        if (u != null && u != "about:blank" && activeTab?.state?.incognito != true)
            history.add(u, activeTab?.state?.pageTitle ?: u)
    }

    // Wire engine handlers + preferences once the active tab's browser is live
    // (navigator methods no-op until the browser is attached, so re-apply as the
    // tab loads).
    LaunchedEffect(activeTabId, activeTab?.state?.lastLoadedUrl) {
        activeTab?.let { wireHandlers(it) }
        activeTab?.navigator?.setDarkMode(settings.isDarkModeAuto)
    }

    // A VIEW intent arriving while running (onNewIntent) loads into the active tab.
    LaunchedEffect(pendingUrl.value) {
        pendingUrl.value?.let { url ->
            activeTab?.navigator?.loadUrl(url)
            pendingUrl.value = null
        }
    }

    // True while the Activity is in the foreground (RESUMED). Gates the active-tab
    // auto-reload so a renderer that died in the background is reloaded on return,
    // not while the app is still hidden. Toggled by the lifecycle observer below.
    var isResumed by remember { mutableStateOf(true) }

    // Recover the active tab after its renderer was killed (OEM low-memory kill while
    // backgrounded). The page is blank/dead; reload it so returning to the browser after
    // a recycle shows content instead of a white page. Keyed on the foreground
    // transition + tab switch (NOT on rendererTermination itself), so a page that keeps
    // killing its renderer cannot trigger a reload loop: this only fires when the user
    // returns to the foreground or selects the tab.
    LaunchedEffect(activeTabId, isResumed) {
        if (isResumed && activeTab?.state?.rendererTermination != null) {
            activeTab?.navigator?.reload()
        }
    }

    // A media-control tap carries the id of the tab that is playing -> switch to it.
    LaunchedEffect(pendingSelectTabId.value, tabs) {
        pendingSelectTabId.value?.let { id ->
            if (tabs.any { it.id == id }) {
                activeTabId = id
                pendingSelectTabId.value = null
            }
        }
    }

    // Persist the session (tab URLs + active index) on every tab/navigation change,
    // so it survives a process kill (low memory) -- not just an Activity that stayed alive.
    LaunchedEffect(tabs, activeTabId, tabs.map { it.state.lastLoadedUrl },
            tabs.map { it.state.pageTitle }) {
        // Incognito tabs are private -- never persisted, so they do not survive a
        // process kill or app restart.
        val persistable = tabs.filter { !it.state.incognito }
        val urls = persistable.map { it.state.lastLoadedUrl ?: it.state.initialUrl ?: "about:blank" }
        val titles = persistable.map {
            it.state.pageTitle?.takeIf { t -> t.isNotBlank() && t != "about:blank" }
                ?: it.restoredTitle ?: ""
        }
        // Capture each tab's full back/forward stack (base64) so restore can go back.
        // A discarded tab's browser is closed, so serializeNavigationState() is null;
        // fall back to the blob its CefriumViewState already carries, so a discarded
        // background tab keeps its full history across a process kill too.
        val navStates = persistable.map {
            (it.navigator.serializeNavigationState() ?: it.state.navigationState)?.let { b ->
                android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP)
            } ?: ""
        }
        settings.saveSession(urls, titles, navStates,
            persistable.indexOfFirst { it.id == activeTabId }.coerceAtLeast(0))
    }

    // Inject the YouTube "ad hidden" observer once each page settles; reset the
    // badge for non-YouTube pages. The observer reports ad state via cefriumQuery.
    LaunchedEffect(activeTabId, activeTab?.state?.lastLoadedUrl, activeTab?.state?.isLoading) {
        val tab = activeTab ?: return@LaunchedEffect
        val url = tab.state.lastLoadedUrl ?: ""
        if (!url.contains("youtube.com")) {
            tab.adShowing.value = false
        } else if (tab.state.isLoading == false) {
            tab.navigator.evaluateJavaScript(YT_AD_OBSERVER_JS)
        }
        if (url.contains("lasthavens.com") && tab.state.isLoading == false) {
            tab.navigator.evaluateJavaScript(LAST_HAVENS_OPT_JS)
        }
    }

    // Entering edit mode: focus the EditText and raise the keyboard (post so the view
    // is attached first).
    LaunchedEffect(editingUrl) {
        if (editingUrl) urlEditText?.post {
            urlEditText?.requestFocus()
            (activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as? android.view.inputmethod.InputMethodManager)
                ?.showSoftInput(urlEditText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }

    // Free background-tab renderers when the app is backgrounded (ON_STOP) or the OS
    // signals memory pressure (onTrimMemory) -- the low-RAM survival lever.
    DisposableEffect(activity) {
        val act = activity as? MainActivity
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                // Backgrounded (no explicit pressure): keep the active + a couple of
                // recent tabs warm for an instant return; discard older ones, oldest-first.
                Lifecycle.Event.ON_STOP -> discardBackgroundTabs(keepWarm = 2)
                Lifecycle.Event.ON_RESUME -> isResumed = true
                Lifecycle.Event.ON_PAUSE -> isResumed = false
                else -> {}
            }
        }
        act?.lifecycle?.addObserver(obs)
        // Real memory pressure: shed harder, oldest-first. Moderate/complete discard
        // all background tabs; background/critical keep one warm. Lighter levels are
        // ignored -- a transient dip should not evict the user's tabs.
        act?.onTrimMemoryCb = { level ->
            when {
                level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE ->
                    discardBackgroundTabs(keepWarm = 0)
                level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
                    level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ->
                    discardBackgroundTabs(keepWarm = 1)
            }
        }
        onDispose {
            act?.lifecycle?.removeObserver(obs)
            act?.onTrimMemoryCb = null
        }
    }

    DisposableEffect(Unit) { onDispose { tabs.forEach { it.state.close() } } }

    // Only sample display-mode notifications. No frame callbacks or game-page scripts.
    val recentRates = remember(activeTabId) { ArrayDeque<Int>() }
    val observeDisplay = isResumed && sheet == Sheet.NONE
    DisposableEffect(activity, activeTabId, observeDisplay) {
        val manager = activity.getSystemService(android.hardware.display.DisplayManager::class.java)
        val id = activity.windowManager.defaultDisplay.displayId
        fun record() {
            val rate = manager.getDisplay(id)?.refreshRate ?: return
            val hz = Math.round(rate)
            if (hz > 0 && recentRates.lastOrNull() != hz) {
                if (recentRates.size >= 6) recentRates.removeFirst()
                recentRates.addLast(hz)
            }
        }
        val listener = object : android.hardware.display.DisplayManager.DisplayListener {
            override fun onDisplayChanged(displayId: Int) { if (displayId == id) record() }
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
        }
        if (observeDisplay) {
            record()
            manager.registerDisplayListener(listener, android.os.Handler(android.os.Looper.getMainLooper()))
        }
        onDispose { if (observeDisplay) manager.unregisterDisplayListener(listener) }
    }

    val pageUri = android.net.Uri.parse(activeTab?.state?.lastLoadedUrl ?: activeTab?.state?.initialUrl ?: "")
    val isLastHavens = pageUri.host in setOf("lasthavens.com", "alt.lasthavens.com", "www.lasthavens.com") ||
        (pageUri.host?.contains("lasthavens.com") == true)
    val gamePage = isLastHavens || (pageUri.path?.trimEnd('/') == "/play")
    var surfaceVote by remember { mutableStateOf<GameSurfaceRate?>(null) }
    val shouldBoost120Hz = (gaming || isLastHavens) && isResumed
    DisposableEffect(activity, gaming, isResumed, gamePage, activeTabId) {
        val window = activity.window
        val display = activity.windowManager.defaultDisplay
        val oldModeId = window.attributes.preferredDisplayModeId
        val oldRate = if (Build.VERSION.SDK_INT >= 30) window.attributes.preferredRefreshRate else 0f
        val oldViewRate = if (Build.VERSION.SDK_INT >= 35) window.decorView.requestedFrameRate else 0f

        fun applyRefreshRates(params: WindowManager.LayoutParams, rate: Float) {
            if (Build.VERSION.SDK_INT >= 30) {
                params.preferredRefreshRate = rate
                try {
                    params.javaClass.getField("preferredMinDisplayRefreshRate").setFloat(params, rate)
                } catch (_: Throwable) {}
                try {
                    params.javaClass.getField("preferredMaxDisplayRefreshRate").setFloat(params, rate)
                } catch (_: Throwable) {}
            }
        }

        fun setFrameCategory(view: View, category: Int) {
            if (Build.VERSION.SDK_INT >= 35) {
                try {
                    val m = View::class.java.getMethod("setFrameRateCategory", Int::class.javaPrimitiveType)
                    m.invoke(view, category)
                } catch (_: Throwable) {}
            }
        }

        if (shouldBoost120Hz) {
            val current = display.mode
            val best = display.supportedModes.filter {
                val matchesResolution = (it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight) ||
                    (it.physicalWidth == current.physicalHeight && it.physicalHeight == current.physicalWidth)
                matchesResolution && it.refreshRate <= 120.5f
            }.maxByOrNull { it.refreshRate }

            if (best != null) {
                if (Build.VERSION.SDK_INT >= 30) {
                    surfaceVote = GameSurfaceRate(window.decorView, best.refreshRate).also { it.start() }
                    window.attributes = window.attributes.apply {
                        preferredDisplayModeId = best.modeId
                        applyRefreshRates(this, best.refreshRate)
                    }
                } else {
                    window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
                }

                if (Build.VERSION.SDK_INT >= 35) {
                    setFrameCategory(window.decorView, 2) // FRAME_RATE_CATEGORY_HIGH
                    window.decorView.requestedFrameRate = best.refreshRate
                }

                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (Build.VERSION.SDK_INT >= 24) {
                    try { window.setSustainedPerformanceMode(true) } catch (_: Throwable) {}
                }
                try {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY)
                } catch (_: Throwable) {}
            }
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= 30) surfaceVote?.stop()
            surfaceVote = null
            if (shouldBoost120Hz) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (Build.VERSION.SDK_INT >= 24) {
                    try { window.setSustainedPerformanceMode(false) } catch (_: Throwable) {}
                }
                if (Build.VERSION.SDK_INT >= 35) {
                    setFrameCategory(window.decorView, 0) // FRAME_RATE_CATEGORY_DEFAULT
                    window.decorView.requestedFrameRate = oldViewRate
                }
                if (Build.VERSION.SDK_INT >= 30) {
                    window.attributes = window.attributes.apply {
                        preferredDisplayModeId = oldModeId
                        applyRefreshRates(this, oldRate)
                    }
                } else {
                    window.attributes = window.attributes.apply { preferredDisplayModeId = oldModeId }
                }
            }
        }
    }
    val fullscreen = immersive || activeTab?.state?.isFullscreen == true

    // Hide the system bars only during HTML5 fullscreen (video); restore them otherwise.
    LaunchedEffect(fullscreen) {
        androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            .apply {
                if (fullscreen) {
                    systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat
                        .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                } else {
                    show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                }
            }
    }

    // Pad the toolbar below the status bar. Compose's WindowInsets are consumed by the
    // CEF content layer, so the height comes from the decor-view listener in onCreate.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val statusBarTopDp = with(density) {
        ((activity as? MainActivity)?.statusBarTopPx?.value ?: 0).toDp()
    }
    // Keep page content (e.g. YouTube's bottom controls) above the Android nav bar.
    val navBarBottomDp = with(density) {
        ((activity as? MainActivity)?.navBarBottomPx?.value ?: 0).toDp()
    }
    // Keep the game surface in composition when the controls change sides.
    val controlsBelowPage = remember {
        object : Arrangement.Vertical {
            override fun androidx.compose.ui.unit.Density.arrange(
                totalSize: Int, sizes: IntArray, outPositions: IntArray
            ) {
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
    Column(
        verticalArrangement = if (bottomBar) controlsBelowPage else Arrangement.Top,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .then(
                if (fullscreen) Modifier
                else Modifier.padding(top = statusBarTopDp, bottom = navBarBottomDp)
            ),
    ) {
        if (!fullscreen) {
            // Tab bar
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LazyRow(state = tabListState, modifier = Modifier.weight(1f)) {
                    itemsIndexed(tabs, key = { _, tab -> tab.id }) { _, tab ->
                        val isActive = tab.id == activeTabId
                        val isDragged = tab.id == draggedTabId
                        Row(
                            modifier = Modifier
                                .zIndex(if (isDragged) 1f else 0f)
                                .then(
                                    // Animate item placement ONLY while a drag is active (to
                                    // shuffle non-dragged chips as the picked-up one moves).
                                    // Outside a drag, place instantly: an always-on
                                    // animateItem replays a spurious slide whenever the
                                    // LazyRow re-enters composition (exiting HTML5 fullscreen) or
                                    // re-measures (orientation change) or loses an item (closing a
                                    // tab), which shows up as a bad redraw / a gap that has not
                                    // settled yet.
                                    if (isDragged) Modifier.graphicsLayer { translationX = draggedDx }
                                    else if (draggedTabId != null) Modifier.animateItem()
                                    else Modifier
                                )
                                .pointerInput(tab.id) {
                                    // Long-press to pick a tab up, then drag to reorder. A plain
                                    // tap still selects (clickable below); a plain horizontal drag
                                    // still scrolls the strip -- long-press disambiguates.
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggedTabId = tab.id
                                            draggedDx = 0f
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragEnd = {
                                            // Settle: slide the picked-up chip from its residual
                                            // finger offset into its slot, then drop the overlay.
                                            val settling = draggedTabId
                                            dragScope.launch {
                                                animate(draggedDx, 0f, animationSpec = tween(160)) { v, _ ->
                                                    if (draggedTabId == settling) draggedDx = v
                                                }
                                                if (draggedTabId == settling) {
                                                    draggedTabId = null; draggedDx = 0f
                                                }
                                            }
                                        },
                                        onDragCancel = { draggedTabId = null; draggedDx = 0f },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            draggedDx += amount.x
                                            val info = tabListState.layoutInfo
                                            val cur = info.visibleItemsInfo
                                                .firstOrNull { it.key == draggedTabId }
                                                ?: return@detectDragGesturesAfterLongPress
                                            val middle = cur.offset + draggedDx + cur.size / 2f
                                            val target = info.visibleItemsInfo.firstOrNull {
                                                it.key != draggedTabId &&
                                                    middle >= it.offset && middle <= it.offset + it.size
                                            }
                                            if (target != null) {
                                                val from = tabs.indexOfFirst { it.id == draggedTabId }
                                                val to = tabs.indexOfFirst { it.id == target.key }
                                                if (from in tabs.indices && to in tabs.indices && from != to) {
                                                    tabs = tabs.toMutableList()
                                                        .apply { add(to, removeAt(from)) }
                                                    // keep the picked-up chip under the finger after
                                                    // the shuffle moves its home slot.
                                                    draggedDx += (cur.offset - target.offset)
                                                }
                                            }
                                        },
                                    )
                                }
                                .padding(2.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (isActive) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
                                .clickable { activeTabId = tab.id }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                (tab.state.pageTitle?.takeIf { it.isNotBlank() && it != "about:blank" }
                                    ?: tab.restoredTitle?.takeIf { it.isNotBlank() }
                                    ?: "New Tab").take(15),
                                color = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray,
                                fontSize = 12.sp, maxLines = 1,
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Filled.Close, contentDescription = "Close tab",
                                tint = Color.Gray, modifier = Modifier.size(16.dp)
                                    .clickable { closeTab(tab.id) })
                        }
                    }
                }
                NavIcon(Icons.Filled.Add, "New tab", tint = MaterialTheme.colorScheme.primary) {
                    addTab("about:blank"); urlText = ""; editingUrl = true
                }
            }

            // URL bar + nav + overflow. Incognito tabs get a distinct purple toolbar
            // + a mask icon so private browsing is unmistakable.
            val pageInk = MaterialTheme.colorScheme.onSurface
            val fieldFill = MaterialTheme.colorScheme.surfaceContainerHighest
            val incognitoActive = activeTab?.state?.incognito == true
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(if (incognitoActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavIcon(Icons.Filled.ArrowBack, "Back") { activeTab?.navigator?.navigateBack() }
                if (!compact) NavIcon(Icons.Filled.ArrowForward, "Forward") { activeTab?.navigator?.navigateForward() }
                if (!compact) NavIcon(Icons.Filled.Refresh, "Reload") { activeTab?.navigator?.reload() }
                if (incognitoActive) {
                    Icon(
                        Icons.Filled.VisibilityOff, "Incognito",
                        tint = Color(0xFFB39DDB),
                        modifier = Modifier.padding(horizontal = 4.dp).size(20.dp),
                    )
                }
                if (editingUrl) {
                    // Edit mode: a native EditText to type a URL. (Android's selection
                    // magnifier can appear here, but in this mode you type rather than
                    // drag-to-read, and it leaves on GO / focus loss.)
                    AndroidView(
                        modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                        factory = { ctx ->
                            android.widget.EditText(ctx).apply {
                                // MATCH_PARENT width so the field is measured EXACTLY (it fills
                                // the weighted AndroidView). A wrap-content EditText re-runs
                                // TextView.checkForRelayout() on EVERY keystroke, requesting a
                                // toolbar re-layout that backs up under fast typing (caret lags
                                // behind). EXACTLY width means a text change only invalidates.
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
                                setSingleLine(true)
                                setTextColor(pageInk.toArgb())
                                setHintTextColor(pageInk.copy(alpha = .6f).toArgb())
                                hint = "Cerca o inserisci un indirizzo"
                                textSize = 13f
                                setPadding(36, 22, 36, 22)
                                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                                    android.text.InputType.TYPE_TEXT_VARIATION_URI or
                                    android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                                imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_GO
                                background = android.graphics.drawable.GradientDrawable().apply {
                                    cornerRadius = 16f
                                    setColor(fieldFill.toArgb())
                                }
                                setText(urlText)
                                // No per-keystroke urlText sync. Writing the Compose state on
                                // every character recomposed the whole BrowserScreen (the CEF
                                // SurfaceView included); that lag + re-layout desynced the IME
                                // cursor -- the caret fell behind the typed text and the bar
                                // stopped scrolling. The native EditText owns its text while
                                // editing; urlText is committed once, on GO.
                                val imm = ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                                    as? android.view.inputmethod.InputMethodManager
                                setOnEditorActionListener { _, actionId, _ ->
                                    if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO) {
                                        val entered = text.toString()
                                        urlText = entered
                                        navigateTo(entered)
                                        imm?.hideSoftInputFromWindow(windowToken, 0)
                                        editingUrl = false
                                        true
                                    } else false
                                }
                                // Select all when focused; leave edit mode when focus is lost.
                                setOnFocusChangeListener { _, hasFocus ->
                                    if (hasFocus) selectAll() else editingUrl = false
                                }
                                urlEditText = this
                            }
                        },
                        // No update-driven setText: during editing the native EditText is
                        // the sole owner of its text, cursor and horizontal scroll. Pushing
                        // urlText back on each recomposition (isFocused is unreliable here --
                        // the CEF/WindowAndroid focus model reports the field unfocused while
                        // typing) called setText() with a momentarily-stale value every
                        // keystroke, resetting the cursor behind the text and stopping the
                        // scroll. The factory seeds the initial value; the field owns it after.
                    )
                } else {
                    // Read mode: the URL as a drag-to-scroll, non-selectable label -- pan to
                    // read a long URL with NO selection magnifier (no text selection). Tap to edit.
                    Box(
                        modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                            .clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable { editingUrl = true }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = urlText.ifEmpty { "Cerca o inserisci un indirizzo" },
                            color = if (urlText.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp, maxLines = 1, softWrap = false,
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        )
                    }
                }
                var bookmarkTick by remember { mutableIntStateOf(0) }
                val bookmarked = bookmarkTick.let {
                    activeTab?.state?.lastLoadedUrl?.let { u -> bookmarks.isBookmarked(u) } == true
                }
                NavIcon(
                    if (bookmarked) Icons.Filled.Star else Icons.Filled.StarBorder,
                    "Bookmark",
                    tint = if (bookmarked) Color(0xFFFBBF24) else MaterialTheme.colorScheme.onSurface,
                ) {
                    val u = activeTab?.state?.lastLoadedUrl ?: return@NavIcon
                    if (bookmarks.isBookmarked(u)) bookmarks.remove(u)
                    else bookmarks.add(u, activeTab?.state?.pageTitle ?: u)
                    bookmarkTick++
                }
                NavIcon(if (showFind) Icons.Filled.SearchOff else Icons.Filled.Search, "Find") {
                    showFind = !showFind
                    if (!showFind) activeTab?.navigator?.stopFinding()
                }
                NavIcon(Icons.Filled.MoreVert, "Menu") { sheet = Sheet.OVERFLOW }
            }

            if (showFind) FindBar(activeTab, findText, { findText = it })

            if (activeTab?.state?.isLoading == true) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp), color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            activeTab?.let { tab ->
                key(tab.id) {
                    CefriumView(
                        state = tab.state,
                        // window.open, target=_blank: a new tab that keeps
                        // window.opener; window.close() closes that tab.
                        onCreateWindow = { p, url -> openWindowTab(tab, p, url); true },
                        onCloseWindow = { p ->
                            tabs.find { it.window === p }?.let { closeTab(it.id) }
                        },
                        modifier = Modifier.fillMaxSize(),
                        navigator = tab.navigator,
                        // Keep audio/video playing when backgrounded (like Brave); the
                        // SDK's media session (foreground while playing) keeps it alive.
                        backgroundMediaPolicy = BackgroundMediaPolicy.NONE,
                        // JS bridge: the injected YouTube observer reports ad state.
                        // Only YouTube, where the ad observer is injected. Any
                        // other site could otherwise flip the ad badge.
                        bridge = CefriumBridge(
                            CefriumBrowser.QueryOriginPolicy { url ->
                                val u = android.net.Uri.parse(url)
                                val host = u.host ?: ""
                                u.scheme == "https" &&
                                    (host == "youtube.com" || host.endsWith(".youtube.com"))
                            },
                        ) { request, _, callback ->
                            when (request) {
                                "ad:1" -> { tab.adShowing.value = true; callback.success(""); true }
                                "ad:0" -> { tab.adShowing.value = false; callback.success(""); true }
                                else -> false
                            }
                        },
                    )
                }
                // Small badge while the ad blocker silences a (black) YouTube ad.
                if (tab.adShowing.value) {
                    Row(
                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                            .background(
                                Color(0xE60A0E1A),
                                androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Anuncio oculto", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    // ---- Overflow + secondary screens ----
    when (sheet) {
        Sheet.OVERFLOW -> OverflowMenu(
            surfaceLabel = if ((gamePage || gaming) && Build.VERSION.SDK_INT >= 30)
                if (surfaceVote?.requested == true) "Frequenza 120 Hz bloccata sulla superficie di gioco" else "Frequenza 120 Hz bloccata sulla finestra"
                else null,
            recentHz = recentRates.joinToString(" → "),
            onDismiss = { sheet = Sheet.NONE },
            onBookmarks = { sheet = Sheet.BOOKMARKS },
            onHistory = { sheet = Sheet.HISTORY },
            onSettings = { sheet = Sheet.SETTINGS },
            onDownloads = { sheet = Sheet.DOWNLOADS },
            onShare = {
                sheet = Sheet.NONE
                shareUrl(activity, activeTab?.state?.lastLoadedUrl, activeTab?.state?.pageTitle)
            },
            onPrint = {
                sheet = Sheet.NONE
                val out = java.io.File(activity.getExternalFilesDir(null), "page.pdf").absolutePath
                activeTab?.navigator?.printToPdf(out) { _, _ -> }
            },
            onClearCache = { sheet = Sheet.NONE; activeTab?.navigator?.clearCache() },
            onNewIncognitoTab = {
                sheet = Sheet.NONE
                addTab("about:blank", incognito = true); urlText = ""; editingUrl = true
            },
            bottomBar = bottomBar,
            onPosition = { bottomBar = !bottomBar; prefs.edit().putBoolean("bottomBar", bottomBar).apply() },
            compact = compact, gaming = gaming, theme = theme, changeTheme = changeTheme,
            onCompact = { compact = !compact; prefs.edit().putBoolean("compact", compact).apply() },
            onGaming = { gaming = !gaming; prefs.edit().putBoolean("gaming", gaming).apply() },
            onImmersive = { sheet = Sheet.NONE; immersive = true },
            onGame = { query ->
                sheet = Sheet.NONE
                val url = "https://lasthavens.com/play$query"
                val privateMode = activeTab?.state?.incognito == true
                val existing = tabs.lastOrNull {
                    it.state.incognito == privateMode && (it.state.lastLoadedUrl ?: it.state.initialUrl) == url
                }
                if (existing != null) activeTabId = existing.id else addTab(url, privateMode)
            },
            onAbout = { sheet = Sheet.ABOUT },
        )
        Sheet.BOOKMARKS -> ListSheet(
            title = "Preferiti",
            entries = bookmarks.urls.map { it to (bookmarks.getTitle(it) ?: it) },
            onOpen = { sheet = Sheet.NONE; activeTab?.navigator?.loadUrl(it) },
            onDelete = { bookmarks.remove(it) },
            onDismiss = { sheet = Sheet.NONE },
        )
        Sheet.HISTORY -> ListSheet(
            title = "Cronologia",
            entries = history.getRecent(100).map { it.url to it.title },
            onOpen = { sheet = Sheet.NONE; activeTab?.navigator?.loadUrl(it) },
            onDelete = null,
            onDismiss = { sheet = Sheet.NONE },
            onClearAll = { history.clear() },
        )
        Sheet.SETTINGS -> SettingsSheet(settings) { sheet = Sheet.NONE }
        Sheet.DOWNLOADS -> {
            ListSheet(
                title = "Download",
                entries = appDownloads.map { it.path to downloadLabel(it) },
                onOpen = { path ->
                    val entry = appDownloads.firstOrNull { it.path == path }
                    val mime = entry?.mimeType ?: ""
                    // "Save as" downloads: the real file is at the user-chosen SAF
                    // uri (path is a temp file), so open that content:// uri.
                    val saf = entry?.let { safOpenUri[it.id] }
                    when {
                        saf != null && isBrowserViewable(mime, entry.name) ->
                            addTab(saf.toString())
                        saf != null ->
                            runCatching {
                                activity.startActivity(
                                    Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(saf, mime.ifBlank { "*/*" })
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    })
                            }
                        else -> {
                            val uri = contentUriFor(activity, path)
                            if (uri != null && isBrowserViewable(mime, entry?.name ?: path)) {
                                addTab(uri.toString())
                            } else {
                                openDownloadedFile(activity, path, mime)
                            }
                        }
                    }
                    sheet = Sheet.NONE
                },
                onDelete = null,
                onDismiss = { sheet = Sheet.NONE },
                onClearAll = if (appDownloads.any {
                        it.state == com.cefrium.compose.DownloadState.COMPLETE ||
                            it.state == com.cefrium.compose.DownloadState.CANCELED
                    }) {
                    {
                        appDownloads.removeAll {
                            it.state == com.cefrium.compose.DownloadState.COMPLETE ||
                                it.state == com.cefrium.compose.DownloadState.CANCELED
                        }
                    }
                } else null,
            )
        }
        Sheet.ABOUT -> AboutSheet { sheet = Sheet.NONE }
        Sheet.NONE -> {}
    }

    // Context menu (engine-driven).
    activeTab?.state?.contextMenu?.let { menu ->
        ContextMenuDialog(menu, activeTab.navigator,
            activeIncognito = activeTab.state.incognito,
            onOpenInNewTab = { url, incognito -> addTab(url, incognito = incognito) }) {
            activeTab.state.contextMenu = null
        }
    }

    // Permission prompt (engine-driven).
    permissionPrompt?.let { p ->
        AlertDialog(
            onDismissRequest = { p.callback.respond(false); permissionPrompt = null },
            confirmButton = {
                TextButton(onClick = { p.callback.respond(true); permissionPrompt = null }) { Text("Allow") }
            },
            dismissButton = {
                TextButton(onClick = { p.callback.respond(false); permissionPrompt = null }) { Text("Deny") }
            },
            title = { Text("Permission request") },
            text = { Text("${p.origin} requests permission (${p.permissions}).") },
        )
    }

    // Device chooser picker (Web Bluetooth/USB/Serial).
    devicePicker?.let { picker ->
        AlertDialog(
            onDismissRequest = { picker.session.cancel(); devicePicker = null },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { picker.session.cancel(); devicePicker = null }) { Text("Cancel") }
            },
            title = { Text("Select a ${picker.typeName()} device") },
            text = {
                LazyColumn {
                    items(picker.devices) { d ->
                        Text(
                            if (d.name.isNullOrEmpty()) d.id else "${d.name}\n${d.id}",
                            modifier = Modifier.fillMaxWidth()
                                .clickable { picker.session.select(d.id); devicePicker = null }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
        )
    }
}

private class PermissionPrompt(
    val origin: String, val permissions: Int, val callback: CefriumBrowser.PermissionCallback,
)

private class DevicePickerState(val session: CefriumBrowser.DeviceChooserSession) {
    val devices = mutableStateListOf<CefriumBrowser.ChooserDevice>()
    fun add(d: CefriumBrowser.ChooserDevice) {
        val i = devices.indexOfFirst { it.id == d.id }
        if (i >= 0) devices[i] = d else devices.add(d)
    }
    fun remove(id: String) { devices.removeAll { it.id == id } }
    fun typeName(): String = when (session.type) {
        CefriumBrowser.DEVICE_BLUETOOTH -> "Bluetooth"
        CefriumBrowser.DEVICE_USB -> "USB"
        CefriumBrowser.DEVICE_SERIAL -> "Serial"
        else -> "device"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FindBar(activeTab: Tab?, findText: String, onFindText: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = findText,
            onValueChange = {
                onFindText(it)
                activeTab?.navigator?.find(it, forward = true, findNext = false)
            },
            modifier = Modifier.weight(1f), singleLine = true,
            placeholder = { Text("Find in page", fontSize = 13.sp) },
            colors = urlFieldColors(),
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                activeTab?.navigator?.find(findText, forward = true, findNext = true)
            }),
        )
        val total = activeTab?.state?.findMatchCount ?: 0
        val active = activeTab?.state?.findActiveMatch ?: 0
        Text(if (total > 0) " $active/$total " else " 0/0 ", color = Color.Gray, fontSize = 12.sp)
        NavIcon(Icons.Filled.KeyboardArrowUp, "Previous") {
            activeTab?.navigator?.find(findText, forward = false, findNext = true)
        }
        NavIcon(Icons.Filled.KeyboardArrowDown, "Next") {
            activeTab?.navigator?.find(findText, forward = true, findNext = true)
        }
    }
}

@Composable
private fun OverflowMenu(
    recentHz: String, surfaceLabel: String?,
    onDismiss: () -> Unit, onBookmarks: () -> Unit, onHistory: () -> Unit,
    onSettings: () -> Unit, onDownloads: () -> Unit, onShare: () -> Unit,
    onPrint: () -> Unit, onClearCache: () -> Unit, onNewIncognitoTab: () -> Unit,
    bottomBar: Boolean, onPosition: () -> Unit, theme: Int, changeTheme: () -> Unit, compact: Boolean, gaming: Boolean, onCompact: () -> Unit, onGaming: () -> Unit,
    onImmersive: () -> Unit, onGame: (String) -> Unit, onAbout: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
        title = { Text("PulseBrowser", style = MaterialTheme.typography.headlineMedium) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                MenuSection("GIOCO")
                MenuRow("Avvia Last Havens") { onGame("") }
                MenuRow("Last Havens · solo FPS") { onGame("?stats=1") }
                MenuRow("Last Havens · diagnostica") { onGame("?stats=1&log=1") }
                MenuRow("Schermo intero", onImmersive)
                MenuRow("Frequenza display · " + if (gaming) "fino a 120 Hz" else "sistema", onGaming)
                if (surfaceLabel != null) Text(surfaceLabel, style = MaterialTheme.typography.bodySmall)
                if (recentHz.isNotEmpty()) Text("Display rilevato · $recentHz Hz", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
                MenuSection("ASPETTO")
                MenuRow("Tema · " + listOf("sistema", "chiaro", "scuro")[theme], changeTheme)
                MenuRow("Barra · " + if (bottomBar) "in basso" else "in alto", onPosition)
                MenuRow("Barra compatta · " + if (compact) "attiva" else "disattivata", onCompact)
                MenuSection("NAVIGAZIONE")
                MenuRow("Scheda privata", onNewIncognitoTab)
                MenuRow("Preferiti", onBookmarks)
                MenuRow("Cronologia", onHistory)
                MenuRow("Download", onDownloads)
                MenuRow("Condividi", onShare)
                MenuRow("Salva in PDF", onPrint)
                MenuRow("Svuota cache", onClearCache)
                MenuRow("Impostazioni", onSettings)
                MenuRow("Informazioni", onAbout)
            }
        },
    )
}

@Composable
private fun MenuSection(label: String) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit) {
    Text(label, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp))
}

@Composable
private fun AboutSheet(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PulseBrowser", style = MaterialTheme.typography.headlineMedium) },
        text = { Text("Versione ${BuildConfig.VERSION_NAME} · Android ARM64") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
    )
}

private fun downloadLabel(e: com.cefrium.compose.DownloadEntry): String = when (e.state) {
    com.cefrium.compose.DownloadState.COMPLETE -> "${e.name}  -  done"
    com.cefrium.compose.DownloadState.CANCELED -> "${e.name}  -  canceled"
    com.cefrium.compose.DownloadState.PAUSED -> "${e.name}  -  paused ${e.percent}%"
    else -> "${e.name}  -  ${e.percent}%"
}

@Composable
private fun ListSheet(
    title: String,
    entries: List<Pair<String, String>>,
    onOpen: (String) -> Unit,
    onDelete: ((String) -> Unit)?,
    onDismiss: () -> Unit,
    onClearAll: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
        dismissButton = if (onClearAll != null) {
            { TextButton(onClick = { onClearAll(); onDismiss() }) { Text("Clear all") } }
        } else null,
        title = { Text(title) },
        text = {
            if (entries.isEmpty()) Text("Nothing here yet.")
            else LazyColumn {
                items(entries) { (url, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, maxLines = 1, modifier = Modifier.weight(1f)
                            .clickable { onOpen(url) }.padding(vertical = 12.dp))
                        if (onDelete != null) {
                            Text(" x ", color = Color.Gray,
                                modifier = Modifier.clickable { onDelete(url) })
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun SettingsSheet(settings: SettingsManager, onDismiss: () -> Unit) {
    var adblock by remember { mutableStateOf(settings.isAdBlockEnabled) }
    var darkAuto by remember { mutableStateOf(settings.isDarkModeAuto) }
    var askWhereToSave by remember { mutableStateOf(settings.isAskWhereToSave) }
    var homepage by remember { mutableStateOf(settings.homepage) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                settings.isAdBlockEnabled = adblock
                settings.isDarkModeAuto = darkAuto
                settings.isAskWhereToSave = askWhereToSave
                settings.homepage = homepage
                onDismiss()
            }) { Text("Save") }
        },
        title = { Text("Impostazioni") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Blocco annunci", modifier = Modifier.weight(1f))
                    Switch(checked = adblock, onCheckedChange = { adblock = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tema scuro delle pagine", modifier = Modifier.weight(1f))
                    Switch(checked = darkAuto, onCheckedChange = { darkAuto = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ask where to save each file")
                        Text(
                            "Off: downloads go to Downloads automatically. On: " +
                                "choose a location for each download.",
                            fontSize = 11.sp, color = Color.Gray,
                        )
                    }
                    Switch(checked = askWhereToSave, onCheckedChange = { askWhereToSave = it })
                }
                OutlinedTextField(
                    value = homepage, onValueChange = { homepage = it },
                    label = { Text("Homepage") }, singleLine = true,
                )
            }
        },
    )
}

// SDK context-menu command id for "Open in new tab" (added in the SDK's
// OnBeforeContextMenu). The SDK handles it by loading the link in the CURRENT
// browser -- but tabs are the app's concern, so the app opens a new tab instead.
private const val MENU_ID_OPEN_IN_NEW_TAB = 1

@Composable
private fun ContextMenuDialog(
    menu: com.cefrium.compose.ContextMenuInfo,
    navigator: CefriumNavigator,
    activeIncognito: Boolean,
    onOpenInNewTab: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    // menuItems format: "id|label" per line (matches the View flagship).
    val items = menu.menuItems.split("\n").mapNotNull { line ->
        val parts = line.split("|", limit = 2)
        parts[0].toIntOrNull()?.let { it to (parts.getOrNull(1) ?: "") }
    }
    AlertDialog(
        onDismissRequest = { navigator.contextMenuCommand(-1); onDismiss() },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { navigator.contextMenuCommand(-1); onDismiss() }) { Text("Cancel") }
        },
        title = { Text(menu.url.take(50)) },
        text = {
            Column {
                items.forEach { (id, label) ->
                    // From an incognito tab, "open in new tab" must STAY incognito --
                    // silently opening a normal tab would leak the navigation out of
                    // incognito. Relabel it so the mode is unmistakable.
                    val display = if (id == MENU_ID_OPEN_IN_NEW_TAB && activeIncognito)
                        "Open in incognito tab" else label
                    Text(display, modifier = Modifier.fillMaxWidth()
                        .clickable {
                            if (id == MENU_ID_OPEN_IN_NEW_TAB && menu.url.isNotEmpty()) {
                                // Cancel the SDK command (it would load in the current
                                // tab) and open the link in a new app tab instead,
                                // matching the current tab's incognito state.
                                navigator.contextMenuCommand(-1)
                                onOpenInNewTab(menu.url, activeIncognito)
                            } else {
                                navigator.contextMenuCommand(id)
                            }
                            onDismiss()
                        }
                        .padding(vertical = 12.dp))
                }
                // From a normal tab, also offer opening the link in incognito (Chrome
                // parity). In incognito the row above already opens incognito.
                if (!activeIncognito && menu.url.isNotEmpty() &&
                    items.any { it.first == MENU_ID_OPEN_IN_NEW_TAB }) {
                    Text("Open in incognito tab", modifier = Modifier.fillMaxWidth()
                        .clickable {
                            navigator.contextMenuCommand(-1)
                            onOpenInNewTab(menu.url, true)
                            onDismiss()
                        }
                        .padding(vertical = 12.dp))
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun urlFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
)

@Composable
private fun NavIcon(icon: ImageVector, desc: String, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(icon, contentDescription = desc, tint = tint, modifier = Modifier.size(22.dp))
    }
}

private fun shareUrl(activity: Activity, url: String?, title: String?) {
    if (url.isNullOrEmpty()) return
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, url)
    if (!title.isNullOrEmpty()) send.putExtra(Intent.EXTRA_SUBJECT, title)
    activity.startActivity(Intent.createChooser(send, title))
}
