package cu.axel.smartdock.services

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.PendingIntent.CanceledException
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.ActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.res.Configuration
import android.graphics.PorterDuff
import android.hardware.display.DisplayManager
import android.hardware.usb.UsbManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Display
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.core.widget.addTextChangedListener
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import cu.axel.smartdock.INotificationCallback
import cu.axel.smartdock.INotificationServiceBridge
import cu.axel.smartdock.R
import cu.axel.smartdock.activities.LAUNCHER_ACTION
import cu.axel.smartdock.activities.LAUNCHER_RESUMED
import cu.axel.smartdock.activities.MainActivity
import cu.axel.smartdock.adapters.AppActionsAdapter
import cu.axel.smartdock.adapters.AppAdapter
import cu.axel.smartdock.adapters.AppAdapter.OnAppClickListener
import cu.axel.smartdock.adapters.AppShortcutAdapter
import cu.axel.smartdock.adapters.AppTaskAdapter
import cu.axel.smartdock.adapters.DisplaysAdapter
import cu.axel.smartdock.adapters.DockAppAdapter
import cu.axel.smartdock.adapters.DockAppAdapter.OnDockAppClickListener
import cu.axel.smartdock.adapters.NotificationAdapter
import cu.axel.smartdock.adapters.NotificationAdapter.OnNotificationClickListener
import cu.axel.smartdock.components.NotificationLayout
import cu.axel.smartdock.db.DBHelper
import cu.axel.smartdock.dialogs.DockDialog
import cu.axel.smartdock.dialogs.DockLayoutDialog
import cu.axel.smartdock.dialogs.NotificationPermissionDialog
import cu.axel.smartdock.models.Action
import cu.axel.smartdock.models.App
import cu.axel.smartdock.models.AppTask
import cu.axel.smartdock.models.DockApp
import cu.axel.smartdock.models.WINDOWING_MODE_FREEFORM
import cu.axel.smartdock.preferences.NAV_LONG_ACTIONS
import cu.axel.smartdock.receivers.BatteryStatsReceiver
import cu.axel.smartdock.receivers.SoundEventsReceiver
import cu.axel.smartdock.utils.AppUtils
import cu.axel.smartdock.utils.ColorUtils
import cu.axel.smartdock.utils.ColorUtils.getMainColors
import cu.axel.smartdock.utils.DeepShortcutManager
import cu.axel.smartdock.utils.DeviceUtils
import cu.axel.smartdock.utils.IconPackUtils
import cu.axel.smartdock.utils.OnSwipeListener
import cu.axel.smartdock.utils.Utils
import cu.axel.smartdock.widgets.HoverInterceptorLayout
import cu.axel.smartdock.wrappers.ActivityManagerWrapper
import cu.axel.smartdock.wrappers.BluetoothManagerWrapper
import cu.axel.smartdock.wrappers.WifiManagerWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.UnsupportedEncodingException
import java.net.URLEncoder

const val DOCK_SERVICE_CONNECTED = "service_connected"
const val ACTION_BIND_NOTIFICATION_SERVICE = "bind_notification_service"
const val ACTION_LAUNCH_APP = "launch_app"
const val ACTION_OPEN_MENU = "open_menu"
const val DESKTOP_APP_PINNED = "desktop_app_pinned"
const val DOCK_SERVICE_ACTION = "dock_service_action"

