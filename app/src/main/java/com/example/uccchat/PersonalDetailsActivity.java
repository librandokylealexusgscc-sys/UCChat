package com.example.uccchat;


import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;
import android.net.Uri;
import android.widget.ImageView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Map;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class PersonalDetailsActivity extends AppCompatActivity {

    private EditText etFullname, etUsername, etPhoneNumber, etEmail, etStudentId;
    private Spinner spinnerProgram;
    private Button btnUpdateProfile;
    private LinearLayout btnTabChats, btnTabSearch, btnTabMenu;

    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private String userId;
    private final String[] PROGRAMS = {
            "Select course",
            "Information Technology",
            "Computer Science",
            "Psychology",
            "Education",
            "Nursing",
            "Accountancy",
            "Business Administration",
            "Civil Engineering",
            "Architecture",
            "Criminology",
            "Social Work",
            "Tourism Management"
    };
    private ImageButton btnProfilePicture;
    private Uri newProfilePicUri = null;
    private String currentPhotoUrl = null;
    private boolean isNavigating = false;

    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    newProfilePicUri = uri;
                    Glide.with(this)
                            .load(uri)
                            .transform(new CircleCrop())
                            .into(btnProfilePicture);
                }
            });
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.personaldetails);


        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        userId = currentUser.getUid();

        etFullname    = findViewById(R.id.etFullname);
        etUsername     = findViewById(R.id.etUsername);
        etPhoneNumber = findViewById(R.id.etPhoneNumber);
        etEmail       = findViewById(R.id.etEmail);
        etStudentId   = findViewById(R.id.etStudentId);
        spinnerProgram = findViewById(R.id.spinnerProgram);
        btnUpdateProfile = findViewById(R.id.btnUpdateProfile);

        btnTabChats  = findViewById(R.id.btnTabChats);
        btnTabSearch = findViewById(R.id.btnTabSearch);
        btnTabMenu   = findViewById(R.id.btnTabMenu);
        btnProfilePicture = findViewById(R.id.btnProfilePicture);
        setListeners();

        btnProfilePicture.setOnClickListener(v -> {
            imagePickerLauncher.launch("image/*");
        });
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                PROGRAMS
        );

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProgram.setAdapter(adapter);

        // Load user data from Firestore
        loadUserData();

        // Update profile on click
        btnUpdateProfile.setOnClickListener(v -> {
            if (validateFields()) {
                updateProfile();
            }
        });


    }

    private void setListeners() {
        btnTabChats.setOnClickListener(v ->
                navigateTo(ChatHomeActivity.class, false));

        btnTabSearch.setOnClickListener(v ->
                navigateTo(SearchActivity.class, false));

        btnTabMenu.setOnClickListener(v -> {
            // already here
        });
    }
    private void navigateTo(Class<?> destination, boolean clearStack) {
        if (isNavigating) return;
        if (this.getClass().equals(destination)) return;
        isNavigating = true;

        Intent intent = new Intent(this, destination);

        if (clearStack) {
            intent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK
            );
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        }

        startActivity(intent);

        if (!clearStack) overridePendingTransition(0, 0);
    }

    private void loadUserData() {
        db.collection("users").document(userId).get()
                .addOnSuccessListener(doc -> {
                    currentPhotoUrl = doc.getString("photoUrl");
                    if (currentPhotoUrl != null && !currentPhotoUrl.isEmpty()) {
                        if (currentPhotoUrl != null && !currentPhotoUrl.isEmpty()) {
                            Glide.with(this)
                                    .load(currentPhotoUrl + "?t=" + System.currentTimeMillis())
                                    .transform(new CircleCrop())
                                    .placeholder(R.drawable.bg_circle_gray)
                                    .into(btnProfilePicture);
                        }
                    }
                    if (doc.exists()) {
                        // Combine firstName + lastName into fullname
                        String first = doc.getString("firstName") != null ? doc.getString("firstName") : "";
                        String last  = doc.getString("lastName") != null ? doc.getString("lastName") : "";
                        etFullname.setText((first + " " + last).trim());

                        etUsername.setText(doc.getString("username"));
                        etPhoneNumber.setText(doc.getString("phone"));
                        etEmail.setText(doc.getString("email"));
                        etStudentId.setText(doc.getString("studentId"));
                        String savedCourse = doc.getString("course");

                        if (savedCourse != null) {
                            for (int i = 0; i < PROGRAMS.length; i++) {
                                if (PROGRAMS[i].equals(savedCourse)) {
                                    spinnerProgram.setSelection(i);
                                    break;
                                }
                            }
                        } // course → etProgram
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show()
                );
    }
    private File uriToFile(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            File file = new File(getCacheDir(), "profile_" + userId + ".jpg");
            FileOutputStream outputStream = new FileOutputStream(file);

            byte[] buffer = new byte[4096];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            outputStream.close();
            inputStream.close();

            return file;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    private void updateProfile() {
        if (newProfilePicUri != null) {

            File file = uriToFile(newProfilePicUri);

            if (file == null) {
                Toast.makeText(this, "File is null!", Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(this, "Uploading image...", Toast.LENGTH_SHORT).show();

            MediaManager.get()
                    .upload(file.getAbsolutePath())
                    .option("public_id", "profile_pictures/" + userId)
                    .option("overwrite", true)
                    .option("invalidate", true)
                    .option("upload_preset", "ucchat_profiles")
                    .callback(new UploadCallback() {

                        @Override
                        public void onStart(String requestId) {
                            Log.d("UPLOAD", "Started");
                        }

                        @Override
                        public void onProgress(String requestId, long bytes, long totalBytes) {}

                        @Override
                        public void onSuccess(String requestId, Map resultData) {
                            String newUrl = (String) resultData.get("secure_url");

                            currentPhotoUrl = newUrl;

                            Glide.with(PersonalDetailsActivity.this)
                                    .load(newUrl + "?t=" + System.currentTimeMillis())
                                    .transform(new CircleCrop())
                                    .into(btnProfilePicture);

                            db.collection("users").document(userId)
                                    .update("photoUrl", newUrl)
                                    .addOnSuccessListener(unused -> {
                                        Toast.makeText(PersonalDetailsActivity.this,
                                                "Profile updated!", Toast.LENGTH_SHORT).show();
                                    });
                        }
                        @Override
                        public void onError(String requestId, ErrorInfo error) {
                            Log.e("UPLOAD", "FAILED: " + error.getDescription());
                            Toast.makeText(PersonalDetailsActivity.this,
                                    "Upload failed: " + error.getDescription(),
                                    Toast.LENGTH_LONG).show();
                        }

                        @Override
                        public void onReschedule(String requestId, ErrorInfo error) {}
                    })
                    .dispatch();

        } else {
            Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveUpdatesToFirestore(Map<String, Object> updates) {
        db.collection("users").document(userId).update(updates)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, MenuActivity.class));
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }
    private boolean validateFields() {
        boolean valid = true;

        String fullname = etFullname.getText().toString().trim();
        String username = etUsername.getText().toString().trim();
        String phone    = etPhoneNumber.getText().toString().trim();
        String email    = etEmail.getText().toString().trim();
        String studentId = etStudentId.getText().toString().trim();
        if (spinnerProgram.getSelectedItemPosition() == 0) {
            Toast.makeText(this,
                    "This program is not covered by UCC.",
                    Toast.LENGTH_SHORT).show();
            valid = false;
        }
        else if (!fullname.matches("^[A-Za-z ]+$")) {
            etFullname.setError("Fullname must contain letters only");
            valid = false;
        }

        // USERNAME
        if (TextUtils.isEmpty(username)) {
            etUsername.setError("Username is required");
            valid = false;
        }

        // PHONE NUMBER VALIDATION
        if (TextUtils.isEmpty(phone)) {
            etPhoneNumber.setError("Phone number is required");
            valid = false;
        }
        else if (!phone.matches("^09\\d{9}$")) {
            etPhoneNumber.setError("Must be 11 digits starting with 09");
            valid = false;
        }

        // EMAIL VALIDATION
        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Email is required");
            valid = false;
        }
        else if (!email.matches("^[A-Za-z][A-Za-z0-9._%+-]*@(gmail|yahoo|outlook|hotmail)\\.com$")) {
            etEmail.setError("Invalid email format. Example: juan@gmail.com");
            valid = false;
        }

        // STUDENT ID VALIDATION
        if (TextUtils.isEmpty(studentId)) {
            etStudentId.setError("Student ID is required");
            valid = false;
        }
        else if (!studentId.matches("^\\d{4}\\d{4}-[CNS]$")) {
            etStudentId.setError("Format must be: 20240391-C");
            valid = false;
        }

        // PROGRAM VALIDATION (like course selector)
        if (spinnerProgram.getSelectedItemPosition() == 0) {
            Toast.makeText(this,
                    "This program is not covered by UCC.",
                    Toast.LENGTH_SHORT).show();
            valid = false;
        }
        return valid;
    }


}
