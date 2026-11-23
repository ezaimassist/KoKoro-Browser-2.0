package com.cefrium.sample.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cefrium.sample.theme.SamuraiThemeId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.kokoroDataStore by preferencesDataStore(name = "kokoro_browser_prefs")

enum class UserAgentPreset(val id: String, val label: String) {
    CHROME_MOBILE("chrome_mobile", "Chrome Android"),
    CHROME_DESKTOP("chrome_desktop", "Desktop Linux/Chrome"),
    SAFARI_MOBILE("safari_mobile", "Safari Mobile"),
    CUSTOM("custom", "Personalizzato");

    companion object {
        fun fromId(raw: String?): UserAgentPreset =
            entries.firstOrNull { it.id == raw } ?: CHROME_MOBILE
    }
}

enum class SearchEngine(val id: String, val displayName: String, val queryTemplate: String) {
    DUCKDUCKGO("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s"),
    GOOGLE("google", "Google", "https://www.google.com/search?q=%s"),
    BRAVE("brave", "Brave Search", "https://search.brave.com/search?q=%s"),
    STARTPAGE("startpage", "Startpage", "https://www.startpage.com/sp/search?query=%s"),
    BING("bing", "Bing", "https://www.bing.com/search?q=%s");

    fun buildUrl(query: String): String = queryTemplate.replace("%s", Uri.encode(query))

    companion object {
        fun fromId(raw: String?): SearchEngine =
            entries.firstOrNull { it.id == raw } ?: DUCKDUCKGO
    }
}

data class BrowserPreferences(
    val theme: SamuraiThemeId = SamuraiThemeId.MURASAKI_SHOGUN,
    val bottomBar: Boolean = false,
    val compactBar: Boolean = false,
    val samuraiAnimations: Boolean = true,
    val showFloatingKoKoro: Boolean = true,
    // Real Gaming Boost controls
    val gamingBoostEnabled: Boolean = false,
    val targetRefreshRate: Int = 120, // 0 = auto max, 60, 90, 120
    val touchBoostEnabled: Boolean = true,
    val sustainedPerformance: Boolean = true,
    val keepScreenOnInBoost: Boolean = true,
    val autoTrimRamOnBoost: Boolean = true,
    val showGamingHudOverlay: Boolean = false,
    val autoBoostGameDomains: Boolean = true,
    // Privacy, Ad Shield & QoL Addons
    val adShieldEnabled: Boolean = true,
    val adShieldWhitelist: Set<String> = emptySet(),
    val popupBlockerEnabled: Boolean = true,
    val forceDarkWeb: Boolean = true,
    val desktopMode: Boolean = false,
    val pageZoomPercent: Int = 100,
    val userAgentPreset: UserAgentPreset = UserAgentPreset.CHROME_MOBILE,
    val customUserAgent: String = "",
    val searchEngine: SearchEngine = SearchEngine.DUCKDUCKGO,
    val homepage: String = "https://duckduckgo.com/",
    val askWhereToSave: Boolean = false,
)

data class BookmarkItem(
    val url: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
)

data class HistoryItem(
    val url: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
)

data class SessionSnapshot(
    val urls: List<String> = emptyList(),
    val titles: List<String> = emptyList(),
    val navStates: List<String> = emptyList(),
    val activeIndex: Int = 0,
)

class KoKoroRepository(private val context: Context) {
    private object Keys {
        val THEME = stringPreferencesKey("samurai_theme")
        val BOTTOM_BAR = booleanPreferencesKey("bottom_bar")
        val COMPACT_BAR = booleanPreferencesKey("compact_bar")
        val SAMURAI_ANIM = booleanPreferencesKey("samurai_animations")
        val FLOATING_KOKORO = booleanPreferencesKey("show_floating_kokoro")

        val GAMING_BOOST = booleanPreferencesKey("gaming_boost_enabled")
        val TARGET_HZ = intPreferencesKey("target_refresh_rate")
        val TOUCH_BOOST = booleanPreferencesKey("touch_boost_enabled")
        val SUSTAINED_PERF = booleanPreferencesKey("sustained_performance")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on_boost")
        val AUTO_TRIM_RAM = booleanPreferencesKey("auto_trim_ram_boost")
        val GAMING_HUD = booleanPreferencesKey("show_gaming_hud_overlay")
        val AUTO_BOOST_GAMES = booleanPreferencesKey("auto_boost_game_domains")

        val AD_SHIELD = booleanPreferencesKey("ad_shield_enabled")
        val AD_WHITELIST = stringSetPreferencesKey("ad_shield_whitelist")
        val POPUP_BLOCKER = booleanPreferencesKey("popup_blocker_enabled")
        val FORCE_DARK = booleanPreferencesKey("force_dark_web")
        val DESKTOP_MODE = booleanPreferencesKey("desktop_mode")
        val PAGE_ZOOM = intPreferencesKey("page_zoom_percent")
        val UA_PRESET = stringPreferencesKey("user_agent_preset")
        val CUSTOM_UA = stringPreferencesKey("custom_user_agent")
        val SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val HOMEPAGE = stringPreferencesKey("homepage")
        val ASK_WHERE_TO_SAVE = booleanPreferencesKey("ask_where_to_save")

