package cu.axel.smartdock.utils

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.media.MediaPlayer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import android.view.Display
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import cu.axel.smartdock.services.DockService
import cu.axel.smartdock.utils.DeviceUtils
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStreamReader

object DeviceUtils {
    const val DISPLAY_SIZE = "display_density_forced"
    const val ICON_BLACKLIST = "icon_blacklist"
    const val HEADS_UP_ENABLED = "heads_up_notifications_enabled"
    const val SETTING_OVERLAYS = "secure_overlay_settings"

    /**
     * The global settings behind the developer options "Force desktop mode"
     * and "Enable freeform windows" toggles. Without them, external
     * monitors are second-class surfaces: apps open there in fullscreen,
     * windows get no system-drawn title bars (nothing to drag or resize
     * with the mouse) and the display shows no launcher or system bars.
     * WindowManagerService observes both settings at runtime (no reboot
     * strictly required, though reconnecting the display helps).
     */
    const val FORCE_DESKTOP_MODE_ON_EXTERNAL_DISPLAYS =
        "force_desktop_mode_on_external_displays"
    const val ENABLE_FREEFORM_SUPPORT = "enable_freeform_support"
    private const val SERVICE_NAME = "cu.axel.smartdock/cu.axel.smartdock.services.DockService"
    private const val ENABLED_ACCESSIBILITY_SERVICES = "enabled_accessibility_services"

    @get:Throws(IOException::class)
    val rootAccess: Process
        //Xtr126
        get() {
            val paths = arrayOf(
                "/sbin/su", "/system/sbin/su", "/system/bin/su", "/system/xbin/su", "/su/bin/su",
                "/magisk/.core/bin/su"
            )
            for (path in paths) {
                if (File(path).canExecute()) return Runtime.getRuntime().exec(path)
            }
            return Runtime.getRuntime().exec("/system/bin/su")
        }

