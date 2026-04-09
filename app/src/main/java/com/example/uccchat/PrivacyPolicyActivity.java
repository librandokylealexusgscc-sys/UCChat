package com.example.uccchat;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ScrollView;

import androidx.appcompat.app.AppCompatActivity;

public class PrivacyPolicyActivity extends AppCompatActivity {

    private Button btnOK;
    private ScrollView scrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.privacypolicy);

        btnOK      = findViewById(R.id.btnOK);
        scrollView = findViewById(R.id.scrollView);

        // Disabled until user scrolls to the bottom
        btnOK.setEnabled(false);
        btnOK.setAlpha(0.4f);

        scrollView.getViewTreeObserver().addOnScrollChangedListener(() -> {
            int scrollY    = scrollView.getScrollY();
            int height     = scrollView.getHeight();
            int totalHeight = scrollView.getChildAt(0).getMeasuredHeight();

            if (scrollY + height >= totalHeight - 10) {
                btnOK.setEnabled(true);
                btnOK.setAlpha(1.0f);
            }
        });

        btnOK.setOnClickListener(v -> finish());
    }
}