        val BOOKMARKS_JSON = stringPreferencesKey("bookmarks_json")
        val HISTORY_JSON = stringPreferencesKey("history_json")
        val SESSION_JSON = stringPreferencesKey("session_json")
    }

    val preferencesFlow: Flow<BrowserPreferences> = context.kokoroDataStore.data.map { p ->
        p.toBrowserPreferences()
    }

    val bookmarksFlow: Flow<List<BookmarkItem>> = context.kokoroDataStore.data.map { p ->
        parseBookmarks(p[Keys.BOOKMARKS_JSON])
    }

    val historyFlow: Flow<List<HistoryItem>> = context.kokoroDataStore.data.map { p ->
        parseHistory(p[Keys.HISTORY_JSON])
    }

    suspend fun loadInitialPreferences(): BrowserPreferences =
        context.kokoroDataStore.data.first().toBrowserPreferences()

    suspend fun loadSession(): SessionSnapshot {
        val raw = context.kokoroDataStore.data.first()[Keys.SESSION_JSON] ?: return SessionSnapshot()
        return runCatching {
            val obj = JSONObject(raw)
            val urls = obj.optJSONArray("urls").toStringList()
            val titles = obj.optJSONArray("titles").toStringList()
            val navStates = obj.optJSONArray("navStates").toStringList()
            val active = obj.optInt("activeIndex", 0)
            SessionSnapshot(urls, titles, navStates, active)
        }.getOrElse { SessionSnapshot() }
    }

    suspend fun saveSession(snapshot: SessionSnapshot) {
        val obj = JSONObject().apply {
            put("urls", JSONArray(snapshot.urls))
            put("titles", JSONArray(snapshot.titles))
            put("navStates", JSONArray(snapshot.navStates))
            put("activeIndex", snapshot.activeIndex)
        }
        context.kokoroDataStore.edit { it[Keys.SESSION_JSON] = obj.toString() }
    }

    suspend fun clearSession() {
        context.kokoroDataStore.edit { it.remove(Keys.SESSION_JSON) }
    }

    suspend fun updatePreferences(transform: (BrowserPreferences) -> BrowserPreferences) {
        context.kokoroDataStore.edit { prefs ->
            val updated = transform(prefs.toBrowserPreferences())
            prefs[Keys.THEME] = updated.theme.id
            prefs[Keys.BOTTOM_BAR] = updated.bottomBar
            prefs[Keys.COMPACT_BAR] = updated.compactBar
            prefs[Keys.SAMURAI_ANIM] = updated.samuraiAnimations
            prefs[Keys.FLOATING_KOKORO] = updated.showFloatingKoKoro

            prefs[Keys.GAMING_BOOST] = updated.gamingBoostEnabled
            prefs[Keys.TARGET_HZ] = updated.targetRefreshRate
            prefs[Keys.TOUCH_BOOST] = updated.touchBoostEnabled
            prefs[Keys.SUSTAINED_PERF] = updated.sustainedPerformance
            prefs[Keys.KEEP_SCREEN_ON] = updated.keepScreenOnInBoost
            prefs[Keys.AUTO_TRIM_RAM] = updated.autoTrimRamOnBoost
            prefs[Keys.GAMING_HUD] = updated.showGamingHudOverlay
            prefs[Keys.AUTO_BOOST_GAMES] = updated.autoBoostGameDomains

            prefs[Keys.AD_SHIELD] = updated.adShieldEnabled
            prefs[Keys.AD_WHITELIST] = updated.adShieldWhitelist
            prefs[Keys.POPUP_BLOCKER] = updated.popupBlockerEnabled
            prefs[Keys.FORCE_DARK] = updated.forceDarkWeb
            prefs[Keys.DESKTOP_MODE] = updated.desktopMode
            prefs[Keys.PAGE_ZOOM] = updated.pageZoomPercent.coerceIn(75, 150)
            prefs[Keys.UA_PRESET] = updated.userAgentPreset.id
            prefs[Keys.CUSTOM_UA] = updated.customUserAgent
            prefs[Keys.SEARCH_ENGINE] = updated.searchEngine.id
            prefs[Keys.HOMEPAGE] = updated.homepage.ifBlank { "https://duckduckgo.com/" }
            prefs[Keys.ASK_WHERE_TO_SAVE] = updated.askWhereToSave
        }
    }

    suspend fun toggleBookmark(url: String, title: String) {
        if (url.isBlank() || url == "about:blank") return
        context.kokoroDataStore.edit { prefs ->
            val current = parseBookmarks(prefs[Keys.BOOKMARKS_JSON]).toMutableList()
            val idx = current.indexOfFirst { it.url == url }
            if (idx >= 0) {
                current.removeAt(idx)
            } else {
                current.add(0, BookmarkItem(url = url, title = title.ifBlank { url }))
            }
            prefs[Keys.BOOKMARKS_JSON] = encodeBookmarks(current)
        }
    }

