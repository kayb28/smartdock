package cu.axel.smartdock.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cu.axel.smartdock.services.DockService

/**
 * Exported automation surface (D1): external automation tools (Tasker,
 * Automate, adb) can drive the dock with the SMARTDOCK_AUTOMATION action.
 *
 * The receiver is guarded by the signature-level SMARTDOCK_AUTOMATION
 * permission, so only apps signed with the same key (or granted the
 * permission by the user/adb) can trigger it. When the dock service is
 * alive the action is forwarded to it; when it is not, the app-menu and
 * launch requests are dropped (the dock is not there to act on them)
 * instead of crashing anything.
 */
class AutomationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_AUTOMATION) return
        // The dock service must be connected to act on either request; a
        // dropped request is silent on purpose (automation tools do not
        // read toasts).
        if (!DockService.isRunning) return
        context.sendBroadcast(
            Intent(cu.axel.smartdock.activities.LAUNCHER_ACTION)
                .setPackage(context.packageName)
                .putExtra("action", intent.getStringExtra("action"))
                .putExtra("app", intent.getStringExtra("app"))
                .putExtra("mode", intent.getStringExtra("mode"))
        )
    }
}

const val ACTION_AUTOMATION = "cu.axel.smartdock.AUTOMATION"
