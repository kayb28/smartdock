package android.app;

interface IActivityManager {

    List<android.app.ActivityManager.RunningTaskInfo> getTasks(int maxNum);

    void resizeTask(int taskId, in Rect bounds, int resizeMode);

    boolean removeTask(int taskId);

    // Force-stop a package's process (upstream #123). userId is 0 for the
    // system user; the wrapper degrades to false if the call throws.
    void forceStopPackage(String packageName, int userId);
}