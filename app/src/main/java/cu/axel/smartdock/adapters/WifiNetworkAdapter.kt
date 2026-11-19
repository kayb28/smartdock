package cu.axel.smartdock.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.ScanResult
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import cu.axel.smartdock.R

/**
 * Rows of the own Wi-Fi networks panel (B2): SSID + signal level. The list is
 * fed sorted by the caller (strongest first) and only unique SSIDs.
 */
class WifiNetworkAdapter(private val context: Context, networks: List<ScanResult>) :
    ArrayAdapter<ScanResult>(context, R.layout.wifi_network_entry, networks) {

    @SuppressLint("MissingPermission")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var convertView = convertView
        if (convertView == null)
            convertView = LayoutInflater.from(context).inflate(R.layout.wifi_network_entry, null)
        val icon = convertView!!.findViewById<ImageView>(R.id.wifi_network_iv)
        val text = convertView.findViewById<TextView>(R.id.wifi_network_tv)
        val network = getItem(position)!!
        icon.setImageResource(R.drawable.ic_wifi_on)
        text.text = network.SSID.ifBlank { context.getString(R.string.hidden_network) }
        return convertView
    }
}
