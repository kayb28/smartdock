package cu.axel.smartdock.utils

import android.content.Context
import android.graphics.Rect
import android.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * Named window geometry presets (F5, upstream #209: "letting the users
 * set up and store a few of these predefined window positions would
 * greatly improve productivity").
 *
 * Stored in the default SharedPreferences as one JSON array under
 * [KEY]; each entry keeps the four percents (left/top/width/height),
 * not resolved pixels, so a preset re-applies correctly on whichever
 * display the task lives on — same reasoning as makeLaunchBounds
 * computing bounds per display. Every method degrades to an empty
 * result on a malformed entry (old/foreign data must not crash the
 * dock), mirroring the wrapper degradation contracts.
 */
object WindowPresets {
    private const val KEY = "window_presets"

    /** Preset names in stored order, empty when none exist. */
    fun list(context: Context): List<String> {
        val names = ArrayList<String>()
        val array = readArray(context)
        for (i in 0 until array.length()) {
            val name = array.optJSONObject(i)?.optString("name") ?: continue
            if (name.isNotBlank()) names.add(name)
        }
        return names
    }

    /** Percent geometry of [name], or null when it does not exist. */
    fun get(context: Context, name: String): IntArray? {
        val array = readArray(context)
        for (i in 0 until array.length()) {
            val entry = array.optJSONObject(i) ?: continue
            if (entry.optString("name") == name)
                return intArrayOf(
                    entry.optInt("left"), entry.optInt("top"),
                    entry.optInt("width"), entry.optInt("height")
                )
        }
        return null
    }

    /** Insert or overwrite [name] with the given percent geometry. */
    fun save(context: Context, name: String, left: Int, top: Int, width: Int, height: Int) {
        val array = readArray(context)
        for (i in 0 until array.length())
            if (array.optJSONObject(i)?.optString("name") == name) {
                array.remove(i)
                break
            }
        array.put(
            JSONObject()
                .put("name", name)
                .put("left", left).put("top", top)
                .put("width", width).put("height", height)
        )
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putString(KEY, array.toString()).apply()
    }

    /** Remove [name]; a no-op when it does not exist. */
    fun delete(context: Context, name: String) {
        val array = readArray(context)
        for (i in 0 until array.length())
            if (array.optJSONObject(i)?.optString("name") == name) {
                array.remove(i)
                break
            }
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putString(KEY, array.toString()).apply()
    }

    /**
     * Resolve the stored percents into pixel bounds for a display area
     * ([displayLeft]/[displayWidth] horizontal, [areaTop]/[areaHeight]
     * vertical usable area). Percent values are clamped and the result
     * sanity-checked: a preset edited outside the app must never
     * produce the empty-Rect zero-window bug (same guard as the
     * "custom" launch mode of F7).
     */
    fun toBounds(
        displayLeft: Int, displayWidth: Int, areaTop: Int, areaHeight: Int,
        percents: IntArray
    ): Rect {
        val left = displayLeft + displayWidth * percents[0].coerceIn(0, 100) / 100
        val top = areaTop + areaHeight * percents[1].coerceIn(0, 100) / 100
        val width = displayWidth * percents[2].coerceIn(0, 100) / 100
        val height = areaHeight * percents[3].coerceIn(0, 100) / 100
        val right = (left + width).coerceAtMost(displayLeft + displayWidth)
        val bottom = (top + height).coerceAtMost(areaTop + areaHeight)
        return if (right > left && bottom > top) Rect(left, top, right, bottom)
        else Rect(displayLeft, areaTop, displayLeft + displayWidth, areaTop + areaHeight)
    }

    private fun readArray(context: Context): JSONArray {
        return try {
            val stored = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(KEY, null) ?: return JSONArray()
            JSONArray(stored)
        } catch (_: Exception) {
            JSONArray()
        }
    }
}
