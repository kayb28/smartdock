package cu.axel.smartdock.adapters

import android.content.Context
import android.net.wifi.ScanResult
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.preference.PreferenceManager
import cu.axel.smartdock.R
import cu.axel.smartdock.utils.ColorUtils

class WifiScanResultAdapter(context: Context, scanResults: List<ScanResult>): ArrayAdapter<ScanResult>(context, R.layout.pin_entry, scanResults) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var convertView = convertView
        if (convertView == null) convertView = LayoutInflater.from(context).inflate(R.layout.pin_entry, null)
        val nameTv = convertView!!.findViewById<TextView>(R.id.pin_entry_tv)
        val icon = convertView.findViewById<ImageView>(R.id.pin_entry_iv)
        icon.setImageResource(R.drawable.ic_wifi_on)
        ColorUtils.applySecondaryColor(context, PreferenceManager.getDefaultSharedPreferences(context), icon)
        val scanResult = getItem(position)
        nameTv.text = scanResult!!.SSID
        return convertView
    }
}