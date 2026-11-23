package cu.axel.smartdock.models

/**
 * Menu actions are identified by a stable [Command], never by their visible
 * text: a translated label (or two locales colliding on one string) must not
 * change what a menu entry does. Builders fill [command] and the click
 * listeners dispatch on it.
 */
enum class MenuCommand {
    NONE,
    BACK,
    PIN,
    UNPIN,
    MOVE,
    MOVE_LEFT,
    MOVE_RIGHT,
    MANAGE,
    APP_INFO,
    HIDE,
    UNINSTALL,
    FREEZE,
    SHORTCUTS,
    PIN_SHORTCUT_TO_DOCK,
    PIN_SHORTCUT,
    OPEN_AS,
    ADD_TO,
    LAUNCH_IN,
    FAVORITES,
    REMOVE_FAVORITE,
    DESKTOP,
    DOCK,
    STANDARD,
    MAXIMIZED,
    PORTRAIT,
    FULLSCREEN,
    RESIZE,
    SNAP,
    TOP,
    BOTTOM,
    LEFT,
    RIGHT,
    CLOSE,
    FORCE_STOP,
    SEND_TO_DISPLAY,
    CHANGE_WALLPAPER,
    DISPLAY_SETTINGS,
    SAVE_WINDOW_SIZE,
    WINDOW_CUSTOM,
    WINDOW_PRESET,
    WINDOW_PRESET_DELETE
}

/**
 * [payload] carries command-scoped data the dispatch needs beyond the
 * command itself (e.g. the preset name for [MenuCommand.WINDOW_PRESET]);
 * the visible [text] stays a label only.
 */
class Action(
    val icon: Int, val text: String, val command: MenuCommand = MenuCommand.NONE,
    val payload: String? = null
)
