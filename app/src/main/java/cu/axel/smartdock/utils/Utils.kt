package cu.axel.smartdock.utils

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.net.Uri
import android.view.Display
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import cu.axel.smartdock.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date

object Utils {
    var shouldPlayChargeComplete = false
    var startupTime: Long = 0

    fun toggleBuiltinNavigation(editor: SharedPreferences.Editor, value: Boolean) {
        editor.putBoolean("enable_nav_back", value)
        editor.putBoolean("enable_nav_home", value)
        editor.putBoolean("enable_nav_recents", value)
        editor.commit()
    }

    fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density + 0.5f).toInt()
    }

    fun getCircularBitmap(bitmap: Bitmap?): Bitmap? {
        if (bitmap == null) return null

        //Copy the bitmap to avoid software rendering issues
        val bitmapCopy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        bitmap.recycle()
        val result =
            createBitmap(bitmapCopy.width, bitmapCopy.height)
        val canvas = Canvas(result)
        val color = -0xbdbdbe
        val paint = Paint()
        val rect = Rect(0, 0, bitmapCopy.width, bitmapCopy.height)
        paint.isAntiAlias = true
        canvas.drawARGB(0, 0, 0, 0)
        paint.color = color
        canvas.drawCircle(
            (bitmapCopy.width / 2).toFloat(),
            (bitmapCopy.height / 2).toFloat(),
            (bitmap.width / 2).toFloat(),
            paint
        )
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmapCopy, rect, rect, paint)
        bitmapCopy.recycle()
        return result
    }

    fun getBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        var bitmap: Bitmap? = null
        val contentResolver = context.contentResolver
        try {
            val source = ImageDecoder.createSource(contentResolver, uri)
            bitmap = ImageDecoder.decodeBitmap(source)
        } catch (_: Exception) {
        }
        return bitmap
    }

    fun getBatteryDrawable(level: Int, plugged: Boolean): Int {
        if (plugged) {
            when (level) {
                0 -> return R.drawable.battery_charging_empty
                in 1..29 -> return R.drawable.battery_charging_20
                in 30..49 -> return R.drawable.battery_charging_30
                in 50..59 -> return R.drawable.battery_charging_50
                in 60..79 -> return R.drawable.battery_charging_60
                in 80..89 -> return R.drawable.battery_charging_80
                in 90..99 -> return R.drawable.battery_charging_90
                100 -> return R.drawable.battery_charging_full
            }
        } else {
            when (level) {
                0 -> return R.drawable.battery_empty
                in 1..29 -> return R.drawable.battery_20
                in 30..49 -> return R.drawable.battery_30
                in 50..59 -> return R.drawable.battery_50
                in 60..79 -> return R.drawable.battery_60
                in 80..89 -> return R.drawable.battery_80
                in 90..99 -> return R.drawable.battery_90
                100 -> return R.drawable.battery_full
            }
        }
        return R.drawable.battery_empty
    }

    fun saveLog(context: Context, name: String, log: String) {
        try {
            val fw = FileWriter(
                File(context.getExternalFilesDir(null), name + "_" + currentDateString + ".log")
            )
            fw.write(log)
            fw.close()
        } catch (_: IOException) {
        }
    }

    fun makeWindowParams(
        width: Int, height: Int, context: Context,
        secondary: Boolean = false,
        accessibilityWindow: Boolean = false
    ): WindowManager.LayoutParams {
        val displayId =
            // Null when no secondary display is actually available: window
            // params must then keep targeting the main screen instead of
            // picking a leftover display id apps cannot use (#263).
            if (secondary) DeviceUtils.getSecondaryDisplay(context)?.displayId
                ?: Display.DEFAULT_DISPLAY else Display.DEFAULT_DISPLAY
        val displayWidth = DeviceUtils.getDisplayBounds(context, displayId).width()
        val displayHeight = DeviceUtils.getDisplayBounds(context, displayId).height()
        val layoutParams = WindowManager.LayoutParams()
        layoutParams.format = PixelFormat.TRANSLUCENT
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        layoutParams.type =
            if (accessibilityWindow) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        layoutParams.width = displayWidth.coerceAtMost(width)
        layoutParams.height = displayHeight.coerceAtMost(height)
        return layoutParams
    }

    fun solve(expression: String): Double {
        // Full-expression evaluation (operators, precedence, parentheses and
        // unary minus) instead of the old first-pair-only math, which turned
        // "2+3+4" into 5. The parser lives in a private object: Kotlin local
        // functions cannot forward-reference each other (two CI rounds
        // failed on exactly that), while object methods can be mutually
        // recursive.
        return ExprParser.evaluate(tokenize(expression))
    }

    private fun tokenize(expression: String): List<String> {
        val tokens = mutableListOf<String>()
        val number = StringBuilder()
        fun flushNumber() {
            if (number.isNotEmpty()) {
                tokens.add(number.toString())
                number.setLength(0)
            }
        }
        for (c in expression) {
            when {
                c.isDigit() || c == '.' -> number.append(c)
                c == '+' || c == '-' || c == '*' || c == '/' || c == '^' || c == '(' || c == ')' -> {
                    flushNumber()
                    tokens.add(c.toString())
                }
                c.isWhitespace() -> flushNumber()
                else -> return emptyList() // reject anything the grammar doesn't know
            }
        }
        flushNumber()
        return tokens
    }

    fun backupPreferences(context: Context, backupUri: Uri) {
        val allPrefs = PreferenceManager.getDefaultSharedPreferences(context).all
        // JSON keeps every type alive — including the Set<String> keys the old
        // line-based format silently dropped (hidden apps, silenced
        // notifications) and the URI-valued prefs (icon packs, sounds) that
        // were skipped by the contains("://") heuristic.
        val json = JSONObject()
        for ((key, value) in allPrefs) {
            when (value) {
                is Boolean -> json.put(key, value)
                is Int -> json.put(key, value)
                is Long -> json.put(key, value)
                is Float -> json.put(key, value.toDouble())
                is Set<*> -> json.put(key, JSONArray(value.map { it.toString() }))
                else -> json.put(key, value?.toString() ?: JSONObject.NULL)
            }
        }
        val content = json.toString(2)
        var outputStream: OutputStream? = null
        try {
            outputStream = context.contentResolver.openOutputStream(backupUri)
            outputStream!!.write(content.toByteArray())
            outputStream.flush()
            Toast.makeText(context, R.string.preferences_saved, Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close()
                } catch (_: IOException) {
                }
            }
        }
    }

    fun restorePreferences(context: Context, restoreUri: Uri) {
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(restoreUri)
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            // Accept the new JSON format; fall back to reading the old
            // line-based backup ("type key value") so backups made before
            // v1.15.2-fork.6 still restore.
            val raw = inputStream!!.readBytes().decodeToString()
            val editor = sharedPreferences.edit()
            if (raw.trimStart().startsWith("{")) {
                val json = JSONObject(raw)
                for (key in json.keys()) {
                    val value = json.get(key) ?: continue
                    when (value) {
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Double -> editor.putFloat(key, value.toFloat())
                        is JSONArray -> editor.putStringSet(
                            key, (0 until value.length()).map { value[it].toString() }.toSet()
                        )
                        is String -> editor.putString(key, value)
                    }
                }
            } else {
                raw.lines().forEach { line ->
                    val contents = line.split(" ".toRegex()).filter { it.isNotEmpty() }
                    if (contents.size > 2) {
                        val type = contents[0]
                        val key = contents[1]
                        val value = contents.subList(2, contents.size).joinToString(" ")
                        when (type) {
                            "boolean" -> editor.putBoolean(key, java.lang.Boolean.parseBoolean(value))
                            "integer" -> editor.putInt(key, value.toInt())
                            else -> editor.putString(key, value)
                        }
                    }
                }
            }
            editor.apply()
            Toast.makeText(context, R.string.preferences_restored, Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    val currentDateString: String
        get() = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Date())

    fun openUrl(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        val chooserIntent = Intent.createChooser(intent, "Open with...")
        if (chooserIntent.resolveActivity(context.packageManager) != null)
            context.startActivity(chooserIntent)
    }
}

