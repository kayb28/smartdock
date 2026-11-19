package cu.axel.smartdock.components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Notification
import android.app.PendingIntent.CanceledException
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit
import cu.axel.smartdock.R
import cu.axel.smartdock.utils.AppUtils
import cu.axel.smartdock.utils.ColorUtils
import cu.axel.smartdock.utils.DeviceUtils
import cu.axel.smartdock.utils.Utils

class NotificationLayout(
    private val context: Context,
    private val sharedPreferences: SharedPreferences,
    val listener: NotificationCancelListener
) {
    lateinit var notificationLayout: LinearLayout
    private lateinit var notificationTitleTv: TextView
    private lateinit var notificationTextTv: TextView
    private lateinit var notificationIconIv: ImageView
    private lateinit var notificationCloseBtn: ImageView
    private lateinit var notificationActionsLayout: LinearLayout
    lateinit var notificationLayoutParams: WindowManager.LayoutParams
    private var handler = Handler(Looper.getMainLooper())
    var actionsHeight = 0
    private var dockHeight: Int = 0
    private val packageManager = context.packageManager

    init {
        createNotificationLayout()
    }

    interface NotificationCancelListener {
        fun onNotificationCancel(key: String)
    }

    fun updateNotificationLayout(sbn: StatusBarNotification) {
        if (sharedPreferences.getBoolean("show_notifications", true)) {
            val notification = sbn.notification
            if ((sbn.isOngoing && !sharedPreferences.getBoolean(
                    "show_ongoing",
                    false
                )) || (sbn.packageName == AppUtils.currentApp && sharedPreferences.getBoolean(
                    "silence_current",
                    true
                )) || notification.contentView != null || isBlackListed(sbn.packageName)
            )
                return
            val extras = notification.extras
            var notificationTitle = extras.getString(Notification.EXTRA_TITLE)
            if (notificationTitle == null) notificationTitle =
                AppUtils.getPackageLabel(context, sbn.packageName)
            val notificationText = extras.getCharSequence(Notification.EXTRA_TEXT)
            ColorUtils.applyMainColor(
                context,
                sharedPreferences,
                notificationLayout
            )

            if (AppUtils.isMediaNotification(notification) && notification.getLargeIcon() != null) {
                val padding = Utils.dpToPx(context, 0)
                notificationIconIv.setPadding(padding, padding, padding, padding)
                notificationIconIv.setImageIcon(notification.getLargeIcon())
                notificationIconIv.background = null
            } else {
                notification.smallIcon.setTint(Color.WHITE)
                notificationIconIv.setBackgroundResource(R.drawable.circle)
                ColorUtils.applySecondaryColor(
                    context, sharedPreferences,
                    notificationIconIv
                )
                val padding = Utils.dpToPx(context, 14)
                notificationIconIv.setPadding(padding, padding, padding, padding)
                notificationIconIv.setImageIcon(notification.smallIcon)
            }

            val progress = extras.getInt(Notification.EXTRA_PROGRESS)
            val p = if (progress != 0) " $progress%" else ""
            notificationTitleTv.text = notificationTitle + p
            notificationTextTv.text = notificationText
            val actions = notification.actions
            notificationActionsLayout.removeAllViews()
            if (actions != null) {
                val actionLayoutParams = LinearLayout.LayoutParams(0, actionsHeight)
                actionLayoutParams.weight = 1f
                if (AppUtils.isMediaNotification(notification)) {
                    for (action in actions) {
                        val actionIv = ImageView(context)
                        try {
                            val resources = packageManager
                                .getResourcesForApplication(sbn.packageName)
                            val drawable = resources.getDrawable(
                                resources.getIdentifier(
                                    action.icon.toString() + "",
                                    "drawable",
                                    sbn.packageName
                                )
                            )
                            drawable.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP)
                            actionIv.setImageDrawable(drawable)
                            actionIv.setOnClickListener {
                                try {
                                    action.actionIntent.send()
                                } catch (_: CanceledException) {
                                }
                            }
                            notificationTextTv.isSingleLine = true
                            notificationActionsLayout.addView(actionIv, actionLayoutParams)
                        } catch (_: PackageManager.NameNotFoundException) {
                        }
                    }
                } else {
                    for (action in actions) {
                        val actionTv = TextView(context)
                        actionTv.isSingleLine = true
                        actionTv.text = action.title
                        actionTv.setTextColor(context.getColor(R.color.action))
                        actionTv.setOnClickListener {
                            try {
                                action.actionIntent.send()
                                notificationLayout.visibility = View.GONE
                                notificationLayout.alpha = 0f
                            } catch (_: CanceledException) {
                            }
                        }
                        notificationActionsLayout.addView(actionTv, actionLayoutParams)
                    }
                }
            }
            notificationCloseBtn.setOnClickListener {
                notificationLayout.visibility = View.GONE
                if (sbn.isClearable)
                    listener.onNotificationCancel(sbn.key)
            }
            notificationLayout.setOnClickListener {
                notificationLayout.visibility = View.GONE
                notificationLayout.alpha = 0f
                val intent = notification.contentIntent
                if (intent != null) {
                    try {
                        intent.send()
                        if (sbn.isClearable)
                            listener.onNotificationCancel(sbn.key)
                    } catch (_: CanceledException) {
                    }
                }
            }
            notificationLayout.setOnLongClickListener {
                val savedApps = sharedPreferences.getStringSet(
                    "ignored_notifications_popups",
                    setOf()
                )!!
                val ignoredApps = mutableSetOf<String>()
                ignoredApps.addAll(savedApps)
                ignoredApps.add(sbn.packageName)

                sharedPreferences.edit {
                    putStringSet("ignored_notifications_popups", ignoredApps)
                }
                notificationLayout.visibility = View.GONE
                notificationLayout.alpha = 0f
                Toast.makeText(
                    context,
                    R.string.silenced_notifications,
                    Toast.LENGTH_LONG
                )
                    .show()
                if (sbn.isClearable)
                    listener.onNotificationCancel(sbn.key)
                true
            }
            notificationLayout.animate().alpha(1f).setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator) {
                        notificationLayout.visibility = View.VISIBLE
                    }
                })
            if (sharedPreferences.getBoolean(
                    "enable_notification_sound",
                    false
                )
            ) DeviceUtils.playEventSound(context, "notification_sound")
            hideNotification()
        }
    }

    private fun createNotificationLayout() {
        dockHeight =
            Utils.dpToPx(context, sharedPreferences.getString("dock_height", "56")!!.toInt())
        notificationLayoutParams = Utils.makeWindowParams(
            Utils.dpToPx(context, 300), LinearLayout.LayoutParams.WRAP_CONTENT, context,
            sharedPreferences.getBoolean("prefer_last_display", false)
        )
        actionsHeight = Utils.dpToPx(context, 20)
        val margins = Utils.dpToPx(context, 2)
        notificationLayoutParams.x = margins
        notificationLayoutParams.y = margins + dockHeight
        notificationLayoutParams.gravity = Gravity.BOTTOM or if (sharedPreferences.getInt(
                "dock_layout",
                -1
            ) == 0
        ) Gravity.CENTER_HORIZONTAL else Gravity.END
        notificationLayout = LayoutInflater.from(context).inflate(
            R.layout.notification_entry,
            null
        ) as LinearLayout
        val padding = Utils.dpToPx(context, 10)
        notificationLayout.setPadding(padding, padding, padding, padding)
        notificationLayout.setBackgroundResource(R.drawable.round_square)
        notificationLayout.visibility = View.GONE
        notificationTitleTv = notificationLayout.findViewById(R.id.notification_title_tv)
        notificationTextTv = notificationLayout.findViewById(R.id.notification_text_tv)
        notificationIconIv = notificationLayout.findViewById(R.id.notification_icon_iv)
        notificationCloseBtn = notificationLayout.findViewById(R.id.notification_close_btn)
        notificationCloseBtn.alpha = 1f
        notificationActionsLayout =
            notificationLayout.findViewById(R.id.notification_actions_layout)
        notificationLayout.alpha = 0f
        notificationLayout.setOnHoverListener { _, event ->
            if (event.action == MotionEvent.ACTION_HOVER_ENTER) {
                handler.removeCallbacksAndMessages(null)
            } else if (event.action == MotionEvent.ACTION_HOVER_EXIT) {
                hideNotification()
            }
            false
        }
    }

    private fun hideNotification() {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            notificationLayout.animate().alpha(0f).setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        notificationLayout.visibility = View.GONE
                    }
                })
        }, sharedPreferences.getString("notification_display_time", "5")!!.toInt() * 1000L)
    }

    private fun isBlackListed(packageName: String): Boolean {
        val ignoredPackages =
            sharedPreferences.getStringSet("ignored_notifications_popups", setOf("android"))
        return ignoredPackages!!.contains(packageName)
    }
}