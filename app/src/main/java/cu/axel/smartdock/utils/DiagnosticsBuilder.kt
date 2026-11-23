package cu.axel.smartdock.utils

import android.content.Context
import android.os.Build
import androidx.preference.PreferenceManager

/**
 * Assembles a plain-text health snapshot for the Diagnostics panel
 * (Settings → Advanced → Diagnostics → "View diagnostics"): service state,
 * Shizuku wrapper liveness, granted permissions, displays, framework
 * version, the update-checker state (build tag, auto-check toggle, last
 * check, cached verdict), and a truncated tail of the last crash. The
 * output is meant to be shared with a bug report (ACTION_SEND) so "why
 * doesn't the dock work?" arrives with context instead of a screenshot.
 *
 * Pure reads — DeviceUtils / AppUtils / CrashLog / DockServiceState /
 * UpdateChecker — never touches the filesystem or network, never throws
 * (every call is already guarded at its source).
 * DeviceUtils/AppUtils/CrashLog/DockServiceState/UpdateChecker are in the
 * same `utils` package, so no imports; the auto-check toggle reads default
 * SharedPreferences via androidx.preference.PreferenceManager. Body labels
 * are English (diagnostic output, like crash logs); only the preference /
 * dialog / button strings are localized.
 */
object DiagnosticsBuilder {
    private const val CRASH_TAIL_CHARS = 600

    fun build(context: Context): String {
        val sb = StringBuilder()
        sb.appendLine("SmartDock diagnostics")
        sb.appendLine("Generated: ${java.util.Date()}")
        sb.appendLine()

        sb.appendLine("== Service ==")
        sb.appendLine("Running: ${DockServiceState.isRunning}")
        sb.appendLine()

        sb.appendLine("== Shizuku wrappers ==")
        sb.appendLine("ActivityManager:  ${alive(DockServiceState.activityManagerWrapper?.isAlive())}")
        sb.appendLine("WifiManager:      ${alive(DockServiceState.wifiManagerWrapper?.isAlive())}")
        sb.appendLine("BluetoothManager: ${alive(DockServiceState.bluetoothManagerWrapper?.isAlive())}")
        sb.appendLine()

        sb.appendLine("== Permissions / privileges ==")
        sb.appendLine("Shizuku bound:          ${DeviceUtils.hasShizukuPermission()}")
        sb.appendLine("System app:             ${AppUtils.isSystemApp(context, context.packageName)}")
        sb.appendLine("WRITE_SECURE_SETTINGS:  ${DeviceUtils.hasWriteSettingsPermission(context)}")
        sb.appendLine()

        sb.appendLine("== Displays ==")
        val displays = DeviceUtils.getDisplays(context)
        if (displays.isEmpty()) sb.appendLine("  (none)")
        else displays.forEach { sb.appendLine("  - $it") }
        sb.appendLine()

        sb.appendLine("== Framework ==")
        sb.appendLine("Android: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
        sb.appendLine("Device:  ${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine()

        sb.appendLine("== Updater ==")
        sb.appendLine("Build tag:    ${UpdateChecker.currentTag(context)}")
        val autoOn = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(UpdateChecker.KEY_AUTO_CHECK, true)
        sb.appendLine("Auto-check:   ${if (autoOn) "on" else "off"}")
        val lastMs = UpdateChecker.lastCheckMs(context)
        sb.appendLine("Last check:   ${if (lastMs == 0L) "never" else java.util.Date(lastMs)}")
        val verdict = UpdateChecker.cachedVerdict(context)
        if (verdict == null) {
            sb.appendLine("Latest seen:  (none cached)")
        } else {
            sb.appendLine("Latest seen:  ${verdict.tag}")
            sb.appendLine("Verdict:      ${if (verdict.isNewer) "update available" else "up to date"}")
        }
        sb.appendLine()

        sb.appendLine("== Last crash ==")
        val crash = CrashLog.latestCrashText()
        if (crash.isEmpty()) sb.appendLine("none")
        else sb.appendLine(crash.take(CRASH_TAIL_CHARS))

        return sb.toString()
    }

    private fun alive(b: Boolean?) =
        if (b == true) "alive" else "dead / unavailable"
}
