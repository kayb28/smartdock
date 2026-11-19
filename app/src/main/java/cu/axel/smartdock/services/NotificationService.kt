package cu.axel.smartdock.services

import android.annotation.SuppressLint
import android.app.Notification
import android.content.Intent
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import cu.axel.smartdock.INotificationCallback
import cu.axel.smartdock.INotificationServiceBridge

const val NOTIFICATION_SERVICE_CONNECTED = "notification_service_connected"
const val NOTIFICATION_SERVICE_ACTION = "notification_service_action"

class NotificationService : NotificationListenerService() {

    private var notificationsCallback: INotificationCallback? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        sendBroadcast(
            Intent(NOTIFICATION_SERVICE_ACTION)
                .setPackage(packageName)
                .putExtra("action", NOTIFICATION_SERVICE_CONNECTED)
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        notificationsCallback?.onNotificationRemoved(sbn)
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        notificationsCallback?.onNotificationPosted(sbn)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return if (intent?.action == ACTION_BIND_NOTIFICATION_SERVICE) binder else super.onBind(
            intent
        )
    }

    private val binder = object : INotificationServiceBridge.Stub() {
        override fun getNotifications(): List<StatusBarNotification?>? {
            return activeNotifications.toList()
        }

        override fun getNotificationCount(): Int {
            var count = 0
            var cancelableCount = 0
            val notifications = activeNotifications
            for (notification in notifications) {
                if (notification != null && notification.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0) {
                    count++
                    if (notification.isClearable) cancelableCount++
                }
            }
            return count
        }

        override fun mCancelNotification(key: String?) {
            cancelNotification(key)
        }

        override fun cancelAll() {
            cancelAllNotifications()
        }

        override fun registerCallback(callback: INotificationCallback?) {
            notificationsCallback = callback
        }

    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
