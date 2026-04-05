package com.example.uccchat;

import android.app.Application;
import com.cloudinary.android.MediaManager;
import java.util.HashMap;
import java.util.Map;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        Map<String, String> config = new HashMap<>();
        config.put("cloud_name", "dpuunread");
        config.put("api_key", "467575647571159");
        config.put("api_secret", "LQIHQaLTgg5veQ-K-fFilIafW98");
        MediaManager.init(this, config);
    }
}