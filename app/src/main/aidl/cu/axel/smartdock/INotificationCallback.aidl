package cu.axel.smartdock;

import android.service.notification.StatusBarNotification;

interface INotificationCallback {
    void onNotificationPosted(in StatusBarNotification sbn);
    void onNotificationRemoved(in StatusBarNotification sbn);
}