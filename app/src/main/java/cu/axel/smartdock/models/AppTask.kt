package cu.axel.smartdock.models

import android.graphics.drawable.Drawable
const val WINDOWING_MODE_FULLSCREEN = 1
const val WINDOWING_MODE_FREEFORM = 5
// Means "the display the task lives on could not be determined" (no
// reflection access to the hidden TaskInfo field, or a task that never had
// one): callers fall back to the display the dock lives on.
const val UNKNOWN_DISPLAY = Int.MIN_VALUE
class AppTask(val id: Int, label: String, packageName: String, icon: Drawable, val windowingMode: Int = -1, val displayId: Int = UNKNOWN_DISPLAY) : App(label, packageName, icon)
