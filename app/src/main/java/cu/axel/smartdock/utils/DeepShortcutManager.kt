package cu.axel.smartdock.utils

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.Process

/**
 * Performs operations related to deep shortcuts, such as querying for them, pinning them, etc.
 */
object DeepShortcutManager {
    //TODO: Add free form support
    fun startShortcut(shortcutInfo: ShortcutInfo, context: Context) {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        try {
            launcherApps.startShortcut(shortcutInfo.getPackage(), shortcutInfo.id, null, null,
                    Process.myUserHandle())
        } catch (_: Exception) {
        }
    }

    // Launch a pinned shortcut by its "package/shortcutId" line format: the
    // dock pin list stores shortcuts as package + shortcut id, unlike app
    // entries which are package + user serial.
    fun startShortcutById(packageName: String, shortcutId: String, context: Context): Boolean {
        val shortcuts = getShortcuts(packageName, context) ?: return false
        val shortcut = shortcuts.firstOrNull { it.id == shortcutId } ?: return false
        startShortcut(shortcut, context)
        return true
    }

    fun getShortcutIcon(shortcutInfo: ShortcutInfo, context: Context): Drawable? {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        try {
            val density = context.resources.displayMetrics.density * 48
            return launcherApps.getShortcutIconDrawable(shortcutInfo, density.toInt())
        } catch (_: Exception) {
        }
        return null
    }

    fun getShortcutById(packageName: String, shortcutId: String, context: Context): ShortcutInfo? {
        return getShortcuts(packageName, context)?.firstOrNull { it.id == shortcutId }
    }

    fun getShortcuts(app: String, context: Context): List<ShortcutInfo>? {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val queryParams = LauncherApps.ShortcutQuery()
        queryParams.setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC)
        queryParams.setPackage(app)
        return try {
            launcherApps.getShortcuts(queryParams, Process.myUserHandle())
        } catch (_: Exception) {
            null
        }
    }

    fun hasHostPermission(context: Context): Boolean {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        return launcherApps.hasShortcutHostPermission()
    }
}
