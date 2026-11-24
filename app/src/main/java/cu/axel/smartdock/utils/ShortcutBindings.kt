package cu.axel.smartdock.utils

import androidx.preference.PreferenceManager

/**
 * User-customizable keyboard shortcuts (F8): chord model, conflict rules,
 * JSON persistence and dispatch resolution for the shortcut editor.
 *
 * The dock's fixed shortcuts (Alt+L, Alt+P, ...) live in
 * DockService.onKeyEvent; this model lets the user bind extra chords to the
 * same actions. Dispatch is "bindings first, fixed defaults as fallback": a
 * custom binding on a default chord (e.g. Alt+L) shadows the default for that
 * chord only — the other defaults keep working.
 *
 * Same discipline as [KeyboardModifiers]: pure JVM, no android.jar — the
 * KeyEvent constant values referenced here are frozen by the Android ABI and
 * hardcoded as Ints (verified against AOSP KeyEvent.java, 2026-11-24); the
 * callers (DockService, the capture dialog) pass event-derived fields
 * straight through.
 *
 * AltGr: right Alt reaches the app as Ctrl+Alt (or META_ALT_RIGHT_ON) and the
 * dock passes those chords through untouched so characters like "@" keep
 * typing (#167). Chords that contain BOTH Ctrl and Alt can therefore never
 * fire — [Validation.ALTGR_RESERVED] rejects them at save time instead of
 * creating a silent dead binding.
 */
object ShortcutBindings {
    // Own modifier bitmask (event.metaState bits are wide and non-contiguous;
    // bindings store a normalized form).
    const val MOD_SHIFT = 1
    const val MOD_CTRL = 2
    const val MOD_ALT = 4
    const val MOD_META = 8

    const val MOD_ANY = MOD_SHIFT or MOD_CTRL or MOD_ALT or MOD_META

    /** SharedPreferences key holding the JSON array of custom bindings. */
    const val PREF_KEY = "shortcut_bindings"

    /** Modifier-only main keys can never form a chord: ignore them in capture. */
    fun isModifierKey(keyCode: Int): Boolean = keyCode in intArrayOf(
        57, 58, 59, 60, 113, 114, 117, 118
    ) // ALT_L/R, SHIFT_L/R, CTRL_L/R, META_L/R (AOSP KeyEvent.java)

    /** Chord a custom binding is made of: normalized modifiers + main key. */
    data class Binding(val mods: Int, val keyCode: Int, val action: String)

    /** The actions a chord can trigger, with their fixed default chords. */
    enum class ShortcutAction(
        val id: String,
        val defaultMods: Int,
        val defaultKey: Int,
        /** Gate preference (an "enable_*" switch); null = always active. */
        val prefKey: String?,
        val defaultEnabled: Boolean
    ) {
        LOCK_DESKTOP("lock_desktop", MOD_ALT, 40, "enable_lock_desktop", true),
        OPEN_SETTINGS("open_settings", MOD_ALT, 44, "enable_open_settings", true),
        OPEN_TERMINAL("open_terminal", MOD_ALT, 48, "enable_open_terminal", false),
        EXPAND_NOTIFICATIONS("expand_notifications", MOD_ALT, 45, "enable_expand_notifications", true),
        TOGGLE_PIN("toggle_pin", MOD_ALT, 51, "enable_toggle_pin", true),
        OPEN_MUSIC("open_music", MOD_ALT, 41, "enable_open_music", true),
        OPEN_BROWSER("open_browser", MOD_ALT, 30, "enable_open_browser", true),
        OPEN_ASSIST("open_assist", MOD_ALT, 29, "enable_open_assist", true),
        OPEN_REC("open_rec", MOD_ALT, 46, "enable_open_rec", true),
        GO_HOME("go_home", MOD_ALT, 32, null, true),
        TOGGLE_KEYBOARD("toggle_keyboard", MOD_ALT, 43, null, true),
        SOFT_REBOOT("soft_reboot", MOD_ALT, 142, "enable_soft_reboot", false),
        WINDOW_PORTRAIT("window_portrait", MOD_ALT, 133, null, true),
        WINDOW_MAXIMIZE("window_maximize", MOD_ALT, 19, null, true),
        WINDOW_TILED_LEFT("window_tiled_left", MOD_ALT, 21, null, true),
        WINDOW_TILED_RIGHT("window_tiled_right", MOD_ALT, 22, null, true),
        WINDOW_STANDARD("window_standard", MOD_ALT, 20, null, true),
        NEW_INSTANCE("new_instance", MOD_ALT or MOD_SHIFT, 42, "enable_new_instance", true),
        SEND_DISPLAY_1("send_display_1", MOD_ALT or MOD_SHIFT, 8, "enable_tiling", true),
        SEND_DISPLAY_2("send_display_2", MOD_ALT or MOD_SHIFT, 9, "enable_tiling", true),
        SEND_DISPLAY_3("send_display_3", MOD_ALT or MOD_SHIFT, 10, "enable_tiling", true),
        SEND_DISPLAY_4("send_display_4", MOD_ALT or MOD_SHIFT, 11, "enable_tiling", true);

        companion object {
            /** Action id stored in JSON → enum, null when unknown (old/foreign data). */
            fun fromId(id: String): ShortcutAction? =
                entries.firstOrNull { it.id == id }
        }
    }

