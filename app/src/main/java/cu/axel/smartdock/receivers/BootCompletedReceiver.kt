package cu.axel.smartdock.receivers

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.Manifest
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cu.axel.smartdock.R
import cu.axel.smartdock.activities.MainActivity
import cu.axel.smartdock.services.DockService
import cu.axel.smartdock.utils.DeviceUtils

/**
 * Heals the dock after a reboot.
 *
 * Android binds the accessibility service on its own at boot, but an OS-level
 * crash-reboot can leave the service enabled in settings while the system
 * never re-binds it: the dock stays dead until the user toggles accessibility
 * off and on by hand (#268).
 *
 * BOOT_COMPLETED does nothing on a normal boot beyond scheduling one check:
 * if by then the service is still not running despite being enabled in
 * settings, the receiver drops and re-adds it in the enabled services setting
 * — the same fix as the manual toggle — so the system binds a fresh instance.
 * Without permission to write the setting it can only point the user at it.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    companion object {
        private const val ACTION_CHECK_DOCK = "cu.axel.smartdock.action.CHECK_DOCK"
        private const val ACTION_REBIND_DOCK = "cu.axel.smartdock.action.REBIND_DOCK"

        // Time for the system to bind the service on its own after boot, and
        // time to stay disabled before being re-added (too short and the
        // system may coalesce the two setting writes into a no-op).
        private const val CHECK_DELAY_MS = 30_000L
        private const val REBIND_DELAY_MS = 2_000L

        private const val CHANNEL_ID = "dock_recovery"
        private const val NOTIFICATION_ID = 1003
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                // Only care when the user relies on the dock
                if (DeviceUtils.isServiceListed(context))
                    schedule(context, CHECK_DELAY_MS, ACTION_CHECK_DOCK)
            }

            ACTION_CHECK_DOCK -> {
                // Normal boot: the system bound the service on its own
                if (DockService.isRunning || !DeviceUtils.isServiceListed(context)) return

                if (DeviceUtils.hasWriteSettingsPermission(context)) {
                    // Drop the dead binding; re-add it shortly after
                    DeviceUtils.disableService(context)
                    schedule(context, REBIND_DELAY_MS, ACTION_REBIND_DOCK)
                } else {
                    notifyUser(context)
                }
            }

            ACTION_REBIND_DOCK -> {
                // The user may have intervened in the meantime; never
                // re-enable a service they just turned off themselves.
                if (DockService.isRunning || !DeviceUtils.hasWriteSettingsPermission(context))
                    return
                DeviceUtils.enableService(context)
            }
        }
    }

    private fun schedule(context: Context, delayMs: Long, action: String) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, BootCompletedReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + delayMs,
            pendingIntent
        )
    }

    private fun notifyUser(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.dock_recovery_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_info)
            .setContentTitle(context.getString(R.string.dock_recovery_title))
            .setContentText(context.getString(R.string.dock_recovery_text))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.dock_recovery_text))
            )
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }
}
