package cu.axel.smartdock.dialogs

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import cu.axel.smartdock.R
import cu.axel.smartdock.utils.ShortcutBindings
import cu.axel.smartdock.utils.ShortcutBindingsStore
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Editor dialog of the shortcut editor (F8): one row per customizable action
 * with its effective chord (custom when the user replaced it, the fixed
 * default otherwise), plus a factory-reset entry. Tapping a row opens the
 * [ShortcutCaptureDialog]; saving stores the binding through
 * [ShortcutBindingsStore] and the list refreshes in place.
 *
 * Dispatch is "bindings first, fixed defaults as fallback" (DockService), so
 * a custom binding on a default chord simply shadows that chord — the other
 * defaults keep working, and factory reset (storage cleared) brings every
 * default back.
 */
object ShortcutEditorDialog {
    private val LABELS = mapOf(
        "lock_desktop" to R.string.shortcut_action_lock_desktop,
        "open_settings" to R.string.shortcut_action_open_settings,
        "open_terminal" to R.string.shortcut_action_open_terminal,
        "expand_notifications" to R.string.shortcut_action_expand_notifications,
        "toggle_pin" to R.string.shortcut_action_toggle_pin,
        "open_music" to R.string.shortcut_action_open_music,
        "open_browser" to R.string.shortcut_action_open_browser,
        "open_assist" to R.string.shortcut_action_open_assist,
        "open_rec" to R.string.shortcut_action_open_rec,
        "go_home" to R.string.shortcut_action_go_home,
        "toggle_keyboard" to R.string.shortcut_action_toggle_keyboard,
        "soft_reboot" to R.string.shortcut_action_soft_reboot,
        "window_portrait" to R.string.shortcut_action_window_portrait,
        "window_maximize" to R.string.shortcut_action_window_maximize,
        "window_tiled_left" to R.string.shortcut_action_window_tiled_left,
        "window_tiled_right" to R.string.shortcut_action_window_tiled_right,
        "window_standard" to R.string.shortcut_action_window_standard,
        "new_instance" to R.string.shortcut_action_new_instance,
        "send_display_1" to R.string.shortcut_action_send_display,
        "send_display_2" to R.string.shortcut_action_send_display,
        "send_display_3" to R.string.shortcut_action_send_display,
        "send_display_4" to R.string.shortcut_action_send_display
    )

    fun show(context: Context) {
        var listDialog: AlertDialog? = null
        var rows = buildRows(context)

        fun refresh(adapter: ArrayAdapter<String>) {
            rows = buildRows(context)
            adapter.clear()
            adapter.addAll(rows.map { it.first + "\n" + it.second })
            adapter.notifyDataSetChanged()
        }

        val adapter = ArrayAdapter(
            context, android.R.layout.simple_list_item_2,
            rows.map { it.first + "\n" + it.second }
        )

        listDialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.shortcut_editor_title)
            .setAdapter(adapter) { _, which ->
                if (which >= rows.size) {
                    // The factory-reset entry below the action rows.
                    MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.shortcut_editor_reset)
                        .setMessage(R.string.shortcut_editor_reset_confirm)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            ShortcutBindingsStore.clear(context)
                            refresh(adapter)
                            Toast.makeText(
                                context, R.string.shortcut_editor_reset_done,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                } else {
                    val action = ShortcutBindings.ShortcutAction.entries[which]
                    showCapture(context, action) { refresh(adapter) }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** (label, effective chord) for every action, in catalog order. */
    private fun buildRows(context: Context): List<Pair<String, String>> {
        val bindings = ShortcutBindingsStore.load(context)
        return ShortcutBindings.ShortcutAction.entries.map { action ->
            val label = label(context, action)
            label to ShortcutBindings.displayChord(action, bindings)
        }
    }

    private fun showCapture(
        context: Context,
        action: ShortcutBindings.ShortcutAction,
        onSaved: () -> Unit
    ) {
        val bindings = ShortcutBindingsStore.load(context)

        // A rebinding must not conflict with the action's own current chord;
        // any other action already holding the chord wins the error message.
        fun conflictOf(mods: Int, keyCode: Int): String? {
            val others = bindings.filter { it.action != action.id }
            val hit = ShortcutBindings.conflictOf(others, mods, keyCode) ?: return null
            val id = hit.action
            val other = ShortcutBindings.ShortcutAction.fromId(id) ?: return null
            return label(context, other)
        }

        ShortcutCaptureDialog(
            context,
            label(context, action),
            ShortcutBindings.displayChord(action, bindings),
            ::conflictOf,
            { mods, keyCode ->
                val updated = bindings
                    .filter { it.action != action.id }
                    .plus(ShortcutBindings.Binding(mods, keyCode, action.id))
                ShortcutBindingsStore.save(context, updated)
                Toast.makeText(context, R.string.shortcut_editor_saved, Toast.LENGTH_SHORT).show()
                onSaved()
            }
        ).show()
    }

    private fun label(context: Context, action: ShortcutBindings.ShortcutAction): String {
        val res = LABELS[action.id] ?: return action.id
        return if (action.id.startsWith("send_display_"))
            context.getString(res, action.id.removePrefix("send_display_"))
        else context.getString(res)
    }
}
