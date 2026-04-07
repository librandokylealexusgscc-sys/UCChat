package com.example.uccchat;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        FirebaseApp.initializeApp(this);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                // ✅ Already logged in → go straight to chat home
                startActivity(new Intent(MainActivity.this, ChatHomeActivity.class));
            } else {
                // ✅ Not logged in → go to welcome/sign up
                startActivity(new Intent(MainActivity.this, WelcomePageActivity.class));
            }
            finish();
        }, 2000);
    }
}