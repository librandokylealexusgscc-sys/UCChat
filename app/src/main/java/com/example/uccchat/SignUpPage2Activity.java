package com.example.uccchat;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.FirebaseApp;

public class SignUpPage2Activity extends AppCompatActivity {

    private ImageView ivProfilePreview;
    private TextView tvPlusIcon;

    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    UserSession.profilePicUri = uri;
                    ivProfilePreview.setImageURI(uri);
                    ivProfilePreview.setVisibility(View.VISIBLE);
                    tvPlusIcon.setVisibility(View.GONE);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.signup_page_2);
        FirebaseApp.initializeApp(this);

        ivProfilePreview = findViewById(R.id.ivProfilePreview);
        tvPlusIcon       = findViewById(R.id.tvPlusIcon);

        FrameLayout imagePickerBox = findViewById(R.id.imagePickerBox);
        imagePickerBox.setOnClickListener(v ->
                imagePickerLauncher.launch("image/*"));

        MaterialButton btnGoBack = findViewById(R.id.btnGoBack);
        btnGoBack.setOnClickListener(v -> finish());

        MaterialButton btnContinue = findViewById(R.id.btnContinue);
        btnContinue.setOnClickListener(v ->
                startActivity(new Intent(SignUpPage2Activity.this, SignUpPage3Activity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (UserSession.isFromGoogle && UserSession.googlePhotoUrl != null) {
            tvPlusIcon.setVisibility(View.GONE);
            ivProfilePreview.setVisibility(View.VISIBLE);
            if (ivProfilePreview.getDrawable() == null) {
                new Thread(() -> {
                    try {
                        java.net.URL url = new java.net.URL(UserSession.googlePhotoUrl);
                        android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(url.openStream());
                        runOnUiThread(() -> ivProfilePreview.setImageBitmap(bitmap));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
            }
        } else if (UserSession.profilePicUri != null) {
            ivProfilePreview.setImageURI(UserSession.profilePicUri);
            ivProfilePreview.setVisibility(View.VISIBLE);
            tvPlusIcon.setVisibility(View.GONE);
        }
    }
}