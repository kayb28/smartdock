package android.bluetooth;

interface IBluetoothManager {
    boolean isEnabled();
    //Api31+
    boolean enable(in android.content.AttributionSource p1);
    boolean disable(in android.content.AttributionSource p1, boolean persist);
}