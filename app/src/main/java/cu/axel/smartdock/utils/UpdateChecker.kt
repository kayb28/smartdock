package cu.axel.smartdock.utils

import android.content.Context
import android.content.SharedPreferences
import cu.axel.smartdock.R
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Discrete update check against the fork's GitHub Releases.
 *
 * The fork is not on F-Droid, so the only way a user learns a new release
 * shipped is by watching the repo. This fetches
 *   GET https://api.github.com/repos/kayb28/smartdock/releases/latest
 * on a background thread, parses `tag_name` + `html_url`, and compares the
 * tag's `(major, minor, patch, forkN)` tuple against the installed build's
 * tuple — read from the non-translatable string `fork_release_tag`
 * (e.g. "v1.15.5-fork.9"), which is the single source of truth for the
 * build's own release tag.
 *
 * Tuple comparison (not `versionName`, which carries no `-fork.N` suffix)
 * is robust to same-version fork iterations: fork.10 on 1.15.5 correctly
 * compares newer than fork.9 on 1.15.5.
 *
 * Every entry point is **synchronous and must be called off the main
 * thread** (DockService launches it on `Dispatchers.IO`; the Settings
 * fragment wraps the live check in a `Thread` + main-thread `Handler`).
 * Network and parse failures degrade to `null` / a "could not check"
 * verdict — the checker never throws, never blocks startup, and adds no
 * dependency: `org.json` and `HttpURLConnection` are platform APIs.
 *
 * The silent startup check is rate-limited to once per 24h via the
 * `last_check_ms` preference, so the unauthenticated GitHub API
 * (60 req/h per IP) is never approached.
 */
object UpdateChecker {
    private const val LATEST_URL =
        "https://api.github.com/repos/kayb28/smartdock/releases/latest"
    private const val PREFS = "update_checker"
    private const val KEY_LAST_CHECK_MS = "last_check_ms"
    private const val KEY_LATEST_TAG = "latest_tag"
    private const val KEY_LATEST_URL = "latest_url"
    private const val KEY_LATEST_NEWER = "latest_newer"
    private const val SILENT_INTERVAL_MS = 24L * 60 * 60 * 1000

    data class LatestRelease(
        val tag: String,
        val htmlUrl: String,
    )

    /** The GitHub release tag this build ships as, e.g. "v1.15.5-fork.9". */
    fun currentTag(context: Context): String =
        context.getString(R.string.fork_release_tag)

    /**
     * Synchronous HTTP fetch + parse of the latest release. Returns `null`
     * on any failure (no network, non-200, malformed JSON, missing fields).
     * Caller MUST run this off the main thread.
     */
    fun fetchLatest(): LatestRelease? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(LATEST_URL).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            // GitHub requires a User-Agent on API requests.
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "SmartDock-fork")
            conn.connect()
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.use { src ->
                src.readBytes().toString(Charsets.UTF_8)
            }
            val obj = JSONObject(body)
            val tag = obj.optString("tag_name")
            val url = obj.optString("html_url")
            if (tag.isEmpty() || url.isEmpty()) null
            else LatestRelease(tag, url)
        } catch (t: Throwable) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * `true` iff [latest] denotes a newer release than [current]. Tags are
     * `v<major>.<minor>.<patch>-fork.<n>`; compares the version tuple first,
     * then the fork iteration.
     */
    fun isNewer(latest: String, current: String): Boolean {
        val lp = parseVersionTuple(latest)
        val cp = parseVersionTuple(current)
        val n = maxOf(lp.size, cp.size)
        val lpPadded = lp + List(n - lp.size) { 0 }
        val cpPadded = cp + List(n - cp.size) { 0 }
        for (i in 0 until n) {
            if (lpPadded[i] != cpPadded[i]) return lpPadded[i] > cpPadded[i]
        }
        return parseForkN(latest) > parseForkN(current)
    }

    // "v1.15.5-fork.9" -> [1, 15, 5]. `internal` so unit tests can exercise
    // the parser directly (UpdateCheckerTest); still module-private.
    internal fun parseVersionTuple(tag: String): List<Int> {
        val core = tag.removePrefix("v").substringBefore("-").trim()
        return core.split(".").mapNotNull { it.toIntOrNull() }
    }

    // "v1.15.5-fork.9" -> 9 ; "v1.15.5" (no fork suffix) -> 0. `internal` for
    // direct unit-test coverage (UpdateCheckerTest); still module-private.
    internal fun parseForkN(tag: String): Int {
        val marker = "-fork."
        val idx = tag.indexOf(marker)
        if (idx < 0) return 0
        return tag.substring(idx + marker.length)
            .substringBefore("-")
            .toIntOrNull() ?: 0
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Synchronous, rate-limited silent check (max once per 24h): fetches the
     * latest release and caches the tag/url/newer verdict in prefs so the
     * Settings entry can show it without a network hit. No-op if checked
     * recently. Caller MUST run this off the main thread.
     */
    fun checkSilentIfNeeded(context: Context) {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        if (now - p.getLong(KEY_LAST_CHECK_MS, 0L) < SILENT_INTERVAL_MS) return
        val latest = fetchLatest() ?: return
        val newer = isNewer(latest.tag, currentTag(context))
        p.edit()
            .putLong(KEY_LAST_CHECK_MS, now)
            .putString(KEY_LATEST_TAG, latest.tag)
            .putString(KEY_LATEST_URL, latest.htmlUrl)
            .putBoolean(KEY_LATEST_NEWER, newer)
            .apply()
    }

    /** Verdict cached by the last silent check, or `null` if never checked. */
    data class CachedVerdict(
        val tag: String,
        val htmlUrl: String,
        val isNewer: Boolean,
    )

    fun cachedVerdict(context: Context): CachedVerdict? {
        val p = prefs(context)
        val tag = p.getString(KEY_LATEST_TAG, null) ?: return null
        val url = p.getString(KEY_LATEST_URL, null) ?: return null
        return CachedVerdict(tag, url, p.getBoolean(KEY_LATEST_NEWER, false))
    }
}
