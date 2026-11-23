package com.cefrium.sample;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple browsing history using SharedPreferences.
 * Stores last 100 URLs with timestamps.
 */
public class HistoryManager {
    private static final String PREFS = "cefrium_history";
    private static final String KEY_COUNT = "count";
    private static final int MAX_ENTRIES = 100;
    private final SharedPreferences prefs;

    public HistoryManager(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void add(String url, String title) {
        int count = prefs.getInt(KEY_COUNT, 0);
        int index = count % MAX_ENTRIES;
        prefs.edit()
                .putString("url_" + index, url)
                .putString("title_" + index, title)
                .putLong("time_" + index, System.currentTimeMillis())
                .putInt(KEY_COUNT, count + 1)
                .apply();
    }

    public List<HistoryEntry> getRecent(int limit) {
        int count = prefs.getInt(KEY_COUNT, 0);
        int total = Math.min(count, MAX_ENTRIES);
        int start = (count - 1) % MAX_ENTRIES;
        List<HistoryEntry> entries = new ArrayList<>();

        for (int i = 0; i < Math.min(total, limit); i++) {
            int index = (start - i + MAX_ENTRIES) % MAX_ENTRIES;
            String url = prefs.getString("url_" + index, null);
            if (url == null) break;
            String title = prefs.getString("title_" + index, url);
            long time = prefs.getLong("time_" + index, 0);
            entries.add(new HistoryEntry(url, title, time));
        }
        return entries;
    }

    public void clear() {
        prefs.edit().clear().apply();
    }

    public static class HistoryEntry {
        public final String url;
        public final String title;
        public final long timestamp;

        HistoryEntry(String url, String title, long timestamp) {
            this.url = url;
            this.title = title;
            this.timestamp = timestamp;
        }
    }
}
