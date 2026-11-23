package cu.axel.smartdock.utils

import cu.axel.smartdock.wrappers.ActivityManagerWrapper
import cu.axel.smartdock.wrappers.BluetoothManagerWrapper
import cu.axel.smartdock.wrappers.WifiManagerWrapper

/**
 * Read-only view of DockService's live state, for the diagnostics panel.
 *
 * The wrappers (`ActivityManagerWrapper`, `WifiManagerWrapper`,
 * `BluetoothManagerWrapper`) live as private fields on DockService and the
 * Settings fragment cannot reach them. DockService mirrors them here — plus
 * the `isRunning` flag set in `onServiceConnected` / `onDestroy` — so
 * [DiagnosticsBuilder] can report `isAlive()` without reaching into the
 * service instance. Mirrors `CrashLog`'s pattern: a global `object` holding
 * app-wide state that a context-less caller can read.
 *
 * DockService populates this at four points:
 *  - `initWrappers()` end  — wrapper instances (or null if construction
 *    failed or Shizuku is absent).
 *  - `destroyWrappers()`   — wrappers cleared (re-init / Reactivate).
 *  - `onServiceConnected()` — `isRunning = true`.
 *  - `onDestroy()`         — `reset()` clears everything.
 *
 * `@Volatile` because the diagnostics panel reads from the Settings thread
 * while DockService writes from its own; the values are references/primitives,
 * no compound invariants, so volatile is sufficient.
 */
object DockServiceState {
    @Volatile
    var isRunning: Boolean = false

    @Volatile
    var activityManagerWrapper: ActivityManagerWrapper? = null

    @Volatile
    var wifiManagerWrapper: WifiManagerWrapper? = null

    @Volatile
    var bluetoothManagerWrapper: BluetoothManagerWrapper? = null

    /** Clear all state. Called from DockService.onDestroy. */
    fun reset() {
        isRunning = false
        activityManagerWrapper = null
        wifiManagerWrapper = null
        bluetoothManagerWrapper = null
    }
}
