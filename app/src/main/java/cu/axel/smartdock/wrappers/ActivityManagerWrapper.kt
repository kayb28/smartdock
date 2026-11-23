package cu.axel.smartdock.wrappers

import android.app.ActivityManager
import android.app.IActivityManager
import android.graphics.Rect
import android.os.IBinder
import android.util.Log
import cu.axel.smartdock.utils.CrashLog
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

class ActivityManagerWrapper {

    private var binder: ShizukuBinderWrapper? = null
    private var activityManager: IActivityManager? = null

    private val deathRecipient = IBinder.DeathRecipient {
        synchronized(this) {
            activityManager = null
            binder = null
        }
    }

    init {
        init()
    }

    fun init() {
        synchronized(this) {
            if (activityManager != null)
                return
            val shizukuBinder =
                ShizukuBinderWrapper(SystemServiceHelper.getSystemService("activity"))
            try {
                shizukuBinder.linkToDeath(deathRecipient, 0)
            } catch (_: Throwable) {
            }
            binder = shizukuBinder
            // IActivityManager$Stub.asInterface resolves against the real
            // framework class (parent delegation shadows the local AIDL copy
            // whenever the framework ships its own). On frameworks where
            // that reference no longer links it throws NoSuchMethodError,
            // which used to escape initWrappers and kill the accessibility
            // service in onCreate — no dock anywhere and the app claiming
            // accessibility was off. Come up dead instead: isAlive() false
            // makes the dock fall back to public APIs.
            try {
                activityManager = IActivityManager.Stub.asInterface(shizukuBinder)
            } catch (t: Throwable) {
                CrashLog.log(TAG, "IActivityManager unavailable", t)
                activityManager = null
            }
        }
    }

    fun isAlive() = binder?.isBinderAlive == true && activityManager != null

    fun getRunningTasks(max: Int): List<ActivityManager.RunningTaskInfo> {
        return try {
            activityManager?.getTasks(max) ?: emptyList()
        } catch (t: Throwable) {
            Log.e(TAG, "getTasks failed: $t")
            emptyList()
        }
    }

    fun resizeTask(taskId: Int, bounds: Rect) {
        try {
            activityManager?.resizeTask(taskId, bounds, 0)
        } catch (t: Throwable) {
            Log.e(TAG, "resizeTask failed: $t")
        }
    }

    fun removeTask(taskId: Int): Boolean {
        return try {
            activityManager?.removeTask(taskId) ?: false
        } catch (t: Throwable) {
            Log.e(TAG, "removeTask failed: $t")
            false
        }
    }

    /**
     * Force-stop a package's process (upstream #123): stronger than
     * [removeTask], which only drops the task from recents. Mirrors the
     * Wi-Fi wrapper's degradation — returns false if the wrapper is dead or
     * the framework call throws, never escapes to the caller. Called from
     * the dock app context menu ("Force stop") gated on Shizuku, the same
     * privilege CLOSE already requires.
     */
    fun forceStop(packageName: String): Boolean {
        val am = activityManager ?: return false
        return try {
            am.forceStopPackage(packageName, 0)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "forceStopPackage failed: $t")
            false
        }
    }

    companion object {
        private const val TAG = "SmartDock"
    }
}
