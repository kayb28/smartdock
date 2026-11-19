package cu.axel.smartdock.wrappers

import android.annotation.SuppressLint
import android.bluetooth.IBluetoothManager
import android.content.AttributionSource
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

class BluetoothManagerWrapper {

    private var binder: ShizukuBinderWrapper? = null
    private var bluetoothManager: IBluetoothManager? = null

    private val deathRecipient = IBinder.DeathRecipient {
        synchronized(this) {
            bluetoothManager = null
            binder = null
        }
    }

    init {
        init()
    }

    @SuppressLint("BlockedPrivateApi", "PrivateApi")
    fun init() {
        synchronized(this) {
            if (bluetoothManager != null)
                return
            val shizukuBinder =
                ShizukuBinderWrapper(SystemServiceHelper.getSystemService("bluetooth_manager"))
            try {
                shizukuBinder.linkToDeath(deathRecipient, 0)
            } catch (_: Throwable) {
            }
            binder = shizukuBinder
            // Same deal as IActivityManager: the interface resolves against
            // the real framework class and may not link on new frameworks.
            // Come up dead instead of throwing out of the constructor.
            try {
                bluetoothManager = IBluetoothManager.Stub.asInterface(shizukuBinder)
            } catch (t: Throwable) {
                Log.e(TAG, "IBluetoothManager unavailable: $t")
                bluetoothManager = null
            }
        }
    }

    fun isAlive() = binder?.isBinderAlive == true && bluetoothManager != null

    @RequiresApi(Build.VERSION_CODES.S)
    fun setBluetoothEnabled(enabled: Boolean): Boolean {
        return try {
            val packageName = "com.android.shell"
            val attributionSource =
                AttributionSource.Builder(Shizuku.getUid()).setPackageName(packageName).build()
            if (enabled)
                bluetoothManager?.enable(attributionSource) ?: false
            else
                bluetoothManager?.disable(attributionSource, true) ?: false
        } catch (t: Throwable) {
            Log.e(TAG, "setBluetoothEnabled failed: $t")
            false
        }
    }

    companion object {
        private const val TAG = "SmartDock"
    }
}
