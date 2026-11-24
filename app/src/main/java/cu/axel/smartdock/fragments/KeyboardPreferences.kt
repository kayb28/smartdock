package cu.axel.smartdock.fragments

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import cu.axel.smartdock.R
import cu.axel.smartdock.dialogs.ShortcutEditorDialog

class KeyboardPreferences : PreferenceFragmentCompat() {
    override fun onCreatePreferences(arg0: Bundle?, arg1: String?) {
        setPreferencesFromResource(R.xml.preferences_keyboard, arg1)
        // F8: the shortcut editor opens from the keyboard screen; capture
        // dialogs are shown over the settings activity, not as overlays.
        findPreference<Preference>("shortcut_editor")?.setOnPreferenceClickListener {
            ShortcutEditorDialog.show(requireActivity())
            true
        }
    }
}
