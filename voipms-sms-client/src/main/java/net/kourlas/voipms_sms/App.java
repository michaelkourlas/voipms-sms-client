package net.kourlas.voipms_sms;

import android.app.Activity;

public class App {
    private static App instance = null;
    private Activity currentActivity;

    public App() {
        currentActivity = null;
    }

    public static App getInstance() {
        if (instance == null) {
            instance = new App();
        }
        return instance;
    }

    public Activity getCurrentActivity() {
        return currentActivity;
    }

    public void setCurrentActivity(Activity currentActivity) {
        this.currentActivity = currentActivity;
    }

    public void deleteReferenceToActivity(Activity activity) {
        if (activity.equals(currentActivity)) {
            currentActivity = null;
        }
    }
}
