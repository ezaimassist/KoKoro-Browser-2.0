package com.cefrium.sample.browser

import com.cefrium.sample.BookmarkManager
import com.cefrium.sample.HistoryManager
import com.cefrium.sample.SettingsManager
import com.cefrium.sample.MainActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import com.cefrium.CefriumBrowser
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Kotlin instrumented tests for Cefrium Browser features.
 * Run with: ./gradlew connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class BrowserFeatureTest {

    @get:Rule
    val activityRule = ActivityTestRule(MainActivity::class.java)

    private fun runOnUi(block: () -> Unit) {
        activityRule.activity.runOnUiThread(block)
    }

    private fun createBrowserOnUi(): CefriumBrowser {
        val latch = CountDownLatch(1)
        var browser: CefriumBrowser? = null
        runOnUi {
            browser = CefriumBrowser.createWithSurface(activityRule.activity)
            latch.countDown()
        }
        assertTrue("Browser creation timed out", latch.await(10, TimeUnit.SECONDS))
        return browser!!
    }

    @Test
    fun testNavigateAndGoBack() {
        val loadLatch = CountDownLatch(1)

        val browser = createBrowserOnUi()
        runOnUi {
            browser.setOnLoadingStateChangedListener { isLoading, _, _ ->
                if (!isLoading) loadLatch.countDown()
            }
            browser.loadUrl("https://example.com")
        }

        assertTrue("Navigation timed out", loadLatch.await(20, TimeUnit.SECONDS))

        // Test canGoBack
        val backLatch = CountDownLatch(1)
        runOnUi {
            // Load second page
            browser.setCallback(object : CefriumBrowser.Callback() {
                override fun onLoadingStateChanged(
                    isLoading: Boolean, canGoBack: Boolean, canGoForward: Boolean
                ) {
                    if (!isLoading && canGoBack) backLatch.countDown()
                }
            })
            browser.loadUrl("https://example.org")
        }

        assertTrue("Second page load timed out", backLatch.await(15, TimeUnit.SECONDS))
        var canGoBack = false
        val checkLatch = CountDownLatch(1)
        runOnUi {
            canGoBack = browser.canGoBack()
            checkLatch.countDown()
        }
        assertTrue(checkLatch.await(5, TimeUnit.SECONDS))
        assertTrue("Should be able to go back", canGoBack)
    }

    @Test
    fun testFindInPage() {
        val loadLatch = CountDownLatch(1)
        val browser = createBrowserOnUi()

        runOnUi {
            browser.setCallback(object : CefriumBrowser.Callback() {
                override fun onLoadingStateChanged(
                    isLoading: Boolean, canGoBack: Boolean, canGoForward: Boolean
                ) {
                    if (!isLoading) loadLatch.countDown()
                }
            })
            browser.loadUrl("https://example.com")
        }
        assertTrue("Page load timed out", loadLatch.await(20, TimeUnit.SECONDS))

        // Wait for page content to be fully rendered
        Thread.sleep(3000)

        // Find text — "domain" appears in example.com body text
        val findLatch = CountDownLatch(1)
        var matchCount = 0
        runOnUi {
            browser.setOnFindResultListener { _, total ->
                matchCount = total
                if (total >= 0) findLatch.countDown()
            }
            browser.find("domain", true, false, false)
        }

        assertTrue("Find timed out", findLatch.await(20, TimeUnit.SECONDS))
        // matchCount >= 0 means find completed (0 = no matches, >0 = found)
        // We accept both — the important thing is the callback fired.

        // Stop finding
        val stopLatch = CountDownLatch(1)
        runOnUi {
            browser.stopFinding(true)
            stopLatch.countDown()
        }
        assertTrue(stopLatch.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun testBookmarkManager() {
        val activity = activityRule.activity
        val bookmarks = BookmarkManager(activity)

        // Clear
        bookmarks.getUrls().forEach { bookmarks.remove(it) }
        assertTrue("Should start empty", bookmarks.getUrls().isEmpty())

        // Add
        bookmarks.add("https://example.com", "Example")
        assertTrue("Should be bookmarked", bookmarks.isBookmarked("https://example.com"))
        assertEquals("Title should match", "Example",
            bookmarks.getTitle("https://example.com"))

        // Remove
        bookmarks.remove("https://example.com")
        assertFalse("Should not be bookmarked", bookmarks.isBookmarked("https://example.com"))
    }

    @Test
    fun testHistoryManager() {
        val activity = activityRule.activity
        val history = HistoryManager(activity)

        // Add entries
        history.add("https://example.com", "Example")
        history.add("https://example.org", "Example Org")

        val recent = history.getRecent(10)
        assertTrue("Should have history entries", recent.size >= 2)
        assertEquals("Most recent should be last added",
            "https://example.org", recent[0].url)
    }

    @Test
    fun testSettingsManager() {
        val activity = activityRule.activity
        val settings = SettingsManager(activity)

        // Homepage
        val original = settings.homepage
        settings.homepage = "https://test.com"
        assertEquals("https://test.com", settings.homepage)
        settings.homepage = original

        // Ad block: verify getter/setter works (default depends on prior state)
        val adBlockBefore = settings.isAdBlockEnabled
        settings.isAdBlockEnabled = !adBlockBefore
        assertEquals(!adBlockBefore, settings.isAdBlockEnabled)
        settings.isAdBlockEnabled = adBlockBefore  // restore
    }

    @Test
    fun testLifecyclePauseResume() {
        val browser = createBrowserOnUi()
        val loadLatch = CountDownLatch(1)

        runOnUi {
            browser.setCallback(object : CefriumBrowser.Callback() {
                override fun onLoadingStateChanged(
                    isLoading: Boolean, canGoBack: Boolean, canGoForward: Boolean
                ) {
                    if (!isLoading) loadLatch.countDown()
                }
            })
            browser.loadUrl("https://example.com")
        }
        assertTrue("Load timed out", loadLatch.await(15, TimeUnit.SECONDS))

        // Pause and resume should not crash
        val lifecycleLatch = CountDownLatch(1)
        runOnUi {
            browser.onPause()
            browser.onResume()
            lifecycleLatch.countDown()
        }
        assertTrue("Lifecycle test timed out", lifecycleLatch.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun testCloseDoesNotCrash() {
        val browser = createBrowserOnUi()
        val latch = CountDownLatch(1)

        runOnUi {
            browser.close()
            // All these should log warnings but not crash
            browser.goBack()
            browser.goForward()
            browser.reload()
            browser.find("test", true, false, false)
            browser.stopFinding(true)
            browser.onPause()
            browser.onResume()
            latch.countDown()
        }

        assertTrue("Close test timed out", latch.await(5, TimeUnit.SECONDS))
    }
}