    /** The default chord of every action, in catalog order. */
    fun defaults(): List<Binding> =
        ShortcutAction.entries.map { Binding(it.defaultMods, it.defaultKey, it.id) }

    enum class Validation { OK, ALTGR_RESERVED, NO_MODIFIER, NO_KEY }

    /** Event pressed-state flags → normalized modifier bitmask. */
    fun modsOf(ctrl: Boolean, alt: Boolean, shift: Boolean, meta: Boolean): Int {
        var mods = 0
        if (shift) mods = mods or MOD_SHIFT
        if (ctrl) mods = mods or MOD_CTRL
        if (alt) mods = mods or MOD_ALT
        if (meta) mods = mods or MOD_META
        return mods
    }

    /** Drop unknown bits so stored chords stay canonical. */
    fun normalize(mods: Int): Int = mods and MOD_ANY

    fun isAltGrReserved(mods: Int): Boolean =
        (mods and MOD_CTRL) != 0 && (mods and MOD_ALT) != 0

    /**
     * A chord must carry a real modifier: Shift alone would fire while
     * typing capital letters; a bare key fires on every keystroke.
     */
    fun needsModifier(mods: Int): Boolean =
        (mods and (MOD_CTRL or MOD_ALT or MOD_META)) != 0

    fun validate(mods: Int, keyCode: Int): Validation {
        if (keyCode <= 0) return Validation.NO_KEY
        if (isAltGrReserved(mods)) return Validation.ALTGR_RESERVED
        if (!needsModifier(mods)) return Validation.NO_MODIFIER
        return Validation.OK
    }

    /** The action already bound to this chord, if any. */
    fun conflictOf(bindings: List<Binding>, mods: Int, keyCode: Int): Binding? =
        bindings.firstOrNull {
            it.mods == normalize(mods) && it.keyCode == keyCode
        }

    /** Chord lookup over the custom bindings; null when nothing matches. */
    fun resolve(
        bindings: List<Binding>, mods: Int, keyCode: Int
    ): ShortcutAction? {
        val bound = conflictOf(bindings, mods, keyCode) ?: return null
        return ShortcutAction.fromId(bound.action)
    }

    /**
     * What the settings list shows for an action: the custom chord when the
     * user replaced it, the fixed default otherwise.
     */
    fun displayChord(action: ShortcutAction, bindings: List<Binding>): String {
        val bound = bindings.firstOrNull { it.action == action.id }
        return if (bound != null) formatChord(bound.mods, bound.keyCode)
        else formatChord(action.defaultMods, action.defaultKey)
    }

    // ---- display ----

    /**
     * "Ctrl+Alt+Shift+Meta+L". Modifier names and key names are technical
     * identifiers (like Ctrl/Del) and stay untranslated in every locale.
     */
    fun formatChord(mods: Int, keyCode: Int): String {
        val parts = StringBuilder()
        if (mods and MOD_CTRL != 0) parts.append("Ctrl+")
        if (mods and MOD_ALT != 0) parts.append("Alt+")
        if (mods and MOD_SHIFT != 0) parts.append("Shift+")
        if (mods and MOD_META != 0) parts.append("Meta+")
        return parts.append(keyName(keyCode)).toString()
    }

    fun keyName(keyCode: Int): String = when (keyCode) {
        in 29..54 -> ('A' + keyCode - 29).toString()
        in 7..16 -> ('0' + keyCode - 7).toString()
        in 131..142 -> "F${keyCode - 130}"
        19 -> "Up"
        20 -> "Down"
        21 -> "Left"
        22 -> "Right"
        66 -> "Enter"
        62 -> "Space"
        61 -> "Tab"
        111 -> "Esc"
        else -> "Key $keyCode"
    }

    // ---- JSON persistence ----

