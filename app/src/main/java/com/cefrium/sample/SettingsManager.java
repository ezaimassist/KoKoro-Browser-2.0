package com.cefrium.sample;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Browser settings stored in SharedPreferences.
 */
public class SettingsManager {
    private static final String PREFS = "cefrium_settings";
    private final SharedPreferences prefs;

    public SettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String getHomepage() {
        // Default start page: the Cefrium modern-web showcase (demonstrates the
        // engine's capabilities vs System WebView). DuckDuckGo remains the search
        // engine (see getSearchEngines). Users can change this in Settings.
        return prefs.getString("homepage", "https://duckduckgo.com/");
    }

    public void setHomepage(String url) {
        prefs.edit().putString("homepage", url).apply();
    }

    public String getUserAgent() {
        return prefs.getString("user_agent", "");
    }

    public void setUserAgent(String ua) {
        prefs.edit().putString("user_agent", ua).apply();
    }

    public boolean isAdBlockEnabled() {
        return prefs.getBoolean("adblock", false);
    }

    public void setAdBlockEnabled(boolean enabled) {
        prefs.edit().putBoolean("adblock", enabled).apply();
    }

    public boolean isDarkModeAuto() {
        return prefs.getBoolean("darkmode_auto", true);
    }

    public void setDarkModeAuto(boolean auto) {
        prefs.edit().putBoolean("darkmode_auto", auto).apply();
    }

    /**
     * Whether to advertise the Cefrium product token in the User-Agent. Default
     * false: present a plain Chrome UA so account sign-ins (Google/YouTube) work,
     * like Brave. Turn on to fly the Cefrium flag (some sites, notably Google
     * sign-in, may then ask for extra verification).
     */
    public boolean isIdentifyAsCefrium() {
        return prefs.getBoolean("identify_as_cefrium", false);
    }

    public void setIdentifyAsCefrium(boolean on) {
        prefs.edit().putBoolean("identify_as_cefrium", on).apply();
    }

    // "Ask where to save each file" -- off by default, matching Chrome on
    // Android. When on, downloads open a SAF document picker to choose location.
    public boolean isAskWhereToSave() {
        return prefs.getBoolean("ask_where_to_save", false);
    }

    public void setAskWhereToSave(boolean on) {
        prefs.edit().putBoolean("ask_where_to_save", on).apply();
    }

    public static final String[][] SEARCH_ENGINES = {
        {"DuckDuckGo", "https://duckduckgo.com/?q=%s"},
        {"Google", "https://www.google.com/search?q=%s"},
        {"Bing", "https://www.bing.com/search?q=%s"},
        {"Brave Search", "https://search.brave.com/search?q=%s"},
        {"Startpage", "https://www.startpage.com/sp/search?query=%s"},
    };

    public String getSearchEngine() {
        return prefs.getString("search_engine", SEARCH_ENGINES[0][0]);
    }

    public void setSearchEngine(String name) {
        prefs.edit().putString("search_engine", name).apply();
    }

    /**
     * Persists the open tabs (their URLs) and the active tab index so the session
     * survives a process kill. URLs never contain newlines, so a newline join is a
     * safe, dependency-free encoding.
     */
    public void saveSession(java.util.List<String> urls,
                            java.util.List<String> titles,
                            java.util.List<String> navStates, int activeIndex) {
        java.util.List<String> safeTitles = new java.util.ArrayList<>(titles.size());
        for (String t : titles) {
            safeTitles.add(t == null ? "" : t.replace('\n', ' ').replace('\r', ' '));
        }
        // navStates are base64 (no newlines) parallel to urls; "" = none for that tab.
        java.util.List<String> safeNav = new java.util.ArrayList<>(navStates.size());
        for (String s : navStates) safeNav.add(s == null ? "" : s);
        prefs.edit()
            .putString("session_urls", android.text.TextUtils.join("\n", urls))
            .putString("session_titles", android.text.TextUtils.join("\n", safeTitles))
            .putString("session_nav", android.text.TextUtils.join("\n", safeNav))
            .putInt("session_active", activeIndex)
            .apply();
    }

    public java.util.List<String> getSessionUrls() {
        String s = prefs.getString("session_urls", "");
        if (s.isEmpty()) return new java.util.ArrayList<>();
        return new java.util.ArrayList<>(java.util.Arrays.asList(s.split("\n", -1)));
    }

    /** Saved tab titles, parallel to {@link #getSessionUrls()} (may be shorter/empty). */
    public java.util.List<String> getSessionTitles() {
        String s = prefs.getString("session_titles", "");
        if (s.isEmpty()) return new java.util.ArrayList<>();
        return new java.util.ArrayList<>(java.util.Arrays.asList(s.split("\n", -1)));
    }

    /** Saved per-tab navigation-state blobs (base64), parallel to {@link #getSessionUrls()}. */
    public java.util.List<String> getSessionNavStates() {
        String s = prefs.getString("session_nav", "");
        if (s.isEmpty()) return new java.util.ArrayList<>();
        return new java.util.ArrayList<>(java.util.Arrays.asList(s.split("\n", -1)));
    }

    public int getSessionActive() {
        return prefs.getInt("session_active", 0);
    }

    public String getSearchUrl(String query) {
        String engine = getSearchEngine();
        for (String[] se : SEARCH_ENGINES) {
            if (se[0].equals(engine)) {
                return se[1].replace("%s", android.net.Uri.encode(query));
            }
        }
        return SEARCH_ENGINES[0][1].replace("%s", android.net.Uri.encode(query));
    }
}
