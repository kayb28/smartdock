package cu.axel.smartdock.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Local crash telemetry.
 *
 * Writes the full stack trace of an uncaught exception — or any Throwable
 * caught in a degrade path (the Shizuku wrappers' hidden-API link failures,
 * which used to be swallowed silently) — to a timestamped plain-text file
 * under `filesDir/crashes/`, with basic rotation so the directory cannot grow
 * unbounded. The logger holds the application context set by [init] so callers
 * without a Context (the wrappers, built from DockService) can still report.
 *
 * Logging never throws: every caller is already in a degrade path, and a
 * failing logger would only make things worse. On any internal error it falls
 * back to Log.e.
 */
object CrashLog {
    private const val TAG = "SmartDock"
    private const val DIR = "crashes"
    private const val MAX_FILES = 10

    @Volatile
    private var appContext: Context? = null

    /** Bind the application context. Call once from App.onCreate. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Report a throwable under [tag]; writes a crash file and logs to logcat. */
    fun log(tag: String, throwable: Throwable) {
        log(tag, null, throwable)
    }

    /** Report a throwable with an extra [message] for context. */
    fun log(tag: String, message: String?, throwable: Throwable) {
        val context = appContext
        if (context != null) {
            try {
                writeCrashFile(context, tag, message, throwable)
            } catch (t: Throwable) {
                Log.e(TAG, "CrashLog write failed: $t")
            }
        }
        // Always mirror to logcat so `adb logcat -s SmartDock` works even
        // without read access to filesDir.
        Log.e(tag, message ?: throwable.toString(), throwable)
    }

    private fun writeCrashFile(
        context: Context, tag: String, message: String?, throwable: Throwable
    ) {
        val dir = File(context.filesDir, DIR)
        if (!dir.exists()) dir.mkdirs()
        rotate(dir)
        val ts = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())
        val file = File(dir, "crash_${ts}.log")
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        pw.println("UTC time: $ts")
        pw.println("Tag: $tag")
        if (message != null) pw.println("Message: $message")
        pw.println("Throwable: $throwable")
        throwable.printStackTrace(pw)
        var cause = throwable.cause
        while (cause != null) {
            pw.println("Cause: $cause")
            cause.printStackTrace(pw)
            cause = cause.cause
        }
        pw.println("Android SDK: ${Build.VERSION.SDK_INT}")
        pw.println("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        file.writeText(sw.toString())
    }

    private fun rotate(dir: File) {
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
        val toDelete = files.size + 1 - MAX_FILES
        if (toDelete > 0)
            files.take(toDelete).forEach { it.delete() }
    }

    /** The most recent crash file, or null if none was ever written. */
    fun latestCrashFile(): File? {
        val context = appContext ?: return null
        val dir = File(context.filesDir, DIR)
        return dir.listFiles()?.maxByOrNull { it.lastModified() }
    }

    /** The text of the most recent crash, or empty if none. */
    fun latestCrashText(): String {
        val file = latestCrashFile() ?: return ""
        return try {
            file.readText()
        } catch (t: Throwable) {
            Log.e(TAG, "latestCrashText failed: $t")
            ""
        }
    }

    /**
     * Fire ACTION_SEND with the latest crash text. Returns false if there is
     * nothing to share or no activity can handle the intent.
     */
    fun shareLatest(context: Context): Boolean {
        val text = latestCrashText()
        if (text.isEmpty()) return false
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Intent.EXTRA_SUBJECT, "SmartDock crash log")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return try {
            context.startActivity(Intent.createChooser(intent, null))
            true
        } catch (t: Throwable) {
            Log.e(TAG, "shareLatest failed: $t")
            false
        }
    }
}
