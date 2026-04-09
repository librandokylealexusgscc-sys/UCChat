package com.example.uccchat;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

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

        // Navigation
        btnTabChats.setOnClickListener(v -> startActivity(new Intent(this, ChatActivity.class)));
        btnTabSearch.setOnClickListener(v -> startActivity(new Intent(this, SearchActivity.class)));
        btnTabMenu.setOnClickListener(v -> startActivity(new Intent(this, MenuActivity.class)));
    }

    private void loadUserData() {
        db.collection("users").document(userId).get()
                .addOnSuccessListener(doc -> {
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

    private void updateProfile() {
        String fullname = etFullname.getText().toString().trim();
        // Split fullname back into firstName and lastName
        String firstName, lastName;
        int spaceIndex = fullname.lastIndexOf(" ");
        if (spaceIndex > 0) {
            firstName = fullname.substring(0, spaceIndex).trim();
            lastName  = fullname.substring(spaceIndex + 1).trim();
        } else {
            firstName = fullname;
            lastName  = "";
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("firstName", firstName);
        updates.put("lastName",  lastName);
        updates.put("username",  etUsername.getText().toString().trim());
        updates.put("phone",     etPhoneNumber.getText().toString().trim());
        updates.put("email",     etEmail.getText().toString().trim());
        updates.put("studentId", etStudentId.getText().toString().trim());
        updates.put("course", spinnerProgram.getSelectedItem().toString());// etProgram → course

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
