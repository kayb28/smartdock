package cu.axel.smartdock.db

import android.provider.BaseColumns

class DatabaseContract private constructor() {
    object LaunchModesTable : BaseColumns {
        const val TABLE_NAME = "table_launch_modes"
        const val COLUMN_PACKAGE_NAME = "package_name"
        const val COLUMN_LAUNCH_MODE = "launch_mode"
        // Per-app window geometry (fork F7): null until the user remembers
        // a window size for the app (DBHelper v2 migration adds them).
        const val COLUMN_WINDOW_LEFT = "window_left"
        const val COLUMN_WINDOW_TOP = "window_top"
        const val COLUMN_WINDOW_RIGHT = "window_right"
        const val COLUMN_WINDOW_BOTTOM = "window_bottom"
    }
}
