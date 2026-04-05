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

    // Used to run network work off the main thread
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler   = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.signup_page_3);

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        TextView tvFullName    = findViewById(R.id.tvFullName);
        TextView tvUsername    = findViewById(R.id.tvUsername);
        TextView tvStudentId   = findViewById(R.id.tvStudentId);
        TextView tvEmail       = findViewById(R.id.tvEmail);
        TextView tvPhone       = findViewById(R.id.tvPhone);
        ImageView ivProfilePic = findViewById(R.id.ivProfilePic);
        btnSubmit              = findViewById(R.id.btnSubmit);

        tvFullName.setText(UserSession.firstName + " " + UserSession.lastName);
        tvUsername.setText("@" + UserSession.username);
        tvStudentId.setText(UserSession.studentId);
        tvEmail.setText("Email: " + UserSession.email);
        tvPhone.setText("Phone Number: " + UserSession.phone);

        // Show preview of whichever photo is available
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
                        // User manually picked a local photo — upload to Cloudinary
                        uploadLocalPhotoAndSaveUser(uid);

                    } else if (UserSession.googlePhotoUrl != null) {
                        // ✅ FIX: Download Google photo first, then upload to Cloudinary
                        downloadGooglePhotoAndUpload(uid, UserSession.googlePhotoUrl);

                    } else {
                        // No photo at all
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

    // ✅ NEW: Download the Google photo to a temp file, then upload that file to Cloudinary
    private void downloadGooglePhotoAndUpload(String uid, String googlePhotoUrl) {
        executor.execute(() -> {
            try {
                // Step 1: Download the Google photo into a temp file
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

                // Step 2: Upload the downloaded file to Cloudinary
                mainHandler.post(() -> uploadFileToCloudinary(uid, tempFile));

            } catch (Exception e) {
                // Download failed — save without photo
                mainHandler.post(() -> {
                    Toast.makeText(SignUpPage3Activity.this,
                            "Could not fetch Google photo, saving without photo.",
                            Toast.LENGTH_SHORT).show();
                    saveUserToFirestore(uid, null);
                });
            }
        });
    }

    // Upload a local File object to Cloudinary
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
                        // Clean up temp file
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

    // Upload a local URI picked by the user to Cloudinary
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
        user.put("photoUrl",  photoUrl);

        db.collection("users").document(uid)
                .set(user)
                .addOnSuccessListener(unused -> {
                    btnSubmit.setEnabled(true);
                    Toast.makeText(SignUpPage3Activity.this,
                            "Account created! Welcome, " + UserSession.firstName + "! 🎉",
                            Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(SignUpPage3Activity.this, LoginActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnSubmit.setEnabled(true);
                    Toast.makeText(SignUpPage3Activity.this,
                            "Failed to save user data: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}