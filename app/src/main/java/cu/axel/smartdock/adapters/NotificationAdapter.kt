package cu.axel.smartdock.adapters

import android.app.Notification
import android.app.PendingIntent.CanceledException
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import cu.axel.smartdock.R
import cu.axel.smartdock.utils.AppUtils
import cu.axel.smartdock.utils.ColorUtils
import cu.axel.smartdock.utils.Utils

class NotificationAdapter(
    private val context: Context,
    private var notifications: Array<StatusBarNotification>,
    private val listener: OnNotificationClickListener
) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {
    private var sharedPreferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)
    private val actionsHeight = Utils.dpToPx(context, 20)

    interface OnNotificationClickListener {
        fun onNotificationClicked(notification: StatusBarNotification, item: View)
        fun onNotificationLongClicked(notification: StatusBarNotification, item: View)
        fun onNotificationCancelClicked(notification: StatusBarNotification, item: View)
    }

    override fun onCreateViewHolder(parent: ViewGroup, arg1: Int): ViewHolder {
        val itemLayoutView = LayoutInflater.from(parent.context).inflate(
            R.layout.notification_entry, parent, false
        )
        return ViewHolder(itemLayoutView)
    }

    // F12: una accion con RemoteInput necesita texto antes de enviar el PendingIntent.
    // Mandarlo vacio era un no-op silencioso: la app recibia un reply sin contenido.
    private fun dispatchAction(action: Notification.Action, holder: ViewHolder) {
        val inputs = action.remoteInputs
        if (inputs != null && inputs.isNotEmpty()) showReplyInput(holder, action, inputs[0])
        else sendAction(action, null)
    }

    // Nunca fallar en silencio: si el PendingIntent ya no es valido, se dice.
    private fun sendAction(action: Notification.Action, results: Intent?) {
        try {
            if (results != null) action.actionIntent.send(context, 0, results)
            else action.actionIntent.send()
        } catch (_: CanceledException) {
            Toast.makeText(context, R.string.notification_action_failed, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(context, R.string.notification_action_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showReplyInput(
        holder: ViewHolder,
        action: Notification.Action,
        remoteInput: RemoteInput
    ) {
        val row = holder.notifActionsLayout
        row.removeAllViews()
        val input = EditText(context)
        input.hint = context.getString(R.string.notification_reply_hint)
        input.setTextColor(Color.WHITE)
        input.setHintTextColor(Color.GRAY)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        input.imeOptions = EditorInfo.IME_ACTION_SEND
        input.isSingleLine = true
        input.minHeight = Utils.dpToPx(context, 36)
        val send = TextView(context)
        send.text = context.getString(R.string.notification_send)
        send.setTextColor(context.getColor(R.color.action))
        val pad = Utils.dpToPx(context, 8)
        send.setPadding(pad, 0, pad, 0)
        val inputParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT)
        inputParams.weight = 1f
        row.addView(input, inputParams)
        row.addView(
            send,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        val submit = {
            val text = input.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(context, R.string.notification_reply_empty, Toast.LENGTH_SHORT).show()
            } else {
                val results = Bundle()
                results.putCharSequence(remoteInput.resultKey, text)
                val intent = Intent()
                RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, results)
                sendAction(action, intent)
                val position = holder.adapterPosition
                if (position != RecyclerView.NO_POSITION) notifyItemChanged(position)
            }
        }
        send.setOnClickListener { submit() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submit()
                true
            } else false
        }
        input.requestFocus()
    }

    private fun addTextAction(
        action: Notification.Action,
        holder: ViewHolder,
        params: LinearLayout.LayoutParams
    ) {
        val actionTv = TextView(context)
        actionTv.setTextColor(context.getColor(R.color.action))
        actionTv.isSingleLine = true
        actionTv.text = action.title
        actionTv.setOnClickListener { dispatchAction(action, holder) }
        holder.notifActionsLayout.addView(actionTv, params)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        // updateNotifications swaps the array before RecyclerView processes
        // the notify call; a bind in between arrives with a stale position
        // and used to crash the dock with an IndexOutOfBoundsException
        // (the crash class named in #253).
        if (position < 0 || position >= notifications.size) return
        val sbn = notifications[position]
        val notification = sbn.notification
        val actions = notification.actions
        val extras = notification.extras
        viewHolder.notifActionsLayout.removeAllViews()
        // la fila puede crecer cuando se abre el campo de respuesta
        val rowParams = viewHolder.notifActionsLayout.layoutParams
        rowParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        viewHolder.notifActionsLayout.layoutParams = rowParams
        if (actions != null) {
            val actionLayoutParams = LinearLayout.LayoutParams(0, actionsHeight)
            actionLayoutParams.weight = 1f
            if (AppUtils.isMediaNotification(notification)) {
                for (action in actions) {
                    // Action.icon is a deprecated Int resource id: loadDrawable does not
                    // exist on Int ("Unresolved reference"), which is why the call must go
                    // through getIcon() (public API 23+, resolves resource, bitmap or URI
                    // icons). The previous getIdentifier(icon.toString()) lookup always
                    // returned 0 for a numeric string, so getDrawable(0) threw
                    // NotFoundException and every media action degraded to text silently.
                    // Keep the try/catch as a shield: if the icon cannot be resolved the
                    // action falls back to text instead of crashing the dock.
                    val drawable = try {
                        action.getIcon()?.loadDrawable(context)
                    } catch (_: Exception) {
                        null
                    }
                    if (drawable != null) {
                        val actionIv = ImageView(context)
                        drawable.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP)
                        actionIv.setImageDrawable(drawable)
                        actionIv.setOnClickListener { dispatchAction(action, viewHolder) }
                        viewHolder.notifText.isSingleLine = true
                        viewHolder.notifActionsLayout.addView(actionIv, actionLayoutParams)
                    } else {
                        addTextAction(action, viewHolder, actionLayoutParams)
                    }
                }
            } else {
                for (action in actions) addTextAction(action, viewHolder, actionLayoutParams)
            }
        }
        var notificationTitle = extras.getString(Notification.EXTRA_TITLE)
        if (notificationTitle == null) notificationTitle =
            AppUtils.getPackageLabel(context, sbn.packageName)
        val notificationText = extras.getCharSequence(Notification.EXTRA_TEXT)
        val progress = extras.getInt(Notification.EXTRA_PROGRESS)
        val formattedProgress = if (progress != 0) " $progress%" else ""
        viewHolder.notifTitle.text = notificationTitle + formattedProgress
        viewHolder.notifText.text = notificationText
        if (sbn.isClearable) {
            viewHolder.notifCancelBtn.alpha = 1f
            viewHolder.notifCancelBtn.setOnClickListener { view ->
                listener.onNotificationCancelClicked(sbn, view)
            }
        } else viewHolder.notifCancelBtn.alpha = 0f

        if (AppUtils.isMediaNotification(notification) && notification.getLargeIcon() != null) {
            val padding = Utils.dpToPx(context, 0)
            viewHolder.notifIcon.setPadding(padding, padding, padding, padding)
            viewHolder.notifIcon.setImageIcon(notification.getLargeIcon())
        } else {
            notification.smallIcon.setTint(Color.WHITE)
            viewHolder.notifIcon.setBackgroundResource(R.drawable.circle)
            ColorUtils.applySecondaryColor(context, sharedPreferences, viewHolder.notifIcon)
            val padding = Utils.dpToPx(context, 14)
            viewHolder.notifIcon.setPadding(padding, padding, padding, padding)
            viewHolder.notifIcon.setImageIcon(notification.smallIcon)
        }

        viewHolder.bind(sbn, listener)
    }

    override fun getItemCount(): Int {
        return notifications.size
    }

    fun updateNotifications(newNotifications: Array<StatusBarNotification>) {
        notifications = newNotifications
        notifyDataSetChanged()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var notifIcon: ImageView = itemView.findViewById(R.id.notification_icon_iv)
        var notifCancelBtn: ImageView = itemView.findViewById(R.id.notification_close_btn)
        var notifTitle: TextView = itemView.findViewById(R.id.notification_title_tv)
        var notifText: TextView = itemView.findViewById(R.id.notification_text_tv)
        var notifActionsLayout: LinearLayout = itemView.findViewById(R.id.notification_actions_layout)

        fun bind(notification: StatusBarNotification, listener: OnNotificationClickListener) {
            itemView.setOnClickListener { view ->
                listener.onNotificationClicked(notification, view)
            }
            itemView.setOnLongClickListener { view ->
                listener.onNotificationLongClicked(notification, view)
                true
            }
        }
    }
}
