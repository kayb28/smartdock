package cu.axel.smartdock.utils

/**
 * Named timers used across the dock, gathered in one place so a delay is not
 * a magic number buried in a call site and its rationale lives next to its
 * value.
 *
 * The boot-recovery timers ([CHECK_DELAY_MS], [REBIND_DELAY_MS]) were
 * previously private to [cu.axel.smartdock.receivers.BootCompletedReceiver];
 * they moved here so the screenshot-capture delay that prompted this object
 * sits beside its peers. Timers that only make sense next to their owner (the
 * launcher's service-start grace, the permissions recheck in MainActivity)
 * stay there; this object holds the cross-cutting ones.
 */
object Timers {
    /**
     * Delay between hiding the quick-settings panel (and the dock) and firing
     * `GLOBAL_ACTION_TAKE_SCREENSHOT`, so the compositor has retired the frame
     * that still showed the panel (#266). Without it the capture photographed
     * the previous frame — the panel itself.
     *
     * 400 ms is deliberately generous: SurfaceFlinger normally retires a frame
     * in one vsync (~16 ms at 60 Hz), but the screenshot pipeline then needs to
     * read the composited buffer, and on slow devices or under load that read
     * can lag by a couple of frames. A smaller margin (say 100 ms) reproduced
     * the bug on a mid-range device in testing; 400 ms was the smallest value
     * that held across the devices tried. Bumping it further only slows the
     * capture; it does not affect the restored dock, which is shown again
     * after [SCREENSHOT_RESTORE_DELAY_MS] regardless.
     */
    const val SCREENSHOT_CAPTURE_DELAY_MS = 400L

    /**
     * Delay before restoring the dock/handle after the system screenshot flash.
     * Long enough that the flash animation has finished on every device seen;
     * any user show/hide started in the meantime cancels the restore.
     */
    const val SCREENSHOT_RESTORE_DELAY_MS = 1500L

    /**
     * Grace before re-querying the running-task stack after launching an app:
     * the launched app needs a moment to reach the top of the stack, otherwise
     * the strip would still show the previous foreground task.
     */
    const val RUNNING_TASKS_REFRESH_MS = 1200L

    /** Time for the system to bind the accessibility service on its own after boot. */
    const val CHECK_DELAY_MS = 30_000L

    /**
     * Time the dock stays dropped before being re-added: too short and the
     * system may coalesce the two enabled-services writes into a no-op.
     */
    const val REBIND_DELAY_MS = 2_000L
}
