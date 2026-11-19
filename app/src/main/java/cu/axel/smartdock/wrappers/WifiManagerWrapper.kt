package cu.axel.smartdock.wrappers

import android.annotation.SuppressLint
import android.net.wifi.IWifiManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.os.IBinder
import android.util.Log
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Method

class WifiManagerWrapper {
    private var getListMethod: Method? = null
    private var getScanResultsMethod: Method? = null
    private var getConnectionInfoMethod: Method? = null
    private var binder: ShizukuBinderWrapper? = null
    private var wifiManager: IWifiManager? = null

    private val deathRecipient = IBinder.DeathRecipient {
        synchronized(this) {
            wifiManager = null
            binder = null
        }
    }

    init {
        init()
    }

    @SuppressLint("BlockedPrivateApi", "PrivateApi")
    fun init() {
        synchronized(this) {
            if (wifiManager != null)
                return
            val shizukuBinder =
                ShizukuBinderWrapper(SystemServiceHelper.getSystemService("wifi"))
            try {
                shizukuBinder.linkToDeath(deathRecipient, 0)
            } catch (_: Throwable) {
            }
            binder = shizukuBinder
            // Same deal as IActivityManager: the interface resolves against
            // the real framework class and may not link on new frameworks.
            // Come up dead instead of throwing out of the constructor.
            try {
                wifiManager = IWifiManager.Stub.asInterface(shizukuBinder)
            } catch (t: Throwable) {
                Log.e(TAG, "IWifiManager unavailable: $t")
                wifiManager = null
                return
            }
            // Method lookups are best effort: a framework that changed a
            // signature leaves the wrapper alive (the Wi-Fi toggle keeps
            // working) while the affected queries degrade to empty results.
            try {
                getScanResultsMethod = wifiManager!!.javaClass.getDeclaredMethod(
                    "getScanResults",
                    String::class.java, String::class.java
                )
            } catch (t: Throwable) {
                Log.e(TAG, "getScanResults lookup failed: $t")
            }
            try {
                getConnectionInfoMethod = wifiManager!!.javaClass.getDeclaredMethod(
                    "getConnectionInfo",
                    String::class.java, String::class.java
                )
            } catch (t: Throwable) {
                Log.e(TAG, "getConnectionInfo lookup failed: $t")
            }
            try {
                val parceledListSliceClass =
                    Class.forName("com.android.wifi.x.com.android.modules.utils.ParceledListSlice")
                getListMethod = parceledListSliceClass.getDeclaredMethod("getList")
            } catch (t: Throwable) {
                Log.e(TAG, "ParceledListSlice lookup failed: $t")
            }
        }
    }

    fun isAlive() = binder?.isBinderAlive == true && wifiManager != null

    fun setWifiEnabled(enabled: Boolean): Boolean {
        return try {
            wifiManager?.setWifiEnabled("com.android.shell", enabled) ?: false
        } catch (t: Throwable) {
            Log.e(TAG, "setWifiEnabled failed: $t")
            false
        }
    }

    fun getConnectionInfo(): WifiInfo? {
        val manager = wifiManager ?: return null
        val method = getConnectionInfoMethod ?: return null
        return try {
            method.invoke(manager, "com.android.shell", "") as WifiInfo?
        } catch (t: Throwable) {
            Log.e(TAG, "getConnectionInfo failed: $t")
            null
        }
    }

    fun getScanResults(): List<ScanResult> {
        val manager = wifiManager ?: return emptyList()
        val method = getScanResultsMethod ?: return emptyList()
        return try {
            val results = method.invoke(manager, "com.android.shell", "")
            if (results is ArrayList<*>)
                @Suppress("UNCHECKED_CAST")
                (results as ArrayList<ScanResult>)
            else
                @Suppress("UNCHECKED_CAST")
                (getListMethod?.invoke(results) as? ArrayList<ScanResult>) ?: emptyList()
        } catch (t: Throwable) {
            Log.e(TAG, "getScanResults failed: $t")
            emptyList()
        }
    }

    fun startScan() {
        try {
            wifiManager?.startScan("com.android.shell", "")
        } catch (t: Throwable) {
            Log.e(TAG, "startScan failed: $t")
        }
    }

    companion object {
        private const val TAG = "SmartDock"
    }
}
