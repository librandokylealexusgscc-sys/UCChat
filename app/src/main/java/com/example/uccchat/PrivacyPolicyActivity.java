package com.example.uccchat;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class PrivacyPolicyActivity extends AppCompatActivity {

    private Button btnOK;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.privacypolicy);

        // connect button
        btnOK = findViewById(R.id.btnOK);

        // go to Menu screen when clicked
        btnOK.setOnClickListener(v -> {

            finish();
        });
    }
}