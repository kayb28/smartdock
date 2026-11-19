package cu.axel.smartdock.dialogs

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import cu.axel.smartdock.R
import cu.axel.smartdock.utils.Utils

class DonateDialog(context: Context) : MaterialAlertDialogBuilder(context) {
    init {
        setTitle(R.string.donate)
        setMessage(R.string.donate_message)
        setCancelable(false)
        setNegativeButton(R.string.close) { _, _ ->
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit { putInt("launch_count", -1) }
        }
        setPositiveButton(
            R.string.donate
        ) { _, _ ->
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit { putInt("launch_count", -1) }
            Utils.openUrl(
                context,
                "https://github.com/axel358/smartdock/blob/main/README.md#support-the-project"
            )
        }
        show()
    }
}