package cu.axel.smartdock.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.Rect
import android.database.sqlite.SQLiteOpenHelper
import cu.axel.smartdock.db.DatabaseContract.LaunchModesTable
import android.provider.BaseColumns

class DBHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    override fun onCreate(p1: SQLiteDatabase) {
        p1.execSQL(SQL_CREATE_MODES_ENTRIES)
    }

    override fun onUpgrade(p1: SQLiteDatabase, p2: Int, p3: Int) {
        // v2 adds the per-app window geometry columns (fork F7). Existing
        // rows keep their saved launch mode; the new columns stay null
        // until a geometry is remembered for that app.
        if (p2 < 2) {
            for (column in arrayOf(
                    LaunchModesTable.COLUMN_WINDOW_LEFT, LaunchModesTable.COLUMN_WINDOW_TOP,
                    LaunchModesTable.COLUMN_WINDOW_RIGHT, LaunchModesTable.COLUMN_WINDOW_BOTTOM))
                try {
                    p1.execSQL("ALTER TABLE " + LaunchModesTable.TABLE_NAME + " ADD COLUMN " + column + " INTEGER")
                } catch (_: Exception) {
                }
        }
    }
    fun saveLaunchMode(app: String, mode: String) {
        val values = ContentValues()
        values.put(LaunchModesTable.COLUMN_LAUNCH_MODE, mode)
        upsertLaunchMode(app, values)
    }

    /**
     * Remember a window geometry for an app and switch it to the "custom"
     * launch mode (fork F7): the next launch restores these bounds instead
     * of one of the fixed modes. Needs DATABASE_VERSION >= 2 (the geometry
     * columns); an un-migrated database just keeps the old mode.
     */
    fun saveWindowGeometry(app: String, bounds: Rect) {
        val values = ContentValues()
        values.put(LaunchModesTable.COLUMN_LAUNCH_MODE, "custom")
        values.put(LaunchModesTable.COLUMN_WINDOW_LEFT, bounds.left)
        values.put(LaunchModesTable.COLUMN_WINDOW_TOP, bounds.top)
        values.put(LaunchModesTable.COLUMN_WINDOW_RIGHT, bounds.right)
        values.put(LaunchModesTable.COLUMN_WINDOW_BOTTOM, bounds.bottom)
        upsertLaunchMode(app, values)
    }

    private fun upsertLaunchMode(app: String, values: ContentValues) {
        try {
            val db = writableDatabase
            values.put(LaunchModesTable.COLUMN_PACKAGE_NAME, app)
            val selection = LaunchModesTable.COLUMN_PACKAGE_NAME + " = ?"
            val selectionArgs = arrayOf(app)
            val cursor = db.query(LaunchModesTable.TABLE_NAME, null, selection, selectionArgs, null, null, null)
            if (cursor != null && cursor.count > 0) {
                cursor.moveToFirst()
                db.update(LaunchModesTable.TABLE_NAME, values, selection, selectionArgs)
            } else {
                db.insert(LaunchModesTable.TABLE_NAME, null, values)
            }
            cursor!!.close()
        } catch (_: Exception) {
        }
    }

    /** The remembered window geometry of an app, or null when it has none. */
    fun getWindowGeometry(app: String): Rect? {
        var geometry: Rect? = null
        try {
            val db = readableDatabase
            val selection = LaunchModesTable.COLUMN_PACKAGE_NAME + " = ?"
            val selectionArgs = arrayOf(app)
            val cursor = db.query(LaunchModesTable.TABLE_NAME, null, selection, selectionArgs, null, null, null)
            if (cursor != null && cursor.count > 0) {
                cursor.moveToFirst()
                val left = intColumn(cursor, LaunchModesTable.COLUMN_WINDOW_LEFT)
                val top = intColumn(cursor, LaunchModesTable.COLUMN_WINDOW_TOP)
                val right = intColumn(cursor, LaunchModesTable.COLUMN_WINDOW_RIGHT)
                val bottom = intColumn(cursor, LaunchModesTable.COLUMN_WINDOW_BOTTOM)
                if (left != null && top != null && right != null && bottom != null)
                    geometry = Rect(left, top, right, bottom)
            }
            cursor?.close()
        } catch (_: Exception) {
        }
        return geometry
    }

    private fun intColumn(cursor: Cursor, column: String): Int? {
        val index = cursor.getColumnIndex(column)
        return if (index >= 0 && !cursor.isNull(index)) cursor.getInt(index) else null
    }

    fun getLaunchMode(app: String): String? {
        var mode: String? = null
        try {
            val db = readableDatabase
            val selection = LaunchModesTable.COLUMN_PACKAGE_NAME + " = ?"
            val selectionArgs = arrayOf(app)
            val cursor = db.query(LaunchModesTable.TABLE_NAME, null, selection, selectionArgs, null, null, null)
            if (cursor != null && cursor.count > 0) {
                cursor.moveToFirst()
                //TODO: recheck this
                val index = cursor.getColumnIndex(LaunchModesTable.COLUMN_LAUNCH_MODE)
                mode = cursor.getString(index)
                cursor.close()
            }
        } catch (_: Exception) {
        }
        return mode
    }

    companion object {
        private const val DATABASE_VERSION = 2
        private const val DATABASE_NAME = "smartdock.db"
        private const val SQL_CREATE_MODES_ENTRIES = ("CREATE TABLE " + LaunchModesTable.TABLE_NAME + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY NOT NULL," + LaunchModesTable.COLUMN_PACKAGE_NAME
                + " TEXT NOT NULL," + LaunchModesTable.COLUMN_LAUNCH_MODE + " TEXT NOT NULL,"
                + LaunchModesTable.COLUMN_WINDOW_LEFT + " INTEGER," + LaunchModesTable.COLUMN_WINDOW_TOP
                + " INTEGER," + LaunchModesTable.COLUMN_WINDOW_RIGHT + " INTEGER,"
                + LaunchModesTable.COLUMN_WINDOW_BOTTOM + " INTEGER )")
    }
}
