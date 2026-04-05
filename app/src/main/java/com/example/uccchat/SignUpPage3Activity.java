package com.example.uccchat;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SignUpPage3Activity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private MaterialButton btnSubmit;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.signup_page_3);
        FirebaseApp.initializeApp(this);

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        TextView tvFullName    = findViewById(R.id.tvFullName);
        TextView tvUsername    = findViewById(R.id.tvUsername);
        TextView tvStudentId   = findViewById(R.id.tvStudentId);
        TextView tvCourse      = findViewById(R.id.tvCourse);
        TextView tvEmail       = findViewById(R.id.tvEmail);
        TextView tvPhone       = findViewById(R.id.tvPhone);
        ImageView ivProfilePic = findViewById(R.id.ivProfilePic);
        btnSubmit              = findViewById(R.id.btnSubmit);

        tvFullName.setText(UserSession.firstName + " " + UserSession.lastName);
        tvUsername.setText("@" + UserSession.username);
        tvStudentId.setText(UserSession.studentId);
        tvCourse.setText("Course: " + UserSession.course);  // ✅ Display selected course
        tvEmail.setText("Email: " + UserSession.email);
        tvPhone.setText("Phone Number: " + UserSession.phone);

        if (UserSession.profilePicUri != null) {
            Glide.with(this)
                    .load(UserSession.profilePicUri)
                    .transform(new CircleCrop())
                    .into(ivProfilePic);
            ivProfilePic.setVisibility(View.VISIBLE);

        } else if (UserSession.googlePhotoUrl != null) {
            Glide.with(this)
                    .load(UserSession.googlePhotoUrl)
                    .transform(new CircleCrop())
                    .into(ivProfilePic);
            ivProfilePic.setVisibility(View.VISIBLE);

        } else {
            ivProfilePic.setVisibility(View.GONE);
        }

        findViewById(R.id.btnGoBack).setOnClickListener(v -> finish());
        btnSubmit.setOnClickListener(v -> registerUser());
    }

    private void registerUser() {
        btnSubmit.setEnabled(false);

        mAuth.createUserWithEmailAndPassword(UserSession.email, UserSession.password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    UserSession.firebaseUid = uid;

                    if (UserSession.profilePicUri != null) {
                        uploadLocalPhotoAndSaveUser(uid);
                    } else if (UserSession.googlePhotoUrl != null) {
                        downloadGooglePhotoAndUpload(uid, UserSession.googlePhotoUrl);
                    } else {
                        saveUserToFirestore(uid, null);
                    }
                })
                .addOnFailureListener(e -> {
                    btnSubmit.setEnabled(true);
                    Toast.makeText(this,
                            "Registration failed: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void downloadGooglePhotoAndUpload(String uid, String googlePhotoUrl) {
        executor.execute(() -> {
            try {
                URL url = new URL(googlePhotoUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.connect();

                InputStream inputStream = connection.getInputStream();
                File tempFile = new File(getCacheDir(), "google_profile_" + uid + ".jpg");
                FileOutputStream outputStream = new FileOutputStream(tempFile);

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.close();
                inputStream.close();

                mainHandler.post(() -> uploadFileToCloudinary(uid, tempFile));

            } catch (Exception e) {
                mainHandler.post(() -> {
                    Toast.makeText(SignUpPage3Activity.this,
                            "Could not fetch Google photo, saving without photo.",
                            Toast.LENGTH_SHORT).show();
                    saveUserToFirestore(uid, null);
                });
            }
        });
    }

    private void uploadFileToCloudinary(String uid, File file) {
        MediaManager.get()
                .upload(file.getAbsolutePath())
                .option("public_id", "profile_pictures/" + uid)
                .option("upload_preset", "ucchat_profiles")
                .callback(new UploadCallback() {
                    @Override public void onStart(String requestId) {}
                    @Override public void onProgress(String requestId, long bytes, long totalBytes) {}

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        String cloudinaryUrl = (String) resultData.get("secure_url");
                        file.delete();
                        saveUserToFirestore(uid, cloudinaryUrl);
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        file.delete();
                        Toast.makeText(SignUpPage3Activity.this,
                                "Photo upload failed, saving without photo.",
                                Toast.LENGTH_SHORT).show();
                        saveUserToFirestore(uid, null);
                    }

                    @Override public void onReschedule(String requestId, ErrorInfo error) {}
                })
                .dispatch();
    }

    private void uploadLocalPhotoAndSaveUser(String uid) {
        MediaManager.get()
                .upload(UserSession.profilePicUri)
                .option("public_id", "profile_pictures/" + uid)
                .option("upload_preset", "ucchat_profiles")
                .callback(new UploadCallback() {
                    @Override public void onStart(String requestId) {}
                    @Override public void onProgress(String requestId, long bytes, long totalBytes) {}

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        String photoUrl = (String) resultData.get("secure_url");
                        saveUserToFirestore(uid, photoUrl);
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        Toast.makeText(SignUpPage3Activity.this,
                                "Photo upload failed, saving without photo.",
                                Toast.LENGTH_SHORT).show();
                        saveUserToFirestore(uid, null);
                    }

                    @Override public void onReschedule(String requestId, ErrorInfo error) {}
                })
                .dispatch();
    }

    private void saveUserToFirestore(String uid, String photoUrl) {
        Map<String, Object> user = new HashMap<>();
        user.put("username",  UserSession.username);
        user.put("firstName", UserSession.firstName);
        user.put("lastName",  UserSession.lastName);
        user.put("phone",     UserSession.phone);
        user.put("email",     UserSession.email);
        user.put("studentId", UserSession.studentId);
        user.put("course",    UserSession.course);   // ✅ Save course to Firestore
        user.put("photoUrl",  photoUrl);

        db.collection("users").document(uid)
                .set(user)
                .addOnSuccessListener(unused -> {
                    btnSubmit.setEnabled(true);
                    Toast.makeText(SignUpPage3Activity.this,
                            "Account created! Welcome, " + UserSession.firstName + "! 🎉",
                            Toast.LENGTH_SHORT).show();

                    clearSession();

                    Intent intent = new Intent(SignUpPage3Activity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnSubmit.setEnabled(true);
                    Toast.makeText(SignUpPage3Activity.this,
                            "Failed to save user data: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void clearSession() {
        UserSession.username       = null;
        UserSession.password       = null;
        UserSession.firstName      = null;
        UserSession.lastName       = null;
        UserSession.phone          = null;
        UserSession.email          = null;
        UserSession.studentId      = null;
        UserSession.course         = null;
        UserSession.firebaseUid    = null;
        UserSession.profilePicUri  = null;
        UserSession.googlePhotoUrl = null;
        UserSession.isFromGoogle   = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}