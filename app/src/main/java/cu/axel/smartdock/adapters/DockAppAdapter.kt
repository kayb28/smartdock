package cu.axel.smartdock.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import cu.axel.smartdock.R
import cu.axel.smartdock.models.DockApp
import cu.axel.smartdock.utils.AppUtils
import cu.axel.smartdock.utils.ColorUtils
import cu.axel.smartdock.utils.IconPackUtils
import cu.axel.smartdock.utils.Utils

class DockAppAdapter(
    private val context: Context, private var apps: ArrayList<DockApp>,
    private val listener: OnDockAppClickListener, private val iconPackUtils: IconPackUtils?
) : RecyclerView.Adapter<DockAppAdapter.ViewHolder>() {
    private var iconBackground = 0
    private val iconPadding: Int
    private val iconTheming: Boolean
    private val tintIndicators: Boolean

    interface OnDockAppClickListener {
        fun onDockAppClicked(app: DockApp, view: View)
        fun onDockAppLongClicked(app: DockApp, view: View)
    }

    init {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        iconTheming = sharedPreferences.getString("icon_pack", "") != ""
        iconPadding =
            Utils.dpToPx(context, sharedPreferences.getString("icon_padding", "5")!!.toInt())
        tintIndicators = sharedPreferences.getBoolean("tint_indicators", false)
        when (sharedPreferences.getString("icon_shape", "circle")) {
            "circle" -> iconBackground = R.drawable.circle
            "round_rect" -> iconBackground = R.drawable.round_square
            "default" -> iconBackground = -1
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, arg1: Int): ViewHolder {
        val itemLayoutView =
            LayoutInflater.from(parent.context).inflate(R.layout.app_task_entry, null)
        return ViewHolder(itemLayoutView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        // A bind racing the task strip refresh can arrive with a stale
        // position; the crash report behind #253 was an unexplained
        // IndexOutOfBoundsException, so the strip skips out-of-range binds
        // instead of taking the dock down.
        if (position < 0 || position >= apps.size) return
        val app = apps[position]
        val size = app.tasks.size
        if (size > 0) {
            if (tintIndicators)
                ColorUtils.applyColor(
                    viewHolder.runningIndicator,
                    ColorUtils.manipulateColor(ColorUtils.getDrawableDominantColor(app.icon), 2f)
                )

            viewHolder.runningIndicator.alpha = if (app.tasks[0].id != -1) 1f else 0f
            viewHolder.runningIndicator.layoutParams.width =
                Utils.dpToPx(context, if (app.packageName == AppUtils.currentApp) 16 else 8)

            if (size > 1) {
                viewHolder.taskCounter.text = size.toString()
                viewHolder.taskCounter.alpha = 1f
            } else
                viewHolder.taskCounter.alpha = 0f
        } else {
            viewHolder.runningIndicator.alpha = 0f
            viewHolder.taskCounter.alpha = 0f
        }
        if (iconPackUtils != null)
            viewHolder.iconIv.setImageDrawable(iconPackUtils.getAppThemedIcon(app.packageName))
        else
            viewHolder.iconIv.setImageDrawable(app.icon)
        if (iconBackground != -1) {
            viewHolder.iconIv.setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
            viewHolder.iconIv.setBackgroundResource(iconBackground)
            ColorUtils.applyColor(
                viewHolder.iconIv,
                ColorUtils.getDrawableDominantColor(viewHolder.iconIv.drawable)
            )
        }
        viewHolder.bind(app, listener)
    }

    override fun getItemCount(): Int {
        return apps.size
    }

    fun updateApps(newApps: ArrayList<DockApp>) {
        // DiffUtil instead of notifyDataSetChanged: the task strip refreshes
        // on every window change, and rebuilding every row (with dominant
        // color extraction per icon) burned CPU for unchanged apps. The
        // old TODO noted the wasted full refresh; the 500 ms throttle in
        // updateRunningTasks stays as the outer rate limiter.
        val oldApps = apps
        apps = newApps
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldApps.size
            override fun getNewListSize() = newApps.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                oldApps[oldPos].packageName == newApps[newPos].packageName
            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                val old = oldApps[oldPos]
                val new = newApps[newPos]
                return old.packageName == new.packageName &&
                        old.tasks.size == new.tasks.size &&
                        (old.tasks.isEmpty() || old.tasks[0].id == new.tasks[0].id)
            }
        }, false)
        diff.dispatchUpdatesTo(this)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var iconIv: ImageView = itemView.findViewById(R.id.icon_iv)
        var taskCounter: TextView = itemView.findViewById(R.id.task_count_badge)
        var runningIndicator: View = itemView.findViewById(R.id.running_indicator)

        fun bind(app: DockApp, listener: OnDockAppClickListener) {
            itemView.setOnClickListener { view -> listener.onDockAppClicked(app, view) }
            itemView.setOnLongClickListener { view ->
                listener.onDockAppLongClicked(app, view)
                true
            }
            itemView.setOnTouchListener { view, event ->
                if (event.buttonState == MotionEvent.BUTTON_SECONDARY) {
                    listener.onDockAppLongClicked(app, view)
                    return@setOnTouchListener true
                }
                false
            }
        }
    }
}