/**
 * Recursive-descent parser behind the app-menu calculator. Methods of one
 * object may mutually recurse (Kotlin local functions cannot
 * forward-reference, which is why this is not nested inside solve()).
 * Grammar: expr := term (('+'|'-') term)* ; term := power (('*'|'/') power)* ;
 * power := primary ('^' power)? ; primary := number | '(' expr ')' | sign primary.
 */
private object ExprParser {
    fun evaluate(tokens: List<String>): Double {
        if (tokens.isEmpty()) return 0.0
        val parser = Parser(tokens)
        val result = parser.expr()
        return if (result.isNaN() || result.isInfinite()) 0.0 else result
    }

    private class Parser(private val tokens: List<String>) {
        private var pos = 0

        private fun next(): String = tokens[pos++]

        fun expr(): Double {
            var value = term()
            while (pos < tokens.size && (tokens[pos] == "+" || tokens[pos] == "-")) {
                val op = next()
                val rhs = term()
                value = if (op == "+") value + rhs else value - rhs
            }
            return value
        }

        private fun term(): Double {
            var value = power()
            while (pos < tokens.size && (tokens[pos] == "*" || tokens[pos] == "/")) {
                val op = next()
                val rhs = power()
                value = if (op == "*") value * rhs else value / rhs
            }
            return value
        }

        // a^b binds tightest, right-associative: 2^3^2 = 2^(3^2).
        private fun power(): Double {
            val base = primary()
            if (pos < tokens.size && tokens[pos] == "^") {
                next()
                val exponent = power()
                return Math.pow(base, exponent)
            }
            return base
        }

        private fun primary(): Double {
            if (pos >= tokens.size) return 0.0
            val token = next()
            if (token == "(") {
                val value = expr()
                if (pos < tokens.size && tokens[pos] == ")") pos++
                return value
            }
            if (token == "-") return -primary()
            if (token == "+") return primary()
            return token.toDoubleOrNull() ?: 0.0
        }
    }
}
