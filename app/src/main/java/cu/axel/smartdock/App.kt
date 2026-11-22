package cu.axel.smartdock

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import com.google.android.material.color.DynamicColors
import cu.axel.smartdock.activities.DebugActivity
import kotlin.system.exitProcess
import android.os.Process
import org.lsposed.hiddenapibypass.HiddenApiBypass
import cu.axel.smartdock.utils.CrashLog

class App : Application() {
    private lateinit var uncaughtExceptionHandler: Thread.UncaughtExceptionHandler
    override fun onCreate() {
        // Unseal hidden framework APIs for this process before anything
        // binds to them: the Shizuku wrappers link against hidden binder
        // interfaces (IActivityManager$Stub.asInterface and friends), and on
        // frameworks where that access is blocked the linking threw
        // NoSuchMethodError and killed the accessibility service in onCreate
        // before it drew anything — no dock anywhere and the app claiming
        // accessibility was off. Best effort: if the bypass itself does not
        // work on this Android version, the wrappers degrade individually
        // instead of taking the dock down.
        try {
            HiddenApiBypass.addHiddenApiExemptions("L")
        } catch (_: Throwable) {
        }
        // Bind the application context so the wrappers (which have no Context
        // of their own) and this crash handler can write crash files.
        CrashLog.init(this)
        DynamicColors.applyToActivitiesIfAvailable(this)
        uncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()!!
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            // Persist the crash to filesDir/crashes/ before the DebugActivity
            // flow kills the process, so it can be viewed and shared from
            // Settings. The existing DebugActivity crash UI is unchanged.
            CrashLog.log("App", exception)
            val report = StringBuilder("Exception: $exception\n")
            for (element in exception.stackTrace) report.append(element.toString()).append("\n")
            val cause = exception.cause
            if (cause != null) {
                report.append("Cause: ").append(cause).append("\n")
                for (element in cause.stackTrace) report.append(element.toString()).append("\n")
            }
            val message = exception.message
            if (message != null) report.append("Message: ").append(message)
            val intent = Intent(applicationContext, DebugActivity::class.java)
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            intent.putExtra("report", report.toString())
            //startActivity(intent);
            val pendingIntent = PendingIntent.getActivity(applicationContext, 11111, intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
            val am = getSystemService(ALARM_SERVICE) as AlarmManager
            am[AlarmManager.ELAPSED_REALTIME_WAKEUP, 1000] = pendingIntent
            Process.killProcess(Process.myPid())
            exitProcess(2)
            uncaughtExceptionHandler.uncaughtException(thread, exception)
        }
        super.onCreate()
    }
}