class DockService : AccessibilityService(), OnSharedPreferenceChangeListener, OnTouchListener,
    OnAppClickListener, OnDockAppClickListener, OnNotificationClickListener {

    companion object {
        /**
         * Whether the service is currently connected: set as soon as
         * [onServiceConnected] runs and cleared in [onDestroy]. Distinguishes
         * "the system still has to bind the service" from "the system lost
         * the binding" — the state an OS crash-reboot can leave behind even
         * though the service stays enabled in settings (#268).
         */
        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private var bluetoothButton: ImageView? = null
    private var wifiButton: ImageView? = null
    private var notificationsLv: RecyclerView? = null
    private var wifiManagerWrapper: WifiManagerWrapper? = null
    private var bluetoothManagerWrapper: BluetoothManagerWrapper? = null
    private var activityManagerWrapper: ActivityManagerWrapper? = null
    private var orientationValue: String = ""
    private var orientation = -1
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var activityManager: ActivityManager
    private lateinit var appsBtn: ImageView
    private lateinit var backBtn: ImageView
    private lateinit var homeBtn: ImageView
    private lateinit var recentBtn: ImageView
    private lateinit var assistBtn: ImageView
    private lateinit var powerBtn: ImageView
    private lateinit var bluetoothBtn: ImageView
    private lateinit var wifiBtn: ImageView
    private lateinit var batteryBtn: TextView
    private lateinit var volumeBtn: ImageView
    private lateinit var pinBtn: ImageView
    private lateinit var notificationBtn: TextView
    private lateinit var searchTv: TextView
    private var topRightCorner: Button? = null
    private var bottomRightCorner: Button? = null
    private var dockHandle: Button? = null
    private var appMenu: LinearLayout? = null
    private lateinit var searchLayout: LinearLayout
    private var powerMenu: LinearLayout? = null
    private var quickSettingsPanel: LinearLayout? = null
    private lateinit var searchEntry: LinearLayout
    private lateinit var dockLayout: RelativeLayout
    private lateinit var windowManager: WindowManager
    private lateinit var appsSeparator: View
    private var appMenuVisible = false
    private var powerMenuVisible = false
    private var isPinned = false
    private var quickSettingsPanelVisible = false

    // Whether Meta is currently held, and whether any other key went down
    // during the current Meta press — releasing Meta after a Meta+X shortcut
    // must not open the app menu (#267).
    private var metaLeftDown = false
    private var metaUsedAsModifier = false
    private var systemApp = false
    private var preferSecondaryDisplay = false
    private lateinit var dockLayoutParams: WindowManager.LayoutParams
    private lateinit var searchEt: EditText
    private lateinit var tasksGv: RecyclerView
    private lateinit var favoritesGv: RecyclerView
    private lateinit var appsGv: RecyclerView
    private lateinit var wifiManager: WifiManager
    private lateinit var batteryReceiver: BatteryStatsReceiver
    private lateinit var soundEventsReceiver: SoundEventsReceiver
    private lateinit var gestureDetector: GestureDetector
    private lateinit var db: DBHelper
    private lateinit var dockHandler: Handler
    private var dock: HoverInterceptorLayout? = null
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var pinnedApps: ArrayList<App>
    private lateinit var dateTv: TextClock
    private var maxApps = 0
    private var maxAppsLandscape = 0
    private lateinit var context: Context
    private lateinit var tasks: ArrayList<AppTask>
    private var lastUpdate: Long = 0
    private var dockHeight: Int = 0
    private lateinit var handleLayoutParams: WindowManager.LayoutParams
    private lateinit var launcherApps: LauncherApps
    private lateinit var displayManager: DisplayManager
    private lateinit var displayListener: DisplayManager.DisplayListener
    private lateinit var keyguardManager: KeyguardManager
    private var iconPackUtils: IconPackUtils? = null
    private var notificationBridge: INotificationServiceBridge? = null
    private lateinit var connectivityManager: ConnectivityManager
    private var notificationLayout: NotificationLayout? = null
    private lateinit var statusArea: LinearLayout
    override fun onCreate() {
        super.onCreate()
        db = DBHelper(this)
        activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        launcherApps = getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
        dockHandler = Handler(Looper.getMainLooper())
        if (sharedPreferences.getString("icon_pack", "")!!.isNotEmpty()) {
            iconPackUtils = IconPackUtils(this)
        }
        //Shizuku stuff
        initWrappers()
        Shizuku.addBinderReceivedListener {
            initWrappers()
        }
        Shizuku.addBinderDeadListener {
            destroyWrappers()
        }
    }

    private fun destroyWrappers() {
        activityManagerWrapper = null
        wifiManagerWrapper = null
        bluetoothManagerWrapper = null
    }

    private fun initWrappers() {
        if (DeviceUtils.hasShizukuPermission()) {
            try {
                activityManagerWrapper = ActivityManagerWrapper()
                wifiManagerWrapper = WifiManagerWrapper()
                bluetoothManagerWrapper = BluetoothManagerWrapper()
            } catch (e: Exception) {
                Log.e(packageName, e.toString())
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        Utils.startupTime = System.currentTimeMillis()
        systemApp = AppUtils.isSystemApp(this, packageName)
        maxApps = sharedPreferences.getString("max_running_apps", "10")!!.toInt()
        maxAppsLandscape = sharedPreferences.getString("max_running_apps_landscape", "10")!!.toInt()
        preferSecondaryDisplay = sharedPreferences.getBoolean("prefer_last_display", false)
        createViews()

        //Listen for launcher messages
        ContextCompat.registerReceiver(this, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.getStringExtra("action")) {
                    LAUNCHER_RESUMED -> pinDock()
                    ACTION_LAUNCH_APP -> launchApp(
                        intent.getStringExtra("mode"),
                        intent.getStringExtra("app")!!
                    )
                    ACTION_OPEN_MENU -> toggleAppMenu()
                }
            }
        }, IntentFilter(LAUNCHER_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)

        //Tell the launcher the service has connected
        sendBroadcast(
            Intent(DOCK_SERVICE_ACTION)
                .setPackage(packageName)
                .putExtra("action", DOCK_SERVICE_CONNECTED)
        )

        //Register receivers
        ContextCompat.registerReceiver(
            this, object : BroadcastReceiver() {
                override fun onReceive(p1: Context, intent: Intent) {
                    when (intent.getStringExtra("action")) {
                        NOTIFICATION_SERVICE_CONNECTED -> {
                            bindNotificationService()
                        }
                    }
                }
            }, IntentFilter(NOTIFICATION_SERVICE_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        soundEventsReceiver = SoundEventsReceiver()
        val soundEventsFilter = IntentFilter()
        soundEventsFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        soundEventsFilter.addAction(Intent.ACTION_POWER_CONNECTED)
        ContextCompat.registerReceiver(
            this,
            soundEventsReceiver,
            soundEventsFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ContextCompat.registerReceiver(
            this, object : BroadcastReceiver() {
                override fun onReceive(p1: Context, intent: Intent) {
                    applyTheme()
                }
            }, IntentFilter(Intent.ACTION_WALLPAPER_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val filter = IntentFilter(Intent.ACTION_PACKAGE_FULLY_REMOVED)
        filter.addDataScheme("package")
        ContextCompat.registerReceiver(this, object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {
                loadPinnedApps()
                updateRunningTasks()
            }
        }, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {
                Log.e(packageName, "Display added $displayId")
                if (preferSecondaryDisplay)
                    restartUI()
            }

            override fun onDisplayRemoved(displayId: Int) {
                Log.e(packageName, "Display removed $displayId")
                if (preferSecondaryDisplay) {
                    restartUI()
                }
            }

            override fun onDisplayChanged(displayId: Int) {
            }
        }

        displayManager.registerDisplayListener(displayListener, null)

        batteryReceiver = BatteryStatsReceiver(
            context,
            batteryBtn,
            sharedPreferences.getBoolean("show_battery_level$orientationValue", false)
        )
        updateBatteryBtn()
        ContextCompat.registerReceiver(
            this, batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        ContextCompat.registerReceiver(this, object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {
                updateWiFiStatus()
            }

        }, IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)

        ContextCompat.registerReceiver(this, object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {
                updateBluetoothStatus()
            }

        }, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)

        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder().addTransportType(
                NetworkCapabilities.TRANSPORT_WIFI
            ).build(), wifiNetworkCallback
        )


        bindNotificationService()

        //Play startup sound
        DeviceUtils.playEventSound(this, "startup_sound")

        //Show the dock
        if (sharedPreferences.getBoolean("pin_dock", true))
            pinDock()
        else
            Toast.makeText(context, R.string.start_message, Toast.LENGTH_LONG).show()

    }

    private fun getAppActions(app: App): ArrayList<Action> {
        val actions = ArrayList<Action>()
        if (DeepShortcutManager.hasHostPermission(context)) {
            if (!DeepShortcutManager.getShortcuts(app.packageName, context).isNullOrEmpty())
                actions.add(Action(R.drawable.ic_shortcuts, getString(R.string.shortcuts)))
        }
        actions.add(Action(R.drawable.ic_manage, getString(R.string.manage)))
        actions.add(Action(R.drawable.ic_launch_mode, getString(R.string.open_as)))
        if (DeviceUtils.getDisplays(this).size > 1)
            actions.add(Action(R.drawable.ic_add_to_desktop, getString(R.string.launch_in)))
        if (AppUtils.isPinned(context, app, AppUtils.PINNED_LIST))
            actions.add(Action(R.drawable.ic_remove_favorite, getString(R.string.remove)))
        if (getPinActions(app).isNotEmpty())
            actions.add(Action(R.drawable.ic_pin, getString(R.string.add_to)))

        return actions
    }

    private fun getDockAppActions(app: DockApp): ArrayList<Action> {
        val actions = ArrayList<Action>()
        if (AppUtils.isPinned(this, app, AppUtils.DOCK_PINNED_LIST)) {
            actions.add(Action(R.drawable.ic_unpin, getString(R.string.unpin)))
            actions.add(Action(R.drawable.ic_arrow_right, getString(R.string.move)))
        } else
            actions.add(Action(R.drawable.ic_pin, getString(R.string.pin)))

        if (app.tasks.isNotEmpty() && app.tasks[0].id != -1 && DeviceUtils.hasShizukuPermission()) {
            if (app.tasks[0].windowingMode == WINDOWING_MODE_FREEFORM) {
                actions.add(Action(R.drawable.ic_launch_mode, getString(R.string.resize)))
                actions.add(Action(R.drawable.ic_snap_top, getString(R.string.snap)))
            }
            actions.add(Action(R.drawable.ic_close, getString(R.string.close)))
        }
        return actions
    }

    private fun getPinActions(app: App): ArrayList<Action> {
        val actions = ArrayList<Action>()
        if (!AppUtils.isPinned(context, app, AppUtils.PINNED_LIST))
            actions.add(Action(R.drawable.ic_add_favorite, getString(R.string.favorites)))
        if (!AppUtils.isPinned(context, app, AppUtils.DESKTOP_LIST))
            actions.add(Action(R.drawable.ic_add_to_desktop, getString(R.string.desktop)))
        if (!AppUtils.isPinned(context, app, AppUtils.DOCK_PINNED_LIST))
            actions.add(Action(R.drawable.ic_pin, getString(R.string.dock)))

        return actions
    }

    override fun onDockAppClicked(app: DockApp, anchor: View) {
        val tasks = app.tasks
        if (tasks.size == 1) {
            val taskId = tasks[0].id
            if (taskId == -1)
                launchApp(null, app.packageName)
            else
                activityManager.moveTaskToFront(taskId, 0)
        } else if (tasks.size > 1) {
            val view = LayoutInflater.from(context).inflate(R.layout.task_list, null)
            val layoutParams = Utils.makeWindowParams(-2, -2, context, preferSecondaryDisplay)
            ColorUtils.applyMainColor(context, sharedPreferences, view)
            layoutParams.gravity = Gravity.BOTTOM or Gravity.START
            layoutParams.flags = (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
            layoutParams.y = Utils.dpToPx(context, 2) + dockHeight
            val location = IntArray(2)
            anchor.getLocationOnScreen(location)
            layoutParams.x = location[0]
            view.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    windowManager.removeView(view)
                }
                false
            }
            val tasksLv = view.findViewById<ListView>(R.id.tasks_lv)
            tasksLv.adapter = AppTaskAdapter(context, tasks)
            tasksLv.setOnItemClickListener { adapterView, _, position, _ ->
                activityManager.moveTaskToFront(
                    (adapterView.getItemAtPosition(position) as AppTask).id, 0
                )
                windowManager.removeView(view)
            }
            windowManager.addView(view, layoutParams)
        } else launchApp(getDefaultLaunchMode(app.packageName), app.packageName)
        if (getDefaultLaunchMode(app.packageName) == "fullscreen") {
            if (isPinned && sharedPreferences.getBoolean("auto_unpin", true)) {
                unpinDock()
            }
        } else {
            if (!isPinned && sharedPreferences.getBoolean("auto_pin", true)) {
                pinDock()
            }
        }
    }

    override fun onDockAppLongClicked(app: DockApp, view: View) {
        showDockAppContextMenu(app, view)
    }

    override fun onAppClicked(app: App, item: View) {
        if (app.packageName == "$packageName.calc") {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("results", app.name))
            Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
        } else launchApp(null, app.packageName, null, app)
    }

    override fun onAppLongClicked(app: App, view: View) {
        if (app.packageName != "$packageName.calc") {
            showAppContextMenu(app, view)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isPinned)
            return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            if (event.windowChanges.and(AccessibilityEvent.WINDOWS_CHANGE_REMOVED) == AccessibilityEvent.WINDOWS_CHANGE_REMOVED ||
                event.windowChanges.and(
                    AccessibilityEvent.WINDOWS_CHANGE_ADDED
                ) == AccessibilityEvent.WINDOWS_CHANGE_ADDED
            )
                updateRunningTasks()
        } else if (sharedPreferences.getBoolean(
                "custom_toasts",
                false
            ) && event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED && event.parcelableData !is Notification && event.text.isNotEmpty()
        ) {
            val text = event.text[0].toString()
            val app = event.packageName.toString()
            showToast(app, text)
        }
    }

    private fun showToast(app: String, text: String) {
        val layoutParams = Utils.makeWindowParams(-2, -2, context, preferSecondaryDisplay)
        layoutParams.gravity = Gravity.BOTTOM or Gravity.CENTER
        layoutParams.y = dock!!.measuredHeight + Utils.dpToPx(context, 4)
        val toast = LayoutInflater.from(context).inflate(R.layout.toast, null)
        ColorUtils.applyMainColor(context, sharedPreferences, toast)
        val textTv = toast.findViewById<TextView>(R.id.toast_tv)
        val iconIv = toast.findViewById<ImageView>(R.id.toast_iv)
        textTv.text = text
        val notificationIcon = AppUtils.getAppIcon(context, app)
        iconIv.setImageDrawable(notificationIcon)
        ColorUtils.applyColor(iconIv, ColorUtils.getDrawableDominantColor(notificationIcon))
        toast.alpha = 0f
        toast.animate().alpha(1f).setDuration(250).interpolator = AccelerateDecelerateInterpolator()
        Handler(Looper.getMainLooper()).postDelayed({
            toast.animate().alpha(0f).setDuration(400)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        windowManager.removeView(toast)
                    }
                })
        }, 5000)
        windowManager.addView(toast, layoutParams)
    }

    override fun onInterrupt() {}

    //Handle keyboard shortcuts
    override fun onKeyEvent(event: KeyEvent): Boolean {
        // Track Meta presses to distinguish "Meta pressed alone" (open the
        // app menu) from "Meta held while other keys were used" (a modifier:
        // its release must do nothing). Without this, every Meta+X shortcut
        // was followed by the app menu opening on Meta's release (#267).
        if (event.keyCode == KeyEvent.KEYCODE_META_LEFT) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                metaLeftDown = true
                metaUsedAsModifier = false
            } else if (event.action == KeyEvent.ACTION_UP) {
                metaLeftDown = false
            }
        } else if (metaLeftDown && event.action == KeyEvent.ACTION_DOWN) {
            metaUsedAsModifier = true
        }

        if (event.action == KeyEvent.ACTION_UP) {
            if (event.isAltPressed) {
                if (event.keyCode == KeyEvent.KEYCODE_L && sharedPreferences.getBoolean(
                        "enable_lock_desktop",
                        true
                    )
                )
                    lockScreen()
                else if (event.keyCode == KeyEvent.KEYCODE_P && sharedPreferences.getBoolean(
                        "enable_open_settings",
                        true
                    )
                )
                    launchApp(
                        null, null,
                        Intent(Settings.ACTION_SETTINGS)
                    )
                else if (event.keyCode == KeyEvent.KEYCODE_T && sharedPreferences.getBoolean(
                        "enable_open_terminal",
                        false
                    )
                )
                    launchApp(null, sharedPreferences.getString("app_terminal", "com.termux")!!)
                else if (event.keyCode == KeyEvent.KEYCODE_Q && sharedPreferences.getBoolean(
                        "enable_expand_notifications",
                        true
                    )
                )
                    performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
                else if (event.keyCode == KeyEvent.KEYCODE_W && sharedPreferences.getBoolean(
                        "enable_toggle_pin",
                        true
                    )
                )
                    togglePin()
                else if (event.keyCode == KeyEvent.KEYCODE_M && sharedPreferences.getBoolean(
                        "enable_open_music",
                        true
                    )
                )
                    launchApp(null, sharedPreferences.getString("app_music", "")!!)
                else if (event.keyCode == KeyEvent.KEYCODE_B && sharedPreferences.getBoolean(
                        "enable_open_browser",
                        true
                    )
                )
                    launchApp(null, sharedPreferences.getString("app_browser", "")!!)
                else if (event.keyCode == KeyEvent.KEYCODE_A && sharedPreferences.getBoolean(
                        "enable_open_assist",
                        true
                    )
                )
                    launchApp(null, sharedPreferences.getString("app_assistant", "")!!)
                else if (event.keyCode == KeyEvent.KEYCODE_R && sharedPreferences.getBoolean(
                        "enable_open_rec",
                        true
                    )
                )
                    launchApp(null, sharedPreferences.getString("app_rec", "")!!)
                else if (event.keyCode == KeyEvent.KEYCODE_D)
                    startActivity(
                        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                else if (event.keyCode == KeyEvent.KEYCODE_O) {
                    toggleSoftKeyboard()
                } else if (event.keyCode == KeyEvent.KEYCODE_F12)
                    DeviceUtils.softReboot()
                //Window management
                else if (event.keyCode == KeyEvent.KEYCODE_F3) {
                    if (tasks.isNotEmpty()) {
                        val task = tasks[0]
                        AppUtils.resizeTask(
                            context, "portrait", task.id, dockHeight
                        )
                    }
                } else if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    if (tasks.isNotEmpty()) {
                        val task = tasks[0]
                        if (event.isShiftPressed)
                            launchApp(
                                "maximized",
                                task.packageName,
                                newInstance = true,
                                rememberMode = false
                            )
                        else
                            AppUtils.resizeTask(
                                context, "maximized", task.id, dockHeight
                            )
                    }
                } else if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (tasks.isNotEmpty()) {
                        val task = tasks[0]
                        if (event.isShiftPressed)
                            launchApp(
                                "tiled-left",
                                task.packageName,
                                newInstance = true,
                                rememberMode = false
                            )
                        else
                            AppUtils.resizeTask(
                                context, "tiled-left", task.id, dockHeight
                            )
                        return true
                    }
                } else if (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (tasks.isNotEmpty()) {
                        val task = tasks[0]
                        if (event.isShiftPressed)
                            launchApp(
                                "tiled-right",
                                task.packageName,
                                newInstance = true,
                                rememberMode = false
                            )
                        else
                            AppUtils.resizeTask(
                                context, "tiled-right", task.id, dockHeight
                            )
                        return true
                    }
                } else if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (tasks.isNotEmpty()) {
                        val task = tasks[0]
                        if (event.isShiftPressed)
                            launchApp(
                                "standard",
                                task.packageName,
                                newInstance = true,
                                rememberMode = false
                            )
                        else
                            AppUtils.resizeTask(
                                context, "standard", task.id, dockHeight
                            )
                    }
                } else if (event.isShiftPressed) {
                    val index = when (event.keyCode) {
                        KeyEvent.KEYCODE_1 -> 0
                        KeyEvent.KEYCODE_2 -> 1
                        KeyEvent.KEYCODE_3 -> 2
                        KeyEvent.KEYCODE_4 -> 3
                        KeyEvent.KEYCODE_N -> 4
                        else -> -1
                    }
                    if (index == 4 && sharedPreferences.getBoolean("enable_new_instance", true)) {
                        if (tasks.isNotEmpty()) {
                            val task = tasks[0]
                            launchApp(null, task.packageName, newInstance = true)
                        }
                    } else if (index != -1 && sharedPreferences.getBoolean("enable_tiling", true)) {
                        val displays = DeviceUtils.getDisplays(this)
                        if (tasks.isNotEmpty() && displays.size > index) {
                            val task = tasks[0]
                            launchApp(null, task.packageName, displayId = displays[index].displayId)
                        }
                    }
                }
            } else {
                if (event.keyCode == KeyEvent.KEYCODE_CTRL_RIGHT && sharedPreferences.getBoolean(
                        "enable_ctrl_back",
                        true
                    )
                ) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    return true
                } else if (event.keyCode == KeyEvent.KEYCODE_MENU && sharedPreferences.getBoolean(
                        "enable_menu_recents",
                        false
                    )
                ) {
                    performGlobalAction(GLOBAL_ACTION_RECENTS)
                    return true
                } else if (event.keyCode == KeyEvent.KEYCODE_F10 && sharedPreferences.getBoolean(
                        "enable_f10",
                        true
                    )
                ) {
                    performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
                    return true
                } else if ((event.keyCode == KeyEvent.KEYCODE_HOME || event.keyCode == KeyEvent.KEYCODE_META_LEFT) && sharedPreferences.getBoolean(
                        "enable_open_menu",
                        true
                    )
                ) {
                    if (event.keyCode == KeyEvent.KEYCODE_META_LEFT && metaUsedAsModifier) {
                        // Meta was used as a modifier (Meta+X shortcut); its
                        // release is not a menu press (#267).
                        return super.onKeyEvent(event)
                    }
                    toggleAppMenu()
                    return true
                }
            }
        }

        return super.onKeyEvent(event)
    }

    private fun toggleSoftKeyboard() {
        //TODO
        val kc = softKeyboardController
        val mode = kc.showMode
        if (mode == SHOW_MODE_AUTO || mode == SHOW_MODE_HIDDEN)
            kc.showMode = SHOW_MODE_IGNORE_HARD_KEYBOARD
        else
            kc.showMode = SHOW_MODE_HIDDEN
    }

    private fun togglePin() {
        if (isPinned) unpinDock() else pinDock()
    }

    private fun showDock() {
        //Hack to hide the dock on the lockscreen at boot
        dock!!.isVisible = !keyguardManager.isKeyguardLocked
        dockHandle!!.visibility = View.GONE

        if (dockLayoutParams.height != dockHeight) {
            dockLayoutParams.height = dockHeight
            windowManager.updateViewLayout(dock, dockLayoutParams)
        }

        dockHandler.removeCallbacksAndMessages(null)
        updateRunningTasks()
        val anim = AnimationUtils.loadAnimation(context, R.anim.slide_up)
        dockLayout.visibility = View.VISIBLE
        dockLayout.startAnimation(anim)
    }

    fun pinDock() {
        isPinned = true
        pinBtn.setImageResource(R.drawable.ic_pin)
        if (dockLayout.isGone)
            showDock()
    }

    private fun unpinDock() {
        pinBtn.setImageResource(R.drawable.ic_unpin)
        isPinned = false
        if (dockLayout.isVisible)
            hideDock(500)
    }

    private fun hideDock(delay: Int) {
        dockHandler.removeCallbacksAndMessages(null)
        dockHandler.postDelayed({
            if (!isPinned) {
                val animation = AnimationUtils.loadAnimation(context, R.anim.slide_down)
                animation.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(p1: Animation) {}
                    override fun onAnimationEnd(p1: Animation) {
                        dockLayout.visibility = View.GONE
                        val activationMethod =
                            sharedPreferences.getString("activation_method", "swipe")!!
                        if (activationMethod == "swipe") {
                            val height =
                                sharedPreferences.getString("dock_activation_area", "10")!!.toInt()
                            dockLayoutParams.height = Utils.dpToPx(context, height)
                            windowManager.updateViewLayout(dock, dockLayoutParams)
                        } else {
                            dock!!.visibility = View.GONE
                            dockHandle!!.visibility = View.VISIBLE
                        }
                        if (sharedPreferences.getBoolean("first_hide", true))
                            showFirstDockHideDialog(activationMethod)
                    }

                    override fun onAnimationRepeat(p1: Animation) {}
                })
                dockLayout.startAnimation(animation)
            }
        }, delay.toLong())
    }

    private fun getDefaultLaunchMode(app: String?): String {
        if (app == null)
            return "standard"
        val mode: String? = db.getLaunchMode(app)
        return if (sharedPreferences.getBoolean("remember_launch_mode", true) && mode != null)
            mode
        else if (AppUtils.isGame(
                packageManager,
                app
            ) && sharedPreferences.getBoolean("launch_games_fullscreen", true)
        )
            "fullscreen"
        else
            sharedPreferences.getString("launch_mode", "standard")!!
    }

    private fun launchApp(
        mode: String?,
        packageName: String?,
        intent: Intent? = null,
        app: App? = null,
        displayId: Int = Display.DEFAULT_DISPLAY,
        newInstance: Boolean = false,
        rememberMode: Boolean = true
    ) {
        var launchMode = mode
        if (launchMode == null)
            launchMode = getDefaultLaunchMode(packageName)
        else
            if (rememberMode && sharedPreferences.getBoolean(
                    "remember_launch_mode",
                    true
                ) && packageName != null
            )
                db.saveLaunchMode(packageName, launchMode)

        val options = AppUtils.makeActivityOptions(context, launchMode, dockHeight, displayId)

        //Used only for work apps
        if (app != null && app.userHandle != Process.myUserHandle())
            launcherApps.startMainActivity(
                app.componentName,
                app.userHandle,
                null,
                options.toBundle()
            )
        else {
            val launchIntent: Intent? = if (intent == null && packageName != null)
                packageManager.getLaunchIntentForPackage(packageName)
            else
                intent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            if (launchIntent == null)
                return

            if (newInstance)
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)

            context.startActivity(launchIntent, options.toBundle())
        }

        if (appMenuVisible)
            hideAppMenu()

        if (launchMode == "fullscreen" && sharedPreferences.getBoolean("auto_unpin", true)) {
            if (isPinned)
                unpinDock()
        } else {
            if (!isPinned && sharedPreferences.getBoolean("auto_pin", true))
                pinDock()
        }
        //Hack to ensure the launched app is already on the top of the stack
        dockHandler.postDelayed({ updateRunningTasks() }, 1200)

        if (quickSettingsPanelVisible)
            hideQuickSettingsPanel()
    }

    private fun setOrientation() {
        dockLayoutParams.screenOrientation =
            if (sharedPreferences.getBoolean("lock_landscape", false))
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        windowManager.updateViewLayout(dock, dockLayoutParams)
    }

    private fun toggleAppMenu() {
        if (appMenuVisible)
            hideAppMenu()
        else
            showAppMenu()
    }

    fun showAppMenu() {
        val layoutParams: WindowManager.LayoutParams?
        val displayId =
            if (preferSecondaryDisplay) DeviceUtils.getSecondaryDisplay(context).displayId else Display.DEFAULT_DISPLAY
        val deviceWidth = DeviceUtils.getDisplayBounds(context, displayId).width()
        val deviceHeight = DeviceUtils.getDisplayBounds(context, displayId).height()
        val margins = Utils.dpToPx(context, 2)
        val statusBarHeight = DeviceUtils.getStatusBarHeight(context)
        val usableHeight = deviceHeight - dockHeight - statusBarHeight - margins
        if (sharedPreferences.getBoolean("app_menu_fullscreen", false)) {
            layoutParams =
                Utils.makeWindowParams(
                    -1,
                    usableHeight + margins,
                    context,
                    preferSecondaryDisplay
                )
            layoutParams.y = dockHeight
            if (sharedPreferences.getInt("dock_layout", -1) != 0) {
                val padding = Utils.dpToPx(context, 24)
                appMenu!!.setPadding(padding, padding, padding, padding)
                searchEntry.gravity = Gravity.CENTER
                searchLayout.gravity = Gravity.CENTER
                appsGv.layoutManager = GridLayoutManager(context, 10)
                favoritesGv.layoutManager = GridLayoutManager(context, 10)
            } else {
                appsGv.layoutManager = GridLayoutManager(context, 5)
                favoritesGv.layoutManager = GridLayoutManager(context, 5)
            }
            appMenu!!.setBackgroundResource(R.drawable.rect)
        } else {
            val width = Utils.dpToPx(
                context,
                sharedPreferences.getString("app_menu_width", "650")!!.toInt()
            )
            val height = Utils.dpToPx(
                context,
                sharedPreferences.getString("app_menu_height", "540")!!.toInt()
            )
            layoutParams = Utils.makeWindowParams(
                width.coerceAtMost(deviceWidth - margins * 2), height.coerceAtMost(usableHeight),
                context, preferSecondaryDisplay
            )
            layoutParams.x = margins
            layoutParams.y = margins + dockHeight
            appsGv.layoutManager = GridLayoutManager(
                context,
                sharedPreferences.getString("num_columns", "5")!!.toInt()
            )
            favoritesGv.layoutManager = GridLayoutManager(
                context,
                sharedPreferences.getString("num_columns", "5")!!.toInt()
            )
            val padding = Utils.dpToPx(context, 10)
            appMenu!!.setPadding(padding, padding, padding, padding)
            searchEntry.gravity = Gravity.START
            searchLayout.gravity = Gravity.START
            appMenu!!.setBackgroundResource(R.drawable.round_rect)
        }
        layoutParams.flags = (WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
        val hAlign = if (sharedPreferences.getBoolean(
                "center_app_menu",
                false
            )
        ) Gravity.CENTER_HORIZONTAL else Gravity.START
        layoutParams.gravity = Gravity.BOTTOM or hAlign
        ColorUtils.applyMainColor(context, sharedPreferences, appMenu!!)
        ColorUtils.applyColor(appsSeparator, getMainColors(sharedPreferences, this)[4])

        //Load apps
        updateAppMenu()
        loadFavoriteApps()

        //Load user info
        val avatarIv = appMenu!!.findViewById<ImageView>(R.id.avatar_iv)
        val userNameTv = appMenu!!.findViewById<TextView>(R.id.user_name_tv)

        if (AppUtils.isSystemApp(context, packageName)) {
            val name = DeviceUtils.getUserName(context)
            if (name != null) userNameTv.text = name
            val icon = DeviceUtils.getUserIcon(context)
            if (icon != null) avatarIv.setImageBitmap(icon)
        } else {
            val name = sharedPreferences.getString("user_name", "")
            if (name!!.isNotEmpty()) userNameTv.text = name
            val iconUri = sharedPreferences.getString("user_icon_uri", "default")
            if (iconUri != "default") {
                val bitmap = Utils.getBitmapFromUri(context, Uri.parse(iconUri))
                val icon = Utils.getCircularBitmap(bitmap)
                if (icon != null)
                    avatarIv.setImageBitmap(icon)
            } else avatarIv.setImageResource(R.drawable.ic_user)
        }

        //Work around android showing the ime system ui bar
        val softwareKeyboard =
            context.resources.configuration.keyboard == Configuration.KEYBOARD_NOKEYS
        val tabletMode = sharedPreferences.getInt("dock_layout", -1) == 1

        searchEt.showSoftInputOnFocus = softwareKeyboard || tabletMode
        searchEt.requestFocus()

        windowManager.addView(appMenu, layoutParams)
        appMenuVisible = true
    }

    fun hideAppMenu() {
        searchEt.setText("")
        // Reset filter
        val adapter = appsGv.adapter
        if (adapter is AppAdapter) {
            adapter.filter("")
        }
        windowManager.removeView(appMenu)
        appMenuVisible = false
    }

    private suspend fun fetchInstalledApps(): ArrayList<App> = withContext(Dispatchers.Default) {
        return@withContext AppUtils.getInstalledApps(context)
    }

    private fun updateAppMenu(recreateAdapter: Boolean = false) {
        CoroutineScope(Dispatchers.Default).launch {
            val hiddenApps = sharedPreferences.getStringSet(
                "hidden_apps_grid",
                setOf()
            )!!
            val apps = fetchInstalledApps().filterNot { hiddenApps.contains(it.packageName) }

            withContext(Dispatchers.Main) {
                val menuFullscreen = sharedPreferences.getBoolean("app_menu_fullscreen", false)
                val phoneLayout = sharedPreferences.getInt("dock_layout", -1) == 0
                //TODO: Implement efficient adapter
                val existingAdapter = appsGv.adapter
                if (existingAdapter is AppAdapter && !recreateAdapter) {
                    existingAdapter.updateApps(apps)
                } else {
                    appsGv.adapter = AppAdapter(
                        context, apps, this@DockService,
                        menuFullscreen && !phoneLayout, iconPackUtils
                    )
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showDockAppContextMenu(app: DockApp, anchor: View) {
        val view = LayoutInflater.from(context).inflate(R.layout.task_list, null)
        ColorUtils.applyMainColor(context, sharedPreferences, view)
        val layoutParams = Utils.makeWindowParams(-2, -2, context, preferSecondaryDisplay)
        view.setBackgroundResource(R.drawable.round_rect)
        ColorUtils.applyMainColor(context, sharedPreferences, view)
        layoutParams.gravity = Gravity.BOTTOM or Gravity.START
        layoutParams.flags =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        layoutParams.y = Utils.dpToPx(context, 2) + dockHeight
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        layoutParams.x = location[0]
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE)
                windowManager.removeView(view)

            false
        }
        val actionsLv = view.findViewById<ListView>(R.id.tasks_lv)
        actionsLv.adapter = AppActionsAdapter(context, getDockAppActions(app))
        actionsLv.setOnItemClickListener { adapterView, _, position, _ ->
            if (adapterView.getItemAtPosition(position) is Action) {
                val action = adapterView.getItemAtPosition(position) as Action
                if (action.text == getString(R.string.pin) || action.text == getString(R.string.unpin)) {
                    if (AppUtils.isPinned(context, app, AppUtils.DOCK_PINNED_LIST))
                        AppUtils.unpinApp(
                            context,
                            app.packageName,
                            AppUtils.DOCK_PINNED_LIST
                        ) else
                        AppUtils.pinApp(context, app, AppUtils.DOCK_PINNED_LIST)
                    loadPinnedApps()
                    if (isPinned)
                        updateRunningTasks()
                    windowManager.removeView(view)
                } else if (action.text == getString(R.string.left)) {
                    AppUtils.moveApp(this, app, AppUtils.DOCK_PINNED_LIST, 0)
                    loadPinnedApps()
                    updateRunningTasks()
                } else if (action.text == getString(R.string.right)) {
                    AppUtils.moveApp(this, app, AppUtils.DOCK_PINNED_LIST, 1)
                    loadPinnedApps()
                    updateRunningTasks()
                } else if (action.text == getString(R.string.move)) {
                    val actions = ArrayList<Action>()
                    actions.add(Action(R.drawable.ic_arrow_back, ""))
                    actions.add(Action(R.drawable.ic_arrow_back, getString(R.string.left)))
                    actions.add(Action(R.drawable.ic_arrow_right, getString(R.string.right)))
                    actionsLv.adapter = AppActionsAdapter(context, actions)
                } else if (action.text == getString(R.string.resize)) {
                    val actions = ArrayList<Action>()
                    actions.add(Action(R.drawable.ic_arrow_back, ""))
                    actions.add(Action(R.drawable.ic_standard, getString(R.string.standard)))
                    actions.add(Action(R.drawable.ic_maximized, getString(R.string.maximized)))
                    actions.add(Action(R.drawable.ic_portrait, getString(R.string.portrait)))
                    actions.add(Action(R.drawable.ic_fullscreen, getString(R.string.fullscreen)))
                    actionsLv.adapter = AppActionsAdapter(context, actions)
                } else if (action.text == getString(R.string.snap)) {
                    val actions = ArrayList<Action>()
                    actions.add(Action(R.drawable.ic_arrow_back, ""))
                    actions.add(Action(R.drawable.ic_snap_top, getString(R.string.top)))
                    actions.add(Action(R.drawable.ic_snap_bottom, getString(R.string.bottom)))
                    actions.add(Action(R.drawable.ic_snap_left, getString(R.string.left)))
                    actions.add(Action(R.drawable.ic_snap_right, getString(R.string.right)))
                    actionsLv.adapter = AppActionsAdapter(context, actions)
                } else if (action.text == getString(R.string.standard)) {
                    activityManagerWrapper?.resizeTask(
                        app.tasks[0].id,
                        AppUtils.makeLaunchBounds(this, "standard", dockHeight)
                    )
                    windowManager.removeView(view)
                } else if (action.text == getString(R.string.maximized)) {
                    activityManagerWrapper?.resizeTask(
                        app.tasks[0].id,
                        AppUtils.makeLaunchBounds(this, "maximized", dockHeight)
                    )
                    windowManager.removeView(view)
                } else if (action.text == getString(R.string.portrait)) {
                    activityManagerWrapper?.resizeTask(
                        app.tasks[0].id,
                        AppUtils.makeLaunchBounds(this, "portrait", dockHeight)
                    )
                    windowManager.removeView(view)
                } else if (action.text == getString(R.string.fullscreen)) {
                    activityManagerWrapper?.resizeTask(
                        app.tasks[0].id,
                        AppUtils.makeLaunchBounds(this, "fullscreen", dockHeight)
                    )
                    windowManager.removeView(view)
                } else if (action.text == getString(R.string.top)) {
                    activityManagerWrapper?.resizeTask(
                        app.tasks[0].id,
                        AppUtils.makeLaunchBounds(this, "tiled-top", dockHeight)
                    )
                    windowManager.removeView(view)
                } else if (action.text == getString(R.string.bottom)) {
                    activityManagerWrapper?.resizeTask(
                        app.tasks[0].id,
                        AppUtils.makeLaunchBounds(this, "tiled-bottom", dockHeight)
                    )
                    windowManager.removeView(view)
                } else if (action.text == getString(R.string.close)) {
                    activityManagerWrapper?.removeTask(app.tasks[0].id)
                    windowManager.removeView(view)
                } else if (action.text.isBlank()) {
                    actionsLv.adapter = AppActionsAdapter(context, getDockAppActions(app))
                }
            }
        }
        windowManager.addView(view, layoutParams)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showAppContextMenu(app: App, anchor: View) {
        val view = LayoutInflater.from(context).inflate(R.layout.task_list, null)
        val layoutParams = Utils.makeWindowParams(-2, -2, context, preferSecondaryDisplay)
        ColorUtils.applyMainColor(context, sharedPreferences, view)
        layoutParams.gravity = Gravity.START or Gravity.TOP
        layoutParams.flags =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        layoutParams.x = location[0]
        layoutParams.y = location[1] + Utils.dpToPx(context, anchor.measuredHeight / 2)
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE)
                windowManager.removeView(view)

            false
        }
        val actionsLv = view.findViewById<ListView>(R.id.tasks_lv)
        actionsLv.adapter = AppActionsAdapter(context, getAppActions(app))
        actionsLv.setOnItemClickListener { adapterView, _, position, _ ->
            if (adapterView.getItemAtPosition(position) is Action) {
                val action = adapterView.getItemAtPosition(position) as Action
                if (action.text == getString(R.string.manage)) {
                    val actions = ArrayList<Action>()
                    actions.add(Action(R.drawable.ic_arrow_back, ""))
                    actions.add(Action(R.drawable.ic_info, getString(R.string.app_info)))
                    if (sharedPreferences.getBoolean("enable_app_hiding_grid", false))
                        actions.add(
                            Action(
                                R.drawable.ic_hide,
                                getString(R.string.hide)
                            )
                        )
                    if (!AppUtils.isSystemApp(
                            context,
                            app.packageName
                        ) || sharedPreferences.getBoolean("allow_sysapp_uninstall", false)
                    ) actions.add(Action(R.drawable.ic_uninstall, getString(R.string.uninstall)))
                    if (sharedPreferences.getBoolean("allow_app_freeze", false))
                        actions.add(
                            Action(
                                R.drawable.ic_freeze,
                                getString(R.string.freeze)
                            )
                        )
                    actionsLv.adapter = AppActionsAdapter(context, actions)
                } else if (action.text == getString(R.string.shortcuts)) {
                    actionsLv.adapter = AppShortcutAdapter(
                        context,
                        DeepShortcutManager.getShortcuts(app.packageName, context)!!
                    )
                } else if (action.text == "") {
                    actionsLv.adapter = AppActionsAdapter(context, getAppActions(app))
                } else if (action.text == getString(R.string.open_as)) {
                    val actions = ArrayList<Action>()
                    actions.add(Action(R.drawable.ic_arrow_back, ""))
                    actions.add(Action(R.drawable.ic_standard, getString(R.string.standard)))
                    actions.add(Action(R.drawable.ic_maximized, getString(R.string.maximized)))
                    actions.add(Action(R.drawable.ic_portrait, getString(R.string.portrait)))
                    actions.add(Action(R.drawable.ic_fullscreen, getString(R.string.fullscreen)))
                    actionsLv.adapter = AppActionsAdapter(context, actions)
                } else if (action.text == getString(R.string.add_to)) {
                    val actions = ArrayList<Action>()
                    actions.add(Action(R.drawable.ic_arrow_back, ""))
                    actions.addAll(getPinActions(app))
                    actionsLv.adapter = AppActionsAdapter(context, actions)
                } else if (action.text == getString(R.string.launch_in)) {
                    actionsLv.adapter = DisplaysAdapter(context, DeviceUtils.getDisplays(this))
                } else if (action.text == getString(R.string.app_info)) {
                    launchApp(
                        null, null, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData("package:${app.packageName}".toUri())
                    )
                    windowManager.removeView(view)
                } else if (action.text == getString(R.string.hide)) {
                    val savedApps = sharedPreferences.getStringSet(
                        "hidden_apps_grid",
                        setOf()
                    )!!
                    val hiddenApps = mutableSetOf<String>()
                    hiddenApps.addAll(savedApps)
                    hiddenApps.add(app.packageName)

                    sharedPreferences.edit {
                        putStringSet("hidden_apps_grid", hiddenApps)
                    }

                    if (AppUtils.isPinned(this, app, AppUtils.PINNED_LIST))
                        AppUtils.unpinApp(this, app.packageName, AppUtils.PINNED_LIST)
                    if (AppUtils.isPinned(this, app, AppUtils.DOCK_PINNED_LIST))
                        AppUtils.unpinApp(this, app.packageName, AppUtils.DOCK_PINNED_LIST)
                    if (AppUtils.isPinned(this, app, AppUtils.DESKTOP_LIST))
                        AppUtils.unpinApp(this, app.packageName, AppUtils.DESKTOP_LIST)
                    updateAppMenu()
                    loadFavoriteApps()
                    windowManager.removeView(view)
                } else if (action.text == getString(R.string.uninstall)) {
                    AppUtils.uninstallApp(this, app.packageName)
                    if (appMenuVisible)
                        hideAppMenu()
                    windowManager.removeView(view)
                } else if (action.text == getString(R.string.freeze)) {
                    val status = DeviceUtils.runAsRoot("pm disable ${app.packageName}")
                    if (status != "error") Toast.makeText(
                        context,
                        R.string.app_frozen,
                        Toast.LENGTH_SHORT
                    ).show() else Toast.makeText(
                        context,
                        R.string.something_wrong,
                        Toast.LENGTH_SHORT
                    ).show()
                    windowManager.removeView(view)
                    if (appMenuVisible) hideAppMenu()
                } else if (action.text == getString(R.string.favorites)) {
                    AppUtils.pinApp(context, app, AppUtils.PINNED_LIST)
                    windowManager.removeView(view)
                    loadFavoriteApps()
                } else if (action.text == getString(R.string.remove)) {
                    AppUtils.unpinApp(context, app.packageName, AppUtils.PINNED_LIST)
                    windowManager.removeView(view)
                    loadFavoriteApps()
                } else if (action.text == getString(R.string.desktop)) {
                    AppUtils.pinApp(context, app, AppUtils.DESKTOP_LIST)
                    sendBroadcast(
                        Intent(DOCK_SERVICE_ACTION)
                            .setPackage(packageName)
                            .putExtra("action", DESKTOP_APP_PINNED)
                    )
                    windowManager.removeView(view)
                } else if (action.text == getString(R.string.dock)) {
                    AppUtils.pinApp(context, app, AppUtils.DOCK_PINNED_LIST)
                    loadPinnedApps()
                    updateRunningTasks()
                    windowManager.removeView(view)
                } else if (action.text == getString(R.string.standard)) {
                    windowManager.removeView(view)
                    launchApp("standard", app.packageName, null, app, newInstance = true)
                } else if (action.text == getString(R.string.maximized)) {
                    windowManager.removeView(view)
                    launchApp("maximized", app.packageName, null, app, newInstance = true)
                } else if (action.text == getString(R.string.portrait)) {
                    windowManager.removeView(view)
                    launchApp("portrait", app.packageName, null, app, newInstance = true)
                } else if (action.text == getString(R.string.fullscreen)) {
                    windowManager.removeView(view)
                    launchApp("fullscreen", app.packageName, null, app, newInstance = true)
                }
            } else if (adapterView.getItemAtPosition(position) is ShortcutInfo) {
                val shortcut = adapterView.getItemAtPosition(position) as ShortcutInfo
                windowManager.removeView(view)
                DeepShortcutManager.startShortcut(shortcut, context)
            } else if (adapterView.getItemAtPosition(position) is Display) {
                val display = adapterView.getItemAtPosition(position) as Display
                windowManager.removeView(view)
                launchApp(
                    null,
                    app.packageName,
                    null,
                    app,
                    display.displayId,
                    sharedPreferences.getBoolean("launch_new_instance_secondary", true)
                )
            }
        }
        windowManager.addView(view, layoutParams)
    }

    override fun onSharedPreferenceChanged(p1: SharedPreferences, preference: String?) {
        if (preference == null)
            return
        if (preference.startsWith("theme"))
            applyTheme()
        else if (preference == "menu_icon_uri")
            updateMenuIcon()
        else if (preference.startsWith("icon_")) {
            val iconPack = sharedPreferences.getString("icon_pack", "")!!
            iconPackUtils = if (iconPack.isNotEmpty()) {
                IconPackUtils(this)
            } else
                null

            updateRunningTasks(true)
            updateAppMenu(true)
            loadFavoriteApps()
        } else if (preference == "tint_indicators") {
            updateRunningTasks(true)
        } else if (preference == "lock_landscape")
            setOrientation()
        else if (preference == "center_running_apps") {
            placeRunningApps()
            updateRunningTasks()
        } else if (preference == "dock_activation_area")
            updateDockTrigger()
        else if (preference.startsWith("enable_corner_"))
            updateCorners()
        else if (preference.startsWith("enable_nav_")) {
            updateNavigationBar()
        } else if (preference.startsWith("enable_qs_")) {
            updateQuickSettings()
        } else if (preference == "round_dock")
            updateDockShape()
        else if (preference.startsWith("max_running_apps")) {
            maxApps = sharedPreferences.getString("max_running_apps", "10")!!.toInt()
            maxAppsLandscape =
                sharedPreferences.getString("max_running_apps_landscape", "10")!!.toInt()
            updateRunningTasks()
        } else if (preference == "activation_method") {
            updateActivationMethod()
        } else if (preference == "handle_opacity")
            dockHandle!!.alpha = sharedPreferences.getString("handle_opacity", "0.5")!!.toFloat()
        else if (preference == "dock_height")
            updateDockHeight()
        else if (preference == "handle_position")
            updateHandlePosition()
        else if (preference.startsWith("show_battery_level"))
            updateBatteryBtn()
        else if (preference == "dock_background_alpha")
            applyDockAlpha()
        else if (preference == "override_dock_background_alpha")
            updateDockBackgroundColor()
        else if (preference == "prefer_last_display") {
            preferSecondaryDisplay = sharedPreferences.getBoolean("prefer_last_display", false)
            if (DeviceUtils.getDisplays(this).size > 1)
                restartUI()
        }
    }

    private fun updateDockTrigger() {
        if (!isPinned) {
            val height = sharedPreferences.getString("dock_activation_area", "10")!!.toInt()
            dockLayoutParams.height = Utils.dpToPx(context, height)
            windowManager.updateViewLayout(dock, dockLayoutParams)
        }
    }

    private fun updateActivationMethod() {
        if (!isPinned) {
            val method = sharedPreferences.getString("activation_method", "swipe")
            if (method == "swipe") {
                dockHandle!!.visibility = View.GONE
                updateDockTrigger()
                dock!!.visibility = View.VISIBLE
            } else {
                dock!!.visibility = View.GONE
                dockHandle!!.visibility = View.VISIBLE
            }
        }
    }

    private fun updateDockHeight() {
        dockHeight =
            Utils.dpToPx(context, sharedPreferences.getString("dock_height", "56")!!.toInt())
        if (isPinned) {
            dockLayoutParams.height = dockHeight
            windowManager.updateViewLayout(dock, dockLayoutParams)
        }
    }

    private fun placeRunningApps() {
        val layoutParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        )
        if (sharedPreferences.getBoolean("center_running_apps", true)) {
            layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT)
        } else {
            layoutParams.addRule(RelativeLayout.END_OF, R.id.nav_panel)
            layoutParams.addRule(RelativeLayout.START_OF, R.id.system_tray)
        }
        tasksGv.layoutParams = layoutParams
    }

    private fun loadPinnedApps() {
        pinnedApps = AppUtils.getPinnedApps(context, AppUtils.DOCK_PINNED_LIST)
    }

    private fun updateRunningTasks(recreateAdapter: Boolean = false) {
        val now = System.currentTimeMillis()
        if (now - lastUpdate < 500 && !recreateAdapter)
            return
        lastUpdate = now

        val apps = ArrayList<DockApp>()
        if (::pinnedApps.isInitialized)
            pinnedApps.forEach { pinnedApp ->
                apps.add(DockApp(pinnedApp.name, pinnedApp.packageName, pinnedApp.icon))
            }

        val gridSize = Utils.dpToPx(context, 52)

        //TODO: We can eliminate another for
        //TODO: Don't do anything if tasks has not changed
        val nApps =
            if (orientation == Configuration.ORIENTATION_PORTRAIT) maxApps else maxAppsLandscape
        if (systemApp || DeviceUtils.hasShizukuPermission()) {
            if (systemApp)
                tasks = AppUtils.getRunningTasks(activityManager, null, packageManager, nApps)
            else if (DeviceUtils.hasShizukuPermission())
                tasks = AppUtils.getRunningTasks(
                    activityManager,
                    activityManagerWrapper,
                    packageManager,
                    nApps
                )
            for (j in 1..tasks.size) {
                val task = tasks[tasks.size - j]
                val index = AppUtils.containsTask(apps, task)
                if (index != -1)
                    apps[index].addTask(task)
                else
                    apps.add(DockApp(task))
            }
        } else {
            tasks = AppUtils.getRecentTasks(context, nApps)
            tasks.reversed().forEach { task ->
                val index = AppUtils.containsTask(apps, task)
                if (index == -1)
                    apps.add(DockApp(task))
            }
        }

        tasksGv.layoutParams.width = gridSize * apps.size
        val adapter = tasksGv.adapter
        if (adapter is DockAppAdapter && !recreateAdapter)
            adapter.updateApps(apps)
        else
            tasksGv.adapter = DockAppAdapter(context, apps, this, iconPackUtils)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Everything this update touches is born in createViews(), which runs
        // from onServiceConnected(). Until then, a configuration change —
        // easy to hit when an external display is connected while the
        // service is still starting — crashed the service on the lateinit
        // properties (#270). createViews() builds from the current
        // configuration, so nothing is lost by skipping the update here.
        if (!::context.isInitialized) return

        orientation = newConfig.orientation
        orientationValue =
            if (orientation == Configuration.ORIENTATION_PORTRAIT) "" else "_landscape"
        updateRunningTasks()
        updateNavigationBar()
        updateQuickSettings()
        updateBatteryBtn()
        if (appMenuVisible)
            hideAppMenu()
    }

    private fun updateDockShape() {
        dockLayout.setBackgroundResource(
            if (sharedPreferences.getBoolean(
                    "round_dock",
                    false
                )
            ) R.drawable.round_rect else R.drawable.rect
        )
        updateDockBackgroundColor()
    }

    private fun updateNavigationBar() {
        appsBtn.visibility =
            if (sharedPreferences.getBoolean(
                    "enable_nav_apps$orientationValue",
                    true
                )
            ) View.VISIBLE else View.GONE
        backBtn.visibility =
            if (sharedPreferences.getBoolean(
                    "enable_nav_back$orientationValue",
                    true
                )
            ) View.VISIBLE else View.GONE
        homeBtn.visibility =
            if (sharedPreferences.getBoolean(
                    "enable_nav_home$orientationValue",
                    true
                )
            ) View.VISIBLE else View.GONE
        recentBtn.visibility = if (sharedPreferences.getBoolean(
                "enable_nav_recents$orientationValue",
                true
            )
        ) View.VISIBLE else View.GONE
        assistBtn.visibility = if (sharedPreferences.getBoolean(
                "enable_nav_assist$orientationValue",
                false
            )
        ) View.VISIBLE else View.GONE
    }

    private fun updateQuickSettings() {
        val notifEnabled = sharedPreferences.getBoolean("enable_qs_notif$orientationValue", true)
        notificationBtn.isVisible = notifEnabled

        val bluetoothEnabled =
            sharedPreferences.getBoolean("enable_qs_bluetooth$orientationValue", false)
        bluetoothBtn.isVisible = bluetoothEnabled

        val batteryEnabled =
            sharedPreferences.getBoolean("enable_qs_battery$orientationValue", false)
        batteryBtn.isVisible = batteryEnabled

        val wifiEnabled = sharedPreferences.getBoolean("enable_qs_wifi$orientationValue", true)
        wifiBtn.isVisible = wifiEnabled

        pinBtn.isVisible = sharedPreferences.getBoolean("enable_qs_pin$orientationValue", true)

        val volumeEnabled = sharedPreferences.getBoolean("enable_qs_vol$orientationValue", true)
        volumeBtn.isVisible = volumeEnabled

        dateTv.isVisible = sharedPreferences.getBoolean("enable_qs_date$orientationValue", true)

        val statusEnabled = bluetoothEnabled || batteryEnabled || wifiEnabled || volumeEnabled
        statusArea.isVisible = notifEnabled || statusEnabled
        statusArea.setPadding(if (statusEnabled) Utils.dpToPx(context, 4) else 0)
    }

    private fun launchAssistant() {
        val assistant = sharedPreferences.getString("app_assistant", "")
        if (assistant!!.isNotEmpty()) launchApp(null, assistant) else {
            try {
                startActivity(Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
            }
        }
    }

    private fun openBluetoothSettings() {
        launchApp(
            null, null,
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        )
    }

    private fun openWiFiSettings() {
        launchApp(
            null, null,
            Intent(Settings.ACTION_WIFI_SETTINGS)
        )
    }

    private fun showSystemWiFiPanel() {
        startActivity(Intent(Settings.Panel.ACTION_WIFI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun toggleQuickSettingsPanel(selectedTab: Int = 0) {
        if (!quickSettingsPanelVisible)
            showQuickSettingsPanel(selectedTab)
        else
            hideQuickSettingsPanel()
    }

    private fun hideQuickSettingsPanel() {
        windowManager.removeView(quickSettingsPanel)
        quickSettingsPanelVisible = false
        quickSettingsPanel = null
        notificationsLv = null
        wifiButton = null
        bluetoothButton = null
    }

    @SuppressLint("ClickableViewAccessibility", "MissingPermission")
    private fun showQuickSettingsPanel(selectedTab: Int = 0) {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val layoutParams = Utils.makeWindowParams(
            Utils.dpToPx(context, 400), Utils.dpToPx(context, 305), context,
            preferSecondaryDisplay
        )
        layoutParams.flags =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        layoutParams.y = Utils.dpToPx(context, 2) + dockHeight
        layoutParams.x = Utils.dpToPx(context, 2)
        layoutParams.gravity = Gravity.BOTTOM or Gravity.END
        quickSettingsPanel =
            LayoutInflater.from(ContextThemeWrapper(context, R.style.AppTheme_Dock))
                .inflate(R.layout.quick_settings_panel, null) as LinearLayout
        quickSettingsPanel!!.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE
                && (event.y < quickSettingsPanel!!.measuredHeight || event.x < quickSettingsPanel!!.x)
            )
                hideQuickSettingsPanel()

            false
        }
        val tabLayout = quickSettingsPanel!!.findViewById<TabLayout>(R.id.qs_tab_layout)
        val notificationArea =
            quickSettingsPanel!!.findViewById<LinearLayout>(R.id.notifications_layout)
        val quickSettingsArea =
            quickSettingsPanel!!.findViewById<LinearLayout>(R.id.quick_settings_layout)
        val cancelAllButton = quickSettingsPanel!!.findViewById<ImageView>(R.id.cancel_all_n_btn)
        cancelAllButton.setOnClickListener { notificationBridge?.cancelAll() }
        val volumeButton = quickSettingsPanel!!.findViewById<ImageView>(R.id.volume_btn)
        val volumeSeekbar = quickSettingsPanel!!.findViewById<SeekBar>(R.id.volume_seekbar)
        val brightnessButton = quickSettingsPanel!!.findViewById<ImageView>(R.id.brightness_btn)
        val brightnessSeekbar = quickSettingsPanel!!.findViewById<SeekBar>(R.id.brightness_seekbar)
        brightnessSeekbar.progress =
            DeviceUtils.getSystemSetting(this, Settings.System.SCREEN_BRIGHTNESS, "0").toInt()
        val canWriteSettings = DeviceUtils.canWriteSettings(this)
        brightnessSeekbar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(
                p0: SeekBar?,
                p1: Int,
                p2: Boolean
            ) {
                if (canWriteSettings) {
                    DeviceUtils.putSystemSetting(
                        this@DockService,
                        Settings.System.SCREEN_BRIGHTNESS,
                        p1.toString()
                    )
                } else
                    showSettingsPermissionDialog()
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {

            }

            override fun onStopTrackingTouch(p0: SeekBar?) {

            }
        })
        volumeSeekbar.max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        volumeSeekbar.progress = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        volumeSeekbar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
            }

            override fun onStartTrackingTouch(p1: SeekBar) {}
            override fun onStopTrackingTouch(p1: SeekBar) {}
        })

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(p0: TabLayout.Tab?) {
                notificationArea.isVisible = p0!!.position == 0
                cancelAllButton.isVisible = p0.position == 0
                quickSettingsArea.isVisible = p0.position == 1
            }

            override fun onTabUnselected(p0: TabLayout.Tab?) {

            }

            override fun onTabReselected(p0: TabLayout.Tab?) {

            }

        })
        tabLayout.selectTab(tabLayout.getTabAt(selectedTab))
        ColorUtils.applySecondaryColor(context, sharedPreferences, volumeButton)
        wifiButton = quickSettingsPanel!!.findViewById(R.id.wifi_btn)
        val wifiTile = quickSettingsPanel!!.findViewById<LinearLayout>(R.id.wifi_tile)
        bluetoothButton = quickSettingsPanel!!.findViewById<ImageView>(R.id.bluetooth_btn)
        val bluetoothTile = quickSettingsPanel!!.findViewById<LinearLayout>(R.id.bluetooth_tile)
        val notificationsBtn = quickSettingsPanel!!.findViewById<ImageView>(R.id.notifications_btn)
        val orientationBtn = quickSettingsPanel!!.findViewById<ImageView>(R.id.btn_orientation)
        val touchModeBtn = quickSettingsPanel!!.findViewById<ImageView>(R.id.btn_touch_mode)
        val screenshotBtn = quickSettingsPanel!!.findViewById<ImageView>(R.id.btn_screenshot)
        val screencapBtn = quickSettingsPanel!!.findViewById<ImageView>(R.id.btn_screencast)
        val settingsBtn = quickSettingsPanel!!.findViewById<ImageView>(R.id.btn_settings)
        wifiButton!!.setOnClickListener {
            if (DeviceUtils.hasShizukuPermission() && wifiManagerWrapper?.isAlive() == true)
                wifiManagerWrapper?.setWifiEnabled(!wifiManager.isWifiEnabled)
            else {
                showSystemWiFiPanel()
                hideQuickSettingsPanel()
            }
        }
        wifiTile.setOnClickListener {
            showSystemWiFiPanel()
            hideQuickSettingsPanel()
        }

        bluetoothButton?.setOnClickListener {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                if (!bluetoothManager.adapter.isEnabled)
                    bluetoothManager.adapter?.enable()
                else
                    bluetoothManager.adapter?.disable()
            } else {
                if (DeviceUtils.hasShizukuPermission() && bluetoothManagerWrapper?.isAlive() == true)
                    bluetoothManagerWrapper?.setBluetoothEnabled(!bluetoothManager.adapter.isEnabled)
                else {
                    openBluetoothSettings()
                }
            }
        }
        bluetoothTile.setOnClickListener {
            openBluetoothSettings()
        }

        notificationsLv = quickSettingsPanel!!.findViewById(R.id.notification_lv)

        ColorUtils.applySecondaryColor(context, sharedPreferences, wifiTile)
        ColorUtils.applySecondaryColor(context, sharedPreferences, bluetoothTile)
        ColorUtils.applySecondaryColor(context, sharedPreferences, brightnessButton)
        ColorUtils.applySecondaryColor(context, sharedPreferences, notificationsBtn)
        ColorUtils.applySecondaryColor(context, sharedPreferences, orientationBtn)
        ColorUtils.applySecondaryColor(context, sharedPreferences, touchModeBtn)
        ColorUtils.applySecondaryColor(context, sharedPreferences, screencapBtn)
        ColorUtils.applySecondaryColor(context, sharedPreferences, screenshotBtn)
        ColorUtils.applySecondaryColor(context, sharedPreferences, settingsBtn)
        touchModeBtn.setOnClickListener {
            hideQuickSettingsPanel()
            DockLayoutDialog(context, true)
        }
        orientationBtn.setImageResource(
            if (sharedPreferences.getBoolean(
                    "lock_landscape",
                    true
                )
            ) R.drawable.ic_screen_rotation_off else R.drawable.ic_screen_rotation_on
        )
        orientationBtn.setOnClickListener {
            sharedPreferences.edit {
                putBoolean("lock_landscape", !sharedPreferences.getBoolean("lock_landscape", true))
            }
            orientationBtn
                .setImageResource(
                    if (sharedPreferences.getBoolean(
                            "lock_landscape",
                            true
                        )
                    ) R.drawable.ic_screen_rotation_off else R.drawable.ic_screen_rotation_on
                )
        }
        screenshotBtn.setOnClickListener {
            hideQuickSettingsPanel()
            takeScreenshot()
        }
        screencapBtn.setOnClickListener {
            hideQuickSettingsPanel()
            launchApp("standard", sharedPreferences.getString("app_rec", "")!!)
        }
        settingsBtn.setOnClickListener {
            hideQuickSettingsPanel()
            launchApp("standard", packageName)
        }
        notificationsBtn.setImageResource(
            if (sharedPreferences.getBoolean(
                    "show_notifications",
                    true
                )
            ) R.drawable.ic_notifications else R.drawable.ic_notifications_off
        )
        notificationsBtn.setOnClickListener {
            val showNotifications = sharedPreferences.getBoolean("show_notifications", true)
            sharedPreferences.edit { putBoolean("show_notifications", !showNotifications) }
            notificationsBtn.setImageResource(
                if (!showNotifications) R.drawable.ic_notifications else R.drawable.ic_notifications_off
            )
            if (showNotifications) Toast.makeText(
                context,
                R.string.popups_disabled,
                Toast.LENGTH_LONG
            ).show()
        }
        ColorUtils.applyMainColor(context, sharedPreferences, quickSettingsPanel!!)
        windowManager.addView(quickSettingsPanel, layoutParams)
        quickSettingsPanelVisible = true
        updateNotificationArea()
        updateWiFiStatus()
        updateBluetoothStatus()
    }

    private fun updateNotificationArea() {
        if (notificationBridge?.notifications != null) {
            val ignoredApps =
                sharedPreferences.getStringSet("ignored_notifications_panel", setOf())!!
            val notifications =
                notificationBridge?.notifications!!.filterNot { ignoredApps.contains(it.packageName) }
                    .sortedWith(
                        compareByDescending { AppUtils.isMediaNotification(it.notification) && it.isOngoing })
                    .toTypedArray<StatusBarNotification>()
            var adapter = notificationsLv!!.adapter
            if (adapter is NotificationAdapter)
                adapter.updateNotifications(notifications)
            else {
                adapter = NotificationAdapter(
                    context,
                    notifications,
                    this
                )
                notificationsLv!!.adapter = adapter
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showPowerMenu() {
        val layoutParams = Utils.makeWindowParams(
            Utils.dpToPx(context, 400),
            Utils.dpToPx(context, 120), context, preferSecondaryDisplay
        )
        layoutParams.gravity = Gravity.CENTER
        layoutParams.x = Utils.dpToPx(context, 10)
        layoutParams.flags = (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
        powerMenu = LayoutInflater.from(context).inflate(R.layout.power_menu, null) as LinearLayout
        powerMenu!!.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE)
                hidePowerMenu()

            false
        }
        val powerOffBtn = powerMenu!!.findViewById<ImageButton>(R.id.power_off_btn)
        val restartBtn = powerMenu!!.findViewById<ImageButton>(R.id.restart_btn)
        val softRestartBtn = powerMenu!!.findViewById<ImageButton>(R.id.soft_restart_btn)
        val lockBtn = powerMenu!!.findViewById<ImageButton>(R.id.lock_btn)
        ColorUtils.applySecondaryColor(context, sharedPreferences, powerOffBtn)
        ColorUtils.applySecondaryColor(context, sharedPreferences, restartBtn)
        ColorUtils.applySecondaryColor(context, sharedPreferences, softRestartBtn)
        ColorUtils.applySecondaryColor(context, sharedPreferences, lockBtn)
        powerOffBtn.setOnClickListener {
            hidePowerMenu()
            DeviceUtils.shutdown()
        }
        restartBtn.setOnClickListener {
            hidePowerMenu()
            DeviceUtils.reboot()
        }
        softRestartBtn.setOnClickListener {
            hidePowerMenu()
            DeviceUtils.softReboot()
        }
        lockBtn.setOnClickListener {
            hidePowerMenu()
            lockScreen()
        }
        ColorUtils.applyMainColor(context, sharedPreferences, powerMenu!!)
        windowManager.addView(powerMenu, layoutParams)
        topRightCorner!!.visibility = if (sharedPreferences.getBoolean(
                "enable_corner_top_right",
                false
            )
        ) View.VISIBLE else View.GONE
        powerMenuVisible = true
    }

    private fun hidePowerMenu() {
        windowManager.removeView(powerMenu)
        powerMenuVisible = false
        powerMenu = null
    }

    fun applyTheme() {
        updateDockBackgroundColor()
        ColorUtils.applyMainColor(context, sharedPreferences, appMenu!!)
        ColorUtils.applySecondaryColor(context, sharedPreferences, searchEntry)
        ColorUtils.applySecondaryColor(context, sharedPreferences, backBtn)
        ColorUtils.applySecondaryColor(context, sharedPreferences, homeBtn)
        ColorUtils.applySecondaryColor(context, sharedPreferences, recentBtn)
        ColorUtils.applySecondaryColor(context, sharedPreferences, assistBtn)
        ColorUtils.applySecondaryColor(context, sharedPreferences, pinBtn)
        ColorUtils.applySecondaryColor(context, sharedPreferences, powerBtn)
        ColorUtils.applySecondaryColor(context, sharedPreferences, statusArea)
    }

    private fun updateCorners() {
        topRightCorner!!.visibility = if (sharedPreferences.getBoolean(
                "enable_corner_top_right",
                false
            )
        ) View.VISIBLE else View.GONE
        bottomRightCorner!!.visibility = if (sharedPreferences.getBoolean(
                "enable_corner_bottom_right",
                false
            )
        ) View.VISIBLE else View.GONE
    }

    private fun updateMenuIcon() {
        val iconUri = sharedPreferences.getString("menu_icon_uri", "default")
        if (iconUri == "default") appsBtn.setImageResource(R.drawable.ic_apps_menu) else {
            try {
                val icon = iconUri?.toUri()
                if (icon != null)
                    appsBtn.setImageURI(icon)
            } catch (_: Exception) {
            }
        }
    }

    private fun updateBatteryBtn() {
        if (sharedPreferences.getBoolean("show_battery_level$orientationValue", false)) {
            batteryReceiver.showLevel = true
            batteryBtn.text = "${batteryReceiver.level}%"
        } else {
            batteryReceiver.showLevel = false
            batteryBtn.text = ""
        }
    }

    private fun toggleFavorites(visible: Boolean) {
        favoritesGv.visibility = if (visible) View.VISIBLE else View.GONE
        appsSeparator.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun loadFavoriteApps() {
        val apps = AppUtils.getPinnedApps(context, AppUtils.PINNED_LIST)
        toggleFavorites(apps.isNotEmpty())
        val menuFullscreen = sharedPreferences.getBoolean("app_menu_fullscreen", false)
        val phoneLayout = sharedPreferences.getInt("dock_layout", -1) == 0
        favoritesGv.adapter =
            AppAdapter(context, apps, this, menuFullscreen && !phoneLayout, iconPackUtils)
    }

    fun takeScreenshot() {
        performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
    }

    private fun lockScreen() {
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    }

    private fun updateHandlePositionValues() {
        val position = sharedPreferences.getString("handle_position", "start")
        handleLayoutParams.gravity =
            Gravity.BOTTOM or if (position == "start") Gravity.START else Gravity.END
        if (position == "end") {
            dockHandle!!.setBackgroundResource(R.drawable.dock_handle_bg_end)
            dockHandle!!.setCompoundDrawablesRelativeWithIntrinsicBounds(
                R.drawable.ic_expand_left,
                0,
                0,
                0
            )
        } else {
            dockHandle!!.setBackgroundResource(R.drawable.dock_handle_bg_start)
            dockHandle!!.setCompoundDrawablesRelativeWithIntrinsicBounds(
                R.drawable.ic_expand_right,
                0,
                0,
                0
            )
        }
    }

    private fun updateHandlePosition() {
        updateHandlePositionValues()
        windowManager.updateViewLayout(dockHandle, handleLayoutParams)
    }

    private fun updateDockBackgroundColor() {
        ColorUtils.applyMainColor(context, sharedPreferences, dockLayout)
        if (sharedPreferences.getBoolean("override_dock_background_alpha", false))
            applyDockAlpha()
    }

    private fun applyDockAlpha() {
        val alpha = sharedPreferences.getInt("dock_background_alpha", 255)
        dockLayout.background.alpha = alpha
    }

    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(motionEvent)
        return false
    }

    override fun onDestroy() {
        //TODO: Unregister all receivers
        isRunning = false
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        if (::batteryReceiver.isInitialized)
            unregisterReceiver(batteryReceiver)
        if (::soundEventsReceiver.isInitialized)
            unregisterReceiver(soundEventsReceiver)
        if (::displayListener.isInitialized && ::displayManager.isInitialized)
            displayManager.unregisterDisplayListener(displayListener)
        connectivityManager.unregisterNetworkCallback(wifiNetworkCallback)
        removeAllViews()
        unbindService(notificationServiceConnection)
        super.onDestroy()
    }

    fun performNavAction(key: String) {
        val action = sharedPreferences.getString("${key}_long_action", "none")
        when (action) {
            NAV_LONG_ACTIONS[0] -> return
            NAV_LONG_ACTIONS[1] -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            NAV_LONG_ACTIONS[2] -> launchAssistant()
            NAV_LONG_ACTIONS[3] -> lockScreen()
            NAV_LONG_ACTIONS[4] -> performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
        }
    }

    private fun createViews() {
        context = DeviceUtils.getDisplayContext(this, preferSecondaryDisplay)
        windowManager = context.getSystemService(WINDOW_SERVICE) as WindowManager
        orientation = context.resources.configuration.orientation
        orientationValue =
            if (orientation == Configuration.ORIENTATION_PORTRAIT) "" else "_landscape"

        createDock()
        createHotCorners()
        createAppMenu()
        notificationLayout = NotificationLayout(
            context,
            sharedPreferences,
            object : NotificationLayout.NotificationCancelListener {
                override fun onNotificationCancel(key: String) {
                    notificationBridge?.mCancelNotification(key)
                }
            })
        windowManager.addView(
            notificationLayout!!.notificationLayout,
            notificationLayout!!.notificationLayoutParams
        )
        applyTheme()
    }

    private fun createDock() {
        dock = LayoutInflater.from(
            androidx.appcompat.view.ContextThemeWrapper(
                context,
                R.style.AppTheme_Dock
            )
        ).inflate(R.layout.dock, null) as HoverInterceptorLayout

        dockLayout = dock!!.findViewById(R.id.dock_layout)
        dockHandle = LayoutInflater.from(context).inflate(R.layout.dock_handle, null) as Button
        appsBtn = dock!!.findViewById(R.id.apps_btn)
        tasksGv = dock!!.findViewById(R.id.apps_lv)
        val layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        tasksGv.layoutManager = layoutManager
        backBtn = dock!!.findViewById(R.id.back_btn)
        homeBtn = dock!!.findViewById(R.id.home_btn)
        recentBtn = dock!!.findViewById(R.id.recents_btn)
        assistBtn = dock!!.findViewById(R.id.assist_btn)
        notificationBtn = dock!!.findViewById(R.id.notifications_btn)
        pinBtn = dock!!.findViewById(R.id.pin_btn)
        statusArea = dock!!.findViewById(R.id.status_area)
        bluetoothBtn = dock!!.findViewById(R.id.bluetooth_btn)
        wifiBtn = dock!!.findViewById(R.id.wifi_btn)
        volumeBtn = dock!!.findViewById(R.id.volume_btn)
        batteryBtn = dock!!.findViewById(R.id.battery_btn)
        dateTv = dock!!.findViewById(R.id.date_btn)
        dock!!.setOnHoverListener { _, event ->
            if (event.action == MotionEvent.ACTION_HOVER_ENTER) {
                if (dockLayout.isGone) showDock()
            } else if (event.action == MotionEvent.ACTION_HOVER_EXIT) if (dockLayout.isVisible) {
                hideDock(500)
            }
            false
        }
        gestureDetector = GestureDetector(context, object : OnSwipeListener() {
            override fun onSwipe(direction: Direction): Boolean {
                if (direction == Direction.DOWN) {
                    if (appMenuVisible) hideAppMenu() else unpinDock()
                } else if (direction == Direction.LEFT) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                } else if (direction == Direction.UP) {
                    // The swipe activation method promises this in its own
                    // hint dialog ("Swiping up from the bottom of the
                    // screen"), but it was never implemented: only the
                    // pointer's hover could reveal the hidden dock (#264).
                    if (dockLayout.isGone) showDock()
                }
                return true
            }
        })
        dock!!.setOnTouchListener(this)
        dockLayout.setOnTouchListener(this)
        dockHandle!!.alpha = sharedPreferences.getString("handle_opacity", "0.5")!!.toFloat()
        dockHandle!!.setOnClickListener { pinDock() }
        appsBtn.setOnClickListener { toggleAppMenu() }
        appsBtn.setOnLongClickListener {
            launchApp(
                null, null,
                Intent(Settings.ACTION_APPLICATION_SETTINGS)
            )
            true
        }
        assistBtn.setOnClickListener { launchAssistant() }

        backBtn.setOnClickListener { performGlobalAction(GLOBAL_ACTION_BACK) }
        backBtn.setOnLongClickListener {
            performNavAction("enable_nav_back")
            true
        }
        homeBtn.setOnClickListener { performGlobalAction(GLOBAL_ACTION_HOME) }
        homeBtn.setOnLongClickListener {
            performNavAction("enable_nav_home")
            true
        }
        recentBtn.setOnClickListener { performGlobalAction(GLOBAL_ACTION_RECENTS) }
        recentBtn.setOnLongClickListener {
            performNavAction("enable_nav_recents")
            true
        }

        notificationBtn.setOnClickListener {
            if (sharedPreferences.getBoolean("enable_notif_panel", true)) {
                if (DeviceUtils.isServiceRunning(this, NotificationService::class.java)) {
                    toggleQuickSettingsPanel()
                } else {
                    NotificationPermissionDialog(this, true)
                }
            } else performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        }
        pinBtn.setOnClickListener { togglePin() }
        statusArea.setOnClickListener {
            toggleQuickSettingsPanel(1)
        }
        dateTv.setOnClickListener {
            launchApp(
                null,
                sharedPreferences.getString("app_clock", "com.android.deskclock")!!
            )
        }
        dateTv.setOnLongClickListener {
            launchApp(
                null, null,
                Intent(Settings.ACTION_DATE_SETTINGS)
            )
            true
        }

        dockHeight =
            Utils.dpToPx(context, sharedPreferences.getString("dock_height", "56")!!.toInt())
        dockLayoutParams =
            Utils.makeWindowParams(-1, dockHeight, context, preferSecondaryDisplay)
        dockLayoutParams.screenOrientation =
            if (sharedPreferences.getBoolean("lock_landscape", false))
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED


        updateNavigationBar()
        updateQuickSettings()
        updateDockShape()
        updateMenuIcon()
        loadPinnedApps()
        placeRunningApps()

        dockLayoutParams.gravity = Gravity.BOTTOM or Gravity.START
        windowManager.addView(dock, dockLayoutParams)

        //Dock handle
        handleLayoutParams = Utils.makeWindowParams(
            Utils.dpToPx(context, 22), -2, context,
            preferSecondaryDisplay
        )
        updateHandlePositionValues()
        windowManager.addView(dockHandle, handleLayoutParams)
    }

    private fun createHotCorners() {
        topRightCorner = Button(context)
        topRightCorner!!.setBackgroundResource(R.drawable.corner_background)
        bottomRightCorner = Button(context)
        bottomRightCorner!!.setBackgroundResource(R.drawable.corner_background)
        topRightCorner!!.setOnHoverListener(HotCornersHoverListener("enable_corner_top_right"))
        bottomRightCorner!!.setOnHoverListener(HotCornersHoverListener("enable_corner_bottom_right"))
        updateCorners()
        val cornersLayoutParams = Utils.makeWindowParams(
            Utils.dpToPx(context, 2), -2, context,
            preferSecondaryDisplay
        )
        cornersLayoutParams.gravity = Gravity.TOP or Gravity.END
        windowManager.addView(topRightCorner, cornersLayoutParams)
        cornersLayoutParams.gravity = Gravity.BOTTOM or Gravity.END
        windowManager.addView(bottomRightCorner, cornersLayoutParams)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createAppMenu() {
        appMenu = LayoutInflater.from(ContextThemeWrapper(context, R.style.AppTheme_Dock))
            .inflate(R.layout.apps_menu, null) as LinearLayout
        searchEntry = appMenu!!.findViewById(R.id.search_entry)
        searchEt = appMenu!!.findViewById(R.id.menu_et)
        powerBtn = appMenu!!.findViewById(R.id.power_btn)
        appsGv = appMenu!!.findViewById(R.id.menu_applist_lv)
        appsGv.setHasFixedSize(true)
        appsGv.layoutManager = GridLayoutManager(context, 5)
        favoritesGv = appMenu!!.findViewById(R.id.fav_applist_lv)
        favoritesGv.layoutManager = GridLayoutManager(context, 5)
        searchLayout = appMenu!!.findViewById(R.id.search_layout)
        searchTv = appMenu!!.findViewById(R.id.search_tv)
        appsSeparator = appMenu!!.findViewById(R.id.apps_separator)
        val avatarIv = appMenu!!.findViewById<ImageView>(R.id.avatar_iv)
        powerBtn.setOnClickListener {
            if (sharedPreferences.getBoolean("enable_power_menu", false)) {
                if (powerMenuVisible) hidePowerMenu() else showPowerMenu()
            } else performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
            hideAppMenu()
        }
        searchTv.setOnClickListener {
            try {
                launchApp(
                    null, null,
                    Intent(
                        Intent.ACTION_VIEW,
                        ("https://www.google.com/search?q="
                                + URLEncoder.encode(searchEt.text.toString(), "UTF-8")).toUri()
                    )
                )
            } catch (e: UnsupportedEncodingException) {
                throw RuntimeException(e)
            }
        }

        searchEt.addTextChangedListener { text ->
            if (text != null) {
                val appAdapter = appsGv.adapter as AppAdapter
                appAdapter.filter(text.toString())
                if (text.length > 1) {
                    searchLayout.visibility = View.VISIBLE
                    searchTv.text =
                        getString(R.string.search_for) + " \"" + text + "\" " + getString(R.string.on_google)
                    toggleFavorites(false)
                } else {
                    searchLayout.visibility = View.GONE
                    toggleFavorites(
                        AppUtils.getPinnedApps(
                            context,
                            AppUtils.PINNED_LIST
                        ).isNotEmpty()
                    )
                }
            }
        }

        searchEt.setOnKeyListener { _, code, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (code == KeyEvent.KEYCODE_ENTER && searchEt.text.toString().length > 1) {
                    try {
                        launchApp(
                            null, null,
                            Intent(
                                Intent.ACTION_VIEW,
                                ("https://www.google.com/search?q="
                                        + URLEncoder.encode(
                                    searchEt.text.toString(),
                                    "UTF-8"
                                )).toUri()
                            )
                        )
                    } catch (e: UnsupportedEncodingException) {
                        throw RuntimeException(e)
                    }
                    true
                } else if (code == KeyEvent.KEYCODE_DPAD_DOWN)
                    appsGv.requestFocus()
            }
            false
        }

        avatarIv.setOnClickListener {
            if (AppUtils.isSystemApp(context, packageName))
                launchApp(null, null, Intent("android.settings.USER_SETTINGS"))
            else
                launchApp(null, null, Intent(this, MainActivity::class.java))
        }

        updateAppMenu()

        //TODO: Filter app button menu click only
        appMenu!!.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE
                && (event.y < appMenu!!.measuredHeight || event.x > appMenu!!.measuredWidth)
            ) {
                hideAppMenu()
            }
            false
        }
    }

    private fun restartUI() {
        removeAllViews()
        createViews()
        updateBatteryBtn()
        if (sharedPreferences.getBoolean("pin_dock", true))
            pinDock()
        else
            Toast.makeText(context, R.string.start_message, Toast.LENGTH_LONG).show()
    }

    fun showFirstDockHideDialog(activationMethod: String) {
        val dialog = DockDialog(context, true)
        dialog.setTitle(R.string.dock_hidden)
        val message =
            if (activationMethod == "swipe") R.string.dock_hidden_message_swipe else R.string.dock_hidden_message_handle
        dialog.setMessage(message)
        dialog.setPositiveButton(R.string.close, null)
        dialog.setCancelable(false)
        dialog.show()
        sharedPreferences.edit { putBoolean("first_hide", false) }
    }

    private fun updateWiFiStatus() {
        val wifiIcon = AppCompatResources.getDrawable(this, R.drawable.ic_wifi_on)!!
        val enabled = wifiManager.isWifiEnabled
        if (quickSettingsPanelVisible) {
            if (enabled) {
                wifiIcon.setColorFilter(
                    getMainColors(sharedPreferences, context)[0],
                    PorterDuff.Mode.SRC_ATOP
                )
                ColorUtils.applyColor(wifiButton!!, getColor(R.color.action))
            } else {
                ColorUtils.applyMainColor(context, sharedPreferences, wifiButton!!)
            }
            wifiButton?.setImageDrawable(wifiIcon)
            val wifiSsidTv = quickSettingsPanel!!.findViewById<TextView>(R.id.wifi_ssid_tv)
            wifiManagerWrapper?.getConnectionInfo()?.let {
                if (it.ssid.startsWith("<") && it.ssid.endsWith(">"))
                    wifiSsidTv.isVisible = false
                else {
                    wifiSsidTv.isVisible = true
                    wifiSsidTv.text = it.ssid.replace("\"", "")
                }
            }
        }
        wifiBtn.setImageResource(if (enabled) R.drawable.ic_wifi_on else R.drawable.ic_wifi_off)
    }

    private fun updateBluetoothStatus() {
        val bluetoothIcon = AppCompatResources.getDrawable(this, R.drawable.ic_bluetooth)!!
        val enabled = bluetoothManager.adapter?.isEnabled ?: false
        if (quickSettingsPanelVisible) {
            if (enabled) {
                bluetoothIcon.setColorFilter(
                    getMainColors(sharedPreferences, context)[0],
                    PorterDuff.Mode.SRC_ATOP
                )
                ColorUtils.applyColor(bluetoothButton!!, getColor(R.color.action))
            } else {
                ColorUtils.applyMainColor(context, sharedPreferences, bluetoothButton!!)
            }
            bluetoothButton?.setImageDrawable(bluetoothIcon)
        }
        bluetoothBtn.setImageResource(if (enabled) R.drawable.ic_bluetooth else R.drawable.ic_bluetooth_off)
    }

    private fun removeAllViews() {
        try {
            dock?.let { windowManager.removeViewImmediate(it) }
            dockHandle?.let { windowManager.removeViewImmediate(it) }
            appMenu?.let { windowManager.removeViewImmediate(it) }
            topRightCorner?.let { windowManager.removeViewImmediate(it) }
            bottomRightCorner?.let { windowManager.removeViewImmediate(it) }
            notificationLayout?.let { windowManager.removeViewImmediate(it.notificationLayout) }
        } catch (_: Exception) {
        }

        dock = null
        dockHandle = null
        appMenu = null
        topRightCorner = null
        bottomRightCorner = null
        notificationLayout = null
    }

    inner class HotCornersHoverListener(val key: String) : View.OnHoverListener {
        override fun onHover(
            v: View?,
            event: MotionEvent
        ): Boolean {
            if (event.action == MotionEvent.ACTION_HOVER_ENTER) {
                val handler = Handler(mainLooper)
                handler.postDelayed({
                    if (topRightCorner!!.isHovered)
                        performNavAction(key)
                }, sharedPreferences.getString("hot_corners_delay", "300")!!.toInt().toLong())
            }
            return false
        }
    }

    private fun bindNotificationService() {
        if (notificationBridge != null)
            return
        bindService(
            Intent(
                this,
                NotificationService::class.java
            ).setAction(ACTION_BIND_NOTIFICATION_SERVICE), notificationServiceConnection,
            BIND_AUTO_CREATE
        )
    }

    private val notificationServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(
            p0: ComponentName?,
            p1: IBinder?
        ) {
            notificationBridge = INotificationServiceBridge.Stub.asInterface(p1)
            notificationBridge?.registerCallback(object : INotificationCallback.Stub() {
                override fun onNotificationPosted(sbn: StatusBarNotification?) {
                    if (quickSettingsPanelVisible)
                        updateNotificationArea()
                    updateNotificationCount()
                    notificationLayout?.let {
                        it.updateNotificationLayout(sbn!!)
                        windowManager.updateViewLayout(
                            it.notificationLayout,
                            it.notificationLayoutParams
                        )
                    }
                }

                override fun onNotificationRemoved(sbn: StatusBarNotification?) {
                    if (quickSettingsPanelVisible)
                        updateNotificationArea()
                    updateNotificationCount()
                }

            })
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            notificationBridge = null
        }

    }

    override fun onNotificationClicked(sbn: StatusBarNotification, item: View) {
        val notification = sbn.notification
        if (notification.contentIntent != null) {
            try {
                notification.contentIntent.send()
                if (sbn.isClearable)
                    notificationBridge?.mCancelNotification(sbn.key)
            } catch (_: CanceledException) {
            }
        }
    }

    override fun onNotificationLongClicked(notification: StatusBarNotification, item: View) {
        item.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        val dialog = DockDialog(context, true)
        dialog.setTitle(R.string.hide_notifications)
        dialog.setMessage(R.string.hide_notification_panel)
        dialog.setNegativeButton(R.string.cancel, null)
        dialog.setPositiveButton(
            R.string.hide
        ) { dialog, which ->
            val savedApps = sharedPreferences.getStringSet(
                "ignored_notifications_panel",
                setOf()
            )!!
            val ignoredApps = mutableSetOf<String>()
            ignoredApps.addAll(savedApps)
            ignoredApps.add(notification.packageName)
            Toast.makeText(this, ignoredApps.toString(), Toast.LENGTH_LONG).show()
            sharedPreferences.edit { putStringSet("ignored_notifications_panel", ignoredApps) }
            Toast.makeText(
                this@DockService,
                R.string.silenced_notifications,
                Toast.LENGTH_LONG
            )
                .show()
        }
        dialog.show()
    }

    override fun onNotificationCancelClicked(notification: StatusBarNotification, item: View) {
        notificationBridge?.mCancelNotification(notification.key)
    }

    private fun updateNotificationCount() {
        val count = notificationBridge?.notificationCount ?: 0
        if (count > 0) {
            notificationBtn.setBackgroundResource(R.drawable.circle)
            notificationBtn.text = count.toString()
        } else {
            notificationBtn.setBackgroundResource(R.drawable.ic_expand_up_circle)
            notificationBtn.text = ""
        }
    }

    private fun showSettingsPermissionDialog() {
        val dialog = DockDialog(context, true)
        dialog.setTitle(R.string.missing_permission)
        dialog.setMessage(R.string.write_settings_desc)
        dialog.setNegativeButton(R.string.cancel, null)
        dialog.setPositiveButton(
            R.string.manage
        ) { dialog, which ->
            launchApp(
                null, null, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData("package:${packageName}".toUri())
            )
        }
        dialog.show()
    }

    private val wifiNetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            CoroutineScope(Dispatchers.Main).launch {
                if (quickSettingsPanelVisible)
                    updateWiFiStatus()
            }
        }

        override fun onLost(network: Network) {
            CoroutineScope(Dispatchers.Main).launch {
                if (quickSettingsPanelVisible)
                    updateWiFiStatus()
            }
        }

    }
}
