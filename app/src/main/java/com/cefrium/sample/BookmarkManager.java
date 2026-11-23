package com.cefrium.sample;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Simple bookmark storage using SharedPreferences.
 * Stores URL → title pairs.
 */
public class BookmarkManager {
    private static final String PREFS = "cefrium_bookmarks";
    private static final String KEY_URLS = "bookmark_urls";
    private final SharedPreferences prefs;

    public BookmarkManager(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void add(String url, String title) {
        Set<String> urls = new HashSet<>(getUrls());
        urls.add(url);
        prefs.edit()
                .putStringSet(KEY_URLS, urls)
                .putString("title_" + url.hashCode(), title)
                .apply();
    }

    public void remove(String url) {
        Set<String> urls = new HashSet<>(getUrls());
        urls.remove(url);
        prefs.edit()
                .putStringSet(KEY_URLS, urls)
                .remove("title_" + url.hashCode())
                .apply();
    }

    public boolean isBookmarked(String url) {
        return getUrls().contains(url);
    }

    public String getTitle(String url) {
        return prefs.getString("title_" + url.hashCode(), url);
    }

    public List<String> getUrls() {
        Set<String> set = prefs.getStringSet(KEY_URLS, new HashSet<>());
        return new ArrayList<>(set);
    }
}
