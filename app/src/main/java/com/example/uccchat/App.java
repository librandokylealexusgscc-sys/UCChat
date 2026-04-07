package com.example.uccchat;

import android.app.Application;
import android.app.Activity;
import android.os.Bundle;

import com.cloudinary.android.MediaManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Cloudinary init
        Map<String, String> config = new HashMap<>();
        config.put("cloud_name", "dpuunread");
        config.put("api_key", "467575647571159");
        config.put("api_secret", "LQIHQaLTgg5veQ-K-fFilIafW98");
        MediaManager.init(this, config);

        // ✅ Track app foreground/background for presence
        registerActivityLifecycleCallbacks(
                new ActivityLifecycleCallbacks() {
                    private int activityCount = 0;

                    @Override
                    public void onActivityStarted(Activity a) {
                        activityCount++;
                        if (activityCount == 1) {
                            // App came to foreground
                            setOnlineStatus(true);
                        }
                    }

                    @Override
                    public void onActivityStopped(Activity a) {
                        activityCount--;
                        if (activityCount == 0) {
                            // App went to background
                            setOnlineStatus(false);
                        }
                    }

                    @Override public void onActivityCreated(Activity a, Bundle b) {}
                    @Override public void onActivityResumed(Activity a) {}
                    @Override public void onActivityPaused(Activity a) {}
                    @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
                    @Override public void onActivityDestroyed(Activity a) {}
                });
    }

    public static void setOnlineStatus(boolean isOnline) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;
        if (uid == null) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("isOnline",  isOnline);
        updates.put("lastSeen",  new Date()); // always update lastSeen

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .update(updates);
    }
}