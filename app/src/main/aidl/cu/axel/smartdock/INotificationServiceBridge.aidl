package cu.axel.smartdock;

import cu.axel.smartdock.INotificationCallback;
import android.service.notification.StatusBarNotification;

interface INotificationServiceBridge {
    List<StatusBarNotification> getNotifications();
    int getNotificationCount();
    void mCancelNotification(String key);
    void cancelAll();
    void registerCallback(INotificationCallback callback);
}