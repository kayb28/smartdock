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
    DISPLAY_SETTINGS
}

class Action(val icon: Int, val text: String, val command: MenuCommand = MenuCommand.NONE)
