package cu.axel.smartdock.dialogs

import android.view.KeyEvent
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import cu.axel.smartdock.R
import cu.axel.smartdock.utils.KeyboardModifiers
import cu.axel.smartdock.utils.ShortcutBindings
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Capture dialog of the shortcut editor (F8): records the hardware-keyboard
 * chord the user wants to bind to an action.
 *
 * Modifier machine: AltGr handling is [KeyboardModifiers.isAltGrPassThrough]
 * reused verbatim — an AltGr chord (right Alt, or the Ctrl+Alt the IME
 * delivers) is consumed here and never recorded, so typing "@" keeps working
 * with no side effect (#167) and no Ctrl+Alt binding can be created by
 * pressing keys. Meta needs no [cu.axel.smartdock.utils.MetaMenuTracker]
 * here: the app-menu-on-release behavior (#267) lives in DockService and has
 * no equivalent inside a dialog, so there is nothing to distinguish.
 *
 * Every key event is consumed (return true) while the dialog is open —
 * captured or not — so recording a chord never types into anything behind
 * the dialog.
 */
class ShortcutCaptureDialog(
    private val context: android.content.Context,
    private val title: String,
    private val currentChord: String,
    /** Conflicting action label for the chord, or null when it is free. */
    private val conflictOf: (mods: Int, keyCode: Int) -> String?,
    private val onSave: (mods: Int, keyCode: Int) -> Unit
) {
    private var mods = 0
    private var keyCode = 0

    fun show() {
        val view = android.view.LayoutInflater.from(context)
            .inflate(R.layout.shortcut_capture_dialog, null)
        val captureTv = view.findViewById<TextView>(R.id.shortcut_capture_tv)
        val errorTv = view.findViewById<TextView>(R.id.shortcut_error_tv)
        val currentTv = view.findViewById<TextView>(R.id.shortcut_current_tv)
        currentTv.text = context.getString(R.string.shortcut_editor_current, currentChord)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        captureTv.setOnKeyListener { _, keyCode, event ->
            // AltGr is a typing chord, not a dock chord: pass through
            // untouched (consumed here so "@" does not leak anywhere).
            if (KeyboardModifiers.isAltGrPassThrough(
                    event.action, event.metaState, event.isAltPressed, event.isCtrlPressed
                )
            )
                return@setOnKeyListener true

            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                if (ShortcutBindings.isModifierKey(keyCode)) {
                    // Modifier key itself: show the chord being held so far.
                    mods = modsOf(event)
                    this.keyCode = 0
                    showPending(captureTv, errorTv)
                } else {
                    mods = modsOf(event)
                    this.keyCode = keyCode
                    showPending(captureTv, errorTv)
                }
            }
            true
        }

        dialog.setOnShowListener {
            val save = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            save.isEnabled = false
            save.setOnClickListener {
                if (keyCode <= 0) return@setOnClickListener
                val conflict = conflictOf(mods, keyCode)
                if (conflict != null) {
                    errorTv.text = context.getString(
                        R.string.shortcut_editor_conflict, conflict
                    )
                    return@setOnClickListener
                }
                onSave(mods, keyCode)
                dialog.dismiss()
            }
            captureTv.requestFocus()
        }
        dialog.show()
    }

    private fun modsOf(event: KeyEvent): Int = ShortcutBindings.modsOf(
        event.isCtrlPressed, event.isAltPressed, event.isShiftPressed, event.isMetaPressed
    )

    /**
     * Live feedback: the chord recorded so far, or the reason the editor
     * refuses it — inline, never a dead binding.
     */
    private fun showPending(captureTv: TextView, errorTv: TextView) {
        if (keyCode <= 0) {
            if (mods == 0) captureTv.text = context.getString(R.string.shortcut_editor_capture_hint)
            else captureTv.text = ShortcutBindings.formatChord(mods, 0).removeSuffix("+")
            errorTv.text = ""
            return
        }
        captureTv.text = ShortcutBindings.formatChord(mods, keyCode)
        when (ShortcutBindings.validate(mods, keyCode)) {
            ShortcutBindings.Validation.OK -> errorTv.text = ""
            ShortcutBindings.Validation.ALTGR_RESERVED -> errorTv.text =
                context.getString(R.string.shortcut_editor_altgr_reserved)
            ShortcutBindings.Validation.NO_MODIFIER -> errorTv.text =
                context.getString(R.string.shortcut_editor_needs_modifier)
            ShortcutBindings.Validation.NO_KEY -> errorTv.text = ""
        }
    }
}
