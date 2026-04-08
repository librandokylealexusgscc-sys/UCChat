package com.example.uccchat;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class DeleteAccountActivity extends AppCompatActivity {

    private Button btnCancel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.deleteaccount);

        // connect button from XML
        btnCancel = findViewById(R.id.btnCancel);

        // go back to MenuActivity when clicked
        btnCancel.setOnClickListener(v -> {

            finish();
        });
    }
}