    /**
     * Deterministic compact JSON — `[{"mods":4,"key":40,"action":"lock_desktop"}]`.
     * Hand-rolled instead of org.json so the round-trip is unit-testable on
     * the JVM (android.jar stubs org.json out there); the format is written
     * and read only by this object.
     */
    fun toJson(bindings: List<Binding>): String =
        bindings.joinToString(
            separator = ",",
            prefix = "[",
            postfix = "]"
        ) { b ->
            """{"mods":${normalize(b.mods)},"key":${b.keyCode},"action":"${escape(b.action)}"}"""
        }

    /**
     * Malformed storage must degrade, never crash the dock (same contract as
     * WindowPresets): garbage text → empty list; single broken entries are
     * skipped while valid ones survive.
     */
    fun fromJson(text: String?): List<Binding> {
        if (text.isNullOrBlank()) return emptyList()
        var i = 0
        fun skipWs() {
            while (i < text.length && text[i].isWhitespace()) i++
        }
        val out = ArrayList<Binding>()
        skipWs()
        if (i >= text.length || text[i] != '[') return emptyList()
        i++
        skipWs()
        if (i < text.length && text[i] == ']') return emptyList()
        while (i < text.length) {
            skipWs()
            if (i >= text.length || text[i] != '{') return out // junk entry: keep what parsed
            val objEnd = text.indexOf('}', i)
            if (objEnd < 0) return out // truncated tail: keep what parsed
            val entry = text.substring(i + 1, objEnd)
            i = objEnd + 1
            parseEntry(entry)?.let { out.add(it) }
            skipWs()
            if (i < text.length && text[i] == ',') {
                i++
                continue
            }
            break
        }
        return out
    }

    /** One flat object with exactly mods/key/action; null when broken. */
    private fun parseEntry(entry: String): Binding? {
        val mods = entry.intField("mods") ?: return null
        val key = entry.intField("key") ?: return null
        val actionIdx = entry.indexOf("\"action\"")
        if (actionIdx < 0) return null
        val open = entry.indexOf('"', actionIdx + 8)
        if (open < 0) return null
        // Scan the value respecting backslash escapes so an escaped quote
        // inside it does not end the string early.
        var j = open + 1
        var close = -1
        while (j < entry.length) {
            val c = entry[j]
            if (c == '\\' && j + 1 < entry.length) {
                j += 2
                continue
            }
            if (c == '"') {
                close = j
                break
            }
            j++
        }
        if (close < 0) return null
        val action = unescape(entry.substring(open + 1, close))
        if (action.isEmpty()) return null
        return Binding(normalize(mods), key, action)
    }

    private fun String.intField(name: String): Int? {
        val idx = indexOf("\"$name\"")
        if (idx < 0) return null
        val colon = indexOf(':', idx + name.length + 2)
        if (colon < 0) return null
        var j = colon + 1
        while (j < length && this[j].isWhitespace()) j++
        var end = j
        if (end < length && this[end] == '-') end++
        while (end < length && this[end].isDigit()) end++
        if (end == j || (end == j + 1 && this[j] == '-')) return null
        return substring(j, end).toIntOrNull()
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun unescape(s: String): String =
        s.replace("\\\"", "\"").replace("\\\\", "\\")
}

/**
 * Context-bound persistence and gate checks for the shortcut bindings (F8).
 * Deliberately thin — every decision lives in the pure [ShortcutBindings]
 * above so it can be unit-tested on the JVM; this object only touches
 * SharedPreferences.
 */
object ShortcutBindingsStore {
    /** Stored custom bindings; empty when absent or malformed (defaults then apply). */
    fun load(context: android.content.Context): List<ShortcutBindings.Binding> =
        ShortcutBindings.fromJson(readRaw(context))

    fun save(context: android.content.Context, bindings: List<ShortcutBindings.Binding>) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(ShortcutBindings.PREF_KEY, ShortcutBindings.toJson(bindings))
            .apply()
    }

    /** Factory reset: an absent key means "use the fixed defaults". */
    fun clear(context: android.content.Context) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .remove(ShortcutBindings.PREF_KEY)
            .apply()
    }

    /**
     * Whether the action's gate switch allows dispatch. The catalog's
     * prefKey/defaultEnabled pair mirrors the "enable_*" SwitchPreferenceCompat
     * entries in preferences_keyboard.xml / preferences_advanced.xml, so a
     * custom binding never bypasses a toggle the user turned off.
     */
    fun isActionEnabled(
        context: android.content.Context,
        action: ShortcutBindings.ShortcutAction
    ): Boolean {
        val pref = action.prefKey ?: return true
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(pref, action.defaultEnabled)
    }

    private fun readRaw(context: android.content.Context): String? =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getString(ShortcutBindings.PREF_KEY, null)
}
