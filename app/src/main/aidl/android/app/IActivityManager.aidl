package android.app;

interface IActivityManager {

    List<android.app.ActivityManager.RunningTaskInfo> getTasks(int maxNum);

    void resizeTask(int taskId, in Rect bounds, int resizeMode);

    // Current bounds of a task: lets the dock remember a per-app window
    // geometry (fork F7) from a window the user already placed. Hidden
    // framework API like resizeTask above; the wrapper degrades to null.
    Rect getTaskBounds(int taskId);

    boolean removeTask(int taskId);

    // Force-stop a package's process (upstream #123). userId is 0 for the
    // system user; the wrapper degrades to false if the call throws.
    void forceStopPackage(String packageName, int userId);
}