    fun runAsRoot(command: String): String {
        val output = StringBuilder()
        try {
            val process = rootAccess
            val os = DataOutputStream(process.outputStream)
            os.writeBytes(command + "\n")
            os.flush()
            os.close()
            val br = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (br.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            br.close()
        } catch (_: IOException) {
            return "error"
        }
        return output.toString().trimEnd('\n')
    }

    /**
     * Runs a shell command through Shizuku, i.e. with the privileges of
     * its server: the shell user when started from ADB or wireless
     * debugging (which holds WRITE_SECURE_SETTINGS), or root. This covers
     * installs that use Shizuku instead of granting the app permissions.
     * Returns null when Shizuku is not running, the permission has not
     * been granted or the command failed, so callers can fall back to
     * other methods.
     */
    fun runViaShizuku(command: String): String? {
        if (!hasShizukuPermission()) return null
        return try {
            val service = IShizukuService.Stub.asInterface(Shizuku.getBinder())
                ?: return null
            val process = service.newProcess(arrayOf("sh", "-c", command), null, null)
            // "settings" commands write nothing on success, so both
            // streams are tiny and reading them to EOF cannot deadlock.
            val output = ParcelFileDescriptor.AutoCloseInputStream(process.inputStream)
                .bufferedReader().use { it.readText() }
            val error = ParcelFileDescriptor.AutoCloseInputStream(process.errorStream)
                .bufferedReader().use { it.readText() }
            if (error.isNotBlank() || process.waitFor() != 0) null else output
        } catch (_: Exception) {
            null
        }
    }

    fun softReboot() {
        runAsRoot("setprop ctl.restart zygote")
    }

    fun reboot() {
        runAsRoot("am start -a android.intent.action.REBOOT")
    }

    fun shutdown() {
        runAsRoot(
            "am start -a com.android.internal.intent.action.REQUEST_SHUTDOWN"
        )
    }

    fun playEventSound(context: Context, event: String) {
        val soundUri =
            PreferenceManager.getDefaultSharedPreferences(context).getString(event, "default")
        if (soundUri != "default") {
            try {
                val sound = soundUri?.toUri()
                if (sound != null) {
                    val mp = MediaPlayer.create(context, sound)
                    mp.start()
                    mp.setOnCompletionListener { mp.release() }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun getSecureSetting(context: Context, setting: String, defaultValue: String): String {
        return try {
            val value = Settings.Secure.getString(context.contentResolver, setting)
            value ?: defaultValue
        } catch (_: Exception) {
            defaultValue
        }
    }

    fun putSecureSetting(context: Context, setting: String, value: String): Boolean {
        return try {
            Settings.Secure.putString(context.contentResolver, setting, value)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun putGlobalSetting(context: Context, setting: String, value: Int): Boolean {
        return try {
            Settings.Global.putInt(context.contentResolver, setting, value)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun getGlobalSetting(context: Context, setting: String, defaultValue: Int): Int {
        return try {
            Settings.Global.getInt(context.contentResolver, setting)
        } catch (_: Exception) {
            defaultValue
        }
    }

    /**
     * Whether external displays run as full desktops (the developer
     * options "Force desktop mode" state), which is what makes floating
     * windows usable on a monitor.
     */
    fun isExternalDisplayDesktopModeEnabled(context: Context): Boolean {
        return getGlobalSetting(
            context, FORCE_DESKTOP_MODE_ON_EXTERNAL_DISPLAYS, 0
        ) != 0
    }

    /**
     * Enables or disables desktop mode on external displays. Also turns on
     * freeform window support when enabling, since floating windows need
     * both. Writing global settings requires the WRITE_SECURE_SETTINGS
     * permission (granted via ADB on a normal install); the Shizuku shell
     * user holds it too (installs that use Shizuku instead of granting the
     * app the permission), and root is the last resort. Returns whether
     * the setting could actually be applied.
     */
    fun setExternalDisplayDesktopMode(context: Context, enabled: Boolean): Boolean {
        val value = if (enabled) 1 else 0
        val command = if (enabled)
            "settings put global $FORCE_DESKTOP_MODE_ON_EXTERNAL_DISPLAYS $value " +
                    "&& settings put global $ENABLE_FREEFORM_SUPPORT 1"
        else
            "settings put global $FORCE_DESKTOP_MODE_ON_EXTERNAL_DISPLAYS $value"
        if (hasWriteSettingsPermission(context)
            && putGlobalSetting(context, FORCE_DESKTOP_MODE_ON_EXTERNAL_DISPLAYS, value)
        ) {
            // Freeform support governs the main screen as well, so it is
            // only ever turned on here, never off.
            if (enabled) putGlobalSetting(context, ENABLE_FREEFORM_SUPPORT, 1)
            return true
        }
        // runViaShizuku returns null when Shizuku is unavailable or the
        // command failed; an empty string means the "settings" call went
        // through without errors.
        if (runViaShizuku(command) != null) return true
        // runAsRoot returns "error" when no su binary is available and
        // an empty string on permission problems.
        val result = runAsRoot(command)
        return result != "error" && result.isNotEmpty()
    }

    fun getSystemSetting(context: Context, setting: String, defaultValue: String): String {
        return try {
            val value = Settings.System.getString(context.contentResolver, setting)
            value ?: defaultValue
        } catch (_: Exception) {
            defaultValue
        }
    }

    fun putSystemSetting(context: Context, setting: String, value: String): Boolean {
        return try {
            Settings.System.putString(context.contentResolver, setting, value)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    //Device info
    @SuppressLint("InternalInsetResource", "DiscouragedApi")
    fun getStatusBarHeight(context: Context): Int {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val insets =
            windowManager.currentWindowMetrics.windowInsets.getInsets(WindowInsets.Type.statusBars())
        return insets.top + insets.bottom

    }

    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    fun getNavBarHeight(context: Context): Int {
        //FIXME: This returns 0 with Dex
        return try {
            context.display
            var result = 0
            val resourceId =
                context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
            if (resourceId > 0) {
                result = context.resources.getDimensionPixelSize(resourceId)
            }
            result
        } catch (_: UnsupportedOperationException) {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val insets =
                windowManager.currentWindowMetrics.windowInsets.getInsets(WindowInsets.Type.navigationBars())
            insets.top + insets.bottom
        }
    }

    fun getUserName(context: Context): String? {
        val um = context.getSystemService(Context.USER_SERVICE) as UserManager
        try {
            return um.userName
        } catch (_: Exception) {
        }
        return null
    }

    fun getUserIcon(context: Context): Bitmap? {
        val um = context.getSystemService(Context.USER_SERVICE) as UserManager
        var userIcon: Bitmap? = null
        try {
            val getUserIcon = um.javaClass.getMethod("getUserIcon", Int::class.javaPrimitiveType)
            val myUserId = UserHandle::class.java.getMethod("myUserId")
            val id = myUserId.invoke(UserHandle::class.java) as Int
            userIcon = getUserIcon.invoke(um, id) as Bitmap?
            if (userIcon != null) userIcon = Utils.getCircularBitmap(userIcon)
        } catch (_: Exception) {
        }
        return userIcon
    }

    fun getDisplays(context: Context, category: String? = null): Array<Display> {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return dm.getDisplays(category)
    }

    /**
     * The best display to move the dock and apps to when "prefer secondary
     * display" is on, or null when there is none.
     *
     * Displays of the presentation category are the ones apps are allowed
     * to show content on — real external monitors (USB-C/HDMI) always carry
     * it. The previous implementation took the last display of the whole
     * list, which on OEMs that append private virtual displays to it
     * (Samsung One UI's virtual monitor among others) picked a display apps
     * cannot launch on, so every launch failed (#263).
     */
    fun getSecondaryDisplay(context: Context): Display? {
        val presentations = getDisplays(
            context, DisplayManager.DISPLAY_CATEGORY_PRESENTATION
        ).filter { it.displayId != Display.DEFAULT_DISPLAY }
        if (presentations.isNotEmpty()) return presentations[0]
        return getDisplays(context).firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
    }

    fun getDisplayBounds(
        context: Context,
        displayId: Int = Display.DEFAULT_DISPLAY
    ): Rect {
        return try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            // getDisplay() returns null for ids that no longer exist: a
            // display that was just unplugged still shows up in cached lists
            // for a moment, and dereferencing null here crashed the dock
            // (#263). Fall back to the default display's metrics instead.
            val display = dm.getDisplay(displayId)
                ?: return getDefaultDisplayBounds(context)
            Rect(0, 0, display.width, display.height)
        } catch (_: UnsupportedOperationException) {
            getDefaultDisplayBounds(context)
        }
    }

    private fun getDefaultDisplayBounds(context: Context): Rect {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return windowManager.currentWindowMetrics.bounds
    }

    fun getDisplayContext(context: Context, secondary: Boolean = false): Context {
        if (!secondary) return context
        val display = getSecondaryDisplay(context) ?: return context
        return context.createDisplayContext(display)
    }

    @SuppressLint("PrivateApi")
    fun getSystemProp(prop: String): String {
        val systemPropertiesClass = Class.forName("android.os.SystemProperties")
        val getMethod = systemPropertiesClass.getMethod("get", String::class.java)

        return getMethod.invoke(null, prop) as String
    }

    fun shouldApplyNavbarFix(): Boolean {
        return Build.VERSION.SDK_INT > 31 && isNavbarEnabled()
    }

    fun isNavbarEnabled(): Boolean {
        return getSystemProp("qemu.hw.mainkeys") != "1"
    }

    //Permissions
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        for (enabledService in enabledServices) {
            val serviceInfo = enabledService.resolveInfo.serviceInfo
            if (serviceInfo.packageName == context.packageName && serviceInfo.name == DockService::class.java.name) {
                return true
            }
        }
        return false
    }

    fun hasStoragePermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestStoragePermissions(context: Activity) {
        ActivityCompat.requestPermissions(
            context,
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            8
        )
    }

    fun hasWriteSettingsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun grantPermission(permission: String): Boolean {
        val result = runAsRoot("pm grant cu.axel.smartdock $permission")
        return result.isEmpty()
    }

    fun grantOverlayPermissions(context: Activity) {
        context.startActivityForResult(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                ("package:" + context.packageName).toUri()
            ),
            8
        )
    }

    fun hasRecentAppsPermission(context: Context): Boolean {
        return AppUtils.isSystemApp(
            context,
            context.packageName
        ) || hasShizukuPermission() || checkAppOpsPermission(
            context,
            AppOpsManager.OPSTR_GET_USAGE_STATS
        )
    }

    private fun checkAppOpsPermission(context: Context, permission: String): Boolean {
        val packageManager = context.packageManager
        val applicationInfo: ApplicationInfo = try {
            packageManager.getApplicationInfo(context.packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return false
        }
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOpsManager.checkOpNoThrow(
            permission,
            applicationInfo.uid,
            applicationInfo.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun canWriteSettings(context: Context) =
        checkAppOpsPermission(context, AppOpsManager.OPSTR_WRITE_SETTINGS)

    fun hasShizukuPermission() =
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    //Service control
    fun enableService(context: Context) {
        val services = getSecureSetting(context, ENABLED_ACCESSIBILITY_SERVICES, "")
        if (services.contains(SERVICE_NAME)) return
        val newServices: String =
            if (services.isEmpty()) SERVICE_NAME else "$services:$SERVICE_NAME"
        putSecureSetting(context, ENABLED_ACCESSIBILITY_SERVICES, newServices)
    }

    /**
     * Whether the dock service is listed in the enabled accessibility services
     * setting, regardless of whether the system actually bound it: the state
     * an OS crash-reboot can leave behind is "listed but never bound", which
     * [isAccessibilityServiceEnabled] does not reliably distinguish from
     * "disabled by the user" (#268).
     */
    fun isServiceListed(context: Context): Boolean {
        val services = getSecureSetting(context, ENABLED_ACCESSIBILITY_SERVICES, "")
        if (services.isEmpty()) return false
        return services.split(':').any { it.equals(SERVICE_NAME, ignoreCase = true) }
    }

    fun disableService(context: Context) {
        val services = getSecureSetting(context, ENABLED_ACCESSIBILITY_SERVICES, "")
        if (!services.contains(SERVICE_NAME)) return
        var newServices = ""
        if (services.contains("$SERVICE_NAME:")) newServices = services.replace(
            "$SERVICE_NAME:",
            ""
        ) else if (services.contains(":$SERVICE_NAME")) newServices = services.replace(
            ":$SERVICE_NAME",
            ""
        ) else if (services.contains(SERVICE_NAME)) newServices = services.replace(SERVICE_NAME, "")
        putSecureSetting(context, ENABLED_ACCESSIBILITY_SERVICES, newServices)
    }

    fun canDrawOverOtherApps(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    @Suppress("DEPRECATION")
    fun isServiceRunning(context: Context, serviceName: Class<*>): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in activityManager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceName.name == service.service.className) {
                return true
            }
        }
        return false
    }

    fun getSettingsOverlaysAllowed(context: Context): Boolean {
        return getSecureSetting(context, SETTING_OVERLAYS, "0") == "1"
    }
}