    suspend fun removeBookmark(url: String) {
        context.kokoroDataStore.edit { prefs ->
            val current = parseBookmarks(prefs[Keys.BOOKMARKS_JSON]).filterNot { it.url == url }
            prefs[Keys.BOOKMARKS_JSON] = encodeBookmarks(current)
        }
    }

    suspend fun addHistory(url: String, title: String) {
        if (url.isBlank() || url == "about:blank") return
        context.kokoroDataStore.edit { prefs ->
            val current = parseBookmarksOrHistoryTrimmed(
                parseHistory(prefs[Keys.HISTORY_JSON]),
                HistoryItem(url = url, title = title.ifBlank { url }),
            )
            prefs[Keys.HISTORY_JSON] = encodeHistory(current)
        }
    }

    suspend fun clearHistory() {
        context.kokoroDataStore.edit { it.remove(Keys.HISTORY_JSON) }
    }

    private fun Preferences.toBrowserPreferences(): BrowserPreferences = BrowserPreferences(
        theme = SamuraiThemeId.fromId(this[Keys.THEME]),
        bottomBar = this[Keys.BOTTOM_BAR] ?: false,
        compactBar = this[Keys.COMPACT_BAR] ?: false,
        samuraiAnimations = this[Keys.SAMURAI_ANIM] ?: true,
        showFloatingKoKoro = this[Keys.FLOATING_KOKORO] ?: true,
        gamingBoostEnabled = this[Keys.GAMING_BOOST] ?: false,
        targetRefreshRate = this[Keys.TARGET_HZ] ?: 120,
        touchBoostEnabled = this[Keys.TOUCH_BOOST] ?: true,
        sustainedPerformance = this[Keys.SUSTAINED_PERF] ?: true,
        keepScreenOnInBoost = this[Keys.KEEP_SCREEN_ON] ?: true,
        autoTrimRamOnBoost = this[Keys.AUTO_TRIM_RAM] ?: true,
        showGamingHudOverlay = this[Keys.GAMING_HUD] ?: false,
        autoBoostGameDomains = this[Keys.AUTO_BOOST_GAMES] ?: true,
        adShieldEnabled = this[Keys.AD_SHIELD] ?: true,
        adShieldWhitelist = this[Keys.AD_WHITELIST] ?: emptySet(),
        popupBlockerEnabled = this[Keys.POPUP_BLOCKER] ?: true,
        forceDarkWeb = this[Keys.FORCE_DARK] ?: true,
        desktopMode = this[Keys.DESKTOP_MODE] ?: false,
        pageZoomPercent = (this[Keys.PAGE_ZOOM] ?: 100).coerceIn(75, 150),
        userAgentPreset = UserAgentPreset.fromId(this[Keys.UA_PRESET]),
        customUserAgent = this[Keys.CUSTOM_UA] ?: "",
        searchEngine = SearchEngine.fromId(this[Keys.SEARCH_ENGINE]),
        homepage = this[Keys.HOMEPAGE]?.ifBlank { "https://duckduckgo.com/" } ?: "https://duckduckgo.com/",
        askWhereToSave = this[Keys.ASK_WHERE_TO_SAVE] ?: false,
    )

    private fun parseBookmarks(raw: String?): List<BookmarkItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        BookmarkItem(
                            url = o.getString("url"),
                            title = o.optString("title", o.getString("url")),
                            createdAt = o.optLong("createdAt", 0L),
                        ),
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun encodeBookmarks(items: List<BookmarkItem>): String {
        val arr = JSONArray()
        items.forEach { b ->
            arr.put(
                JSONObject().apply {
                    put("url", b.url)
                    put("title", b.title)
                    put("createdAt", b.createdAt)
                },
            )
        }
        return arr.toString()
    }

    private fun parseHistory(raw: String?): List<HistoryItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        HistoryItem(
                            url = o.getString("url"),
                            title = o.optString("title", o.getString("url")),
                            timestamp = o.optLong("timestamp", 0L),
                        ),
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun parseBookmarksOrHistoryTrimmed(
        existing: List<HistoryItem>,
        newest: HistoryItem,
    ): List<HistoryItem> {
        val deduped = existing.filterNot { it.url == newest.url && (newest.timestamp - it.timestamp) < 60_000L }
        return (listOf(newest) + deduped).take(200)
    }

    private fun encodeHistory(items: List<HistoryItem>): String {
        val arr = JSONArray()
        items.forEach { h ->
            arr.put(
                JSONObject().apply {
                    put("url", h.url)
                    put("title", h.title)
                    put("timestamp", h.timestamp)
                },
            )
        }
        return arr.toString()
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return List(length()) { optString(it, "") }
    }
}
