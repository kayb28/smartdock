package cu.axel.smartdock.wrappers

import android.annotation.SuppressLint
import android.bluetooth.IBluetoothManager
import android.content.AttributionSource
import android.os.Build
import android.os.IBinder
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
            binder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("bluetooth_manager"))
            binder?.linkToDeath(deathRecipient, 0)
            bluetoothManager = IBluetoothManager.Stub.asInterface(binder)
        }
    }

    fun isAlive() = binder?.isBinderAlive == true && bluetoothManager != null

    @RequiresApi(Build.VERSION_CODES.S)
    fun setBluetoothEnabled(enabled: Boolean): Boolean {
        val packageName = "com.android.shell"
        val attributionSource =
            AttributionSource.Builder(Shizuku.getUid()).setPackageName(packageName).build()
        return if (enabled)
            bluetoothManager?.enable(attributionSource) ?: false
        else
            bluetoothManager?.disable(attributionSource, true) ?: false
    }
}