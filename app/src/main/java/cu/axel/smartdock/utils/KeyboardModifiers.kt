package cu.axel.smartdock.utils

/**
 * Pure-JVM extraction of the keyboard-modifier logic that lives inside
 * DockService.onKeyEvent, so the two regression-prone acordes — AltGr firing
 * shortcuts (#167) and Meta+X opening the app menu on Meta's release (#267) —
 * can be unit-tested without android.jar.
 *
 * The [KeyEvent] constant values referenced here are frozen by the Android
 * ABI; they are hardcoded as Ints so this object has no Android dependency and
 * the caller (DockService) passes the event-derived fields straight through.
 */
object KeyboardModifiers {
    // KeyEvent.ACTION_DOWN / ACTION_UP
    const val ACTION_DOWN = 0
    const val ACTION_UP = 1

    // KeyEvent.META_ALT_RIGHT_ON
    const val META_ALT_RIGHT_ON = 0x20

    // KeyEvent.KEYCODE_META_LEFT
    const val KEYCODE_META_LEFT = 117

    /**
     * AltGr (right Alt) reaches the app as Ctrl+Alt, or with META_ALT_RIGHT_ON
     * set. The shortcut block fired on every such chord, so typing AltGr+Q
     * (which is "@" on many layouts) opened quick settings and AltGr+L locked
     * the desktop while the character was still delivered to the app (#167).
     * Dock shortcuts never use Ctrl+Alt, so an AltGr chord on KEY_UP must pass
     * through untouched.
     */
    fun isAltGrPassThrough(
        action: Int, metaState: Int, isAltPressed: Boolean, isCtrlPressed: Boolean
    ): Boolean =
        action == ACTION_UP &&
            ((metaState and META_ALT_RIGHT_ON) != 0 || (isAltPressed && isCtrlPressed))
}

/**
 * Tracks Meta-left presses to distinguish "Meta pressed alone" (open the app
 * menu) from "Meta held while other keys were used" (a modifier: its release
 * must do nothing). Without this, every Meta+X shortcut was followed by the
 * app menu opening on Meta's release (#267).
 *
 * Feed every key event via [onKey]; read [usedAsModifier] when Meta's KEY_UP
 * arrives to decide whether to open the menu.
 */
class MetaMenuTracker {
    var metaLeftDown = false
        private set
    var usedAsModifier = false
        private set

    /**
     * @param action KeyEvent.ACTION_DOWN / ACTION_UP
     * @param keyCode the key
     * @param repeatCount event.repeatCount (a Meta auto-repeat must not reset
     *   the tracker, so only repeatCount == 0 sets the down state)
     */
    fun onKey(action: Int, keyCode: Int, repeatCount: Int = 0) {
        if (keyCode == KeyboardModifiers.KEYCODE_META_LEFT) {
            if (action == KeyboardModifiers.ACTION_DOWN && repeatCount == 0) {
                metaLeftDown = true
                usedAsModifier = false
            } else if (action == KeyboardModifiers.ACTION_UP) {
                metaLeftDown = false
            }
        } else if (metaLeftDown && action == KeyboardModifiers.ACTION_DOWN) {
            usedAsModifier = true
        }
    }
}
