package android.net.wifi;

interface IWifiManager {
    boolean setWifiEnabled(String packageName, boolean enabled);
    List<android.net.wifi.ScanResult> getScanResults(String callingPackage, String p2);
    boolean startScan(String callingPackage, String p2);
    //android.net.wifi.WifiInfo getConnectionInfo(String callingPackage, String p2);
}