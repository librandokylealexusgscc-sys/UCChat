package com.example.uccchat;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseApp;

public class SignUpPage1Activity extends AppCompatActivity {

    private TextInputLayout tilUsername, tilPassword, tilConfirmPassword,
            tilLastName, tilFirstName, tilPhone, tilEmail, tilStudentId;
    private TextInputEditText etUsername, etPassword, etConfirmPassword,
            etLastName, etFirstName, etPhone, etEmail, etStudentId;
    private TextView tvStudentIdError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.signup_page_1);
        FirebaseApp.initializeApp(this);

        tilUsername        = findViewById(R.id.tilUsername);
        tilPassword        = findViewById(R.id.tilPassword);
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword);
        tilLastName        = findViewById(R.id.tilLastName);
        tilFirstName       = findViewById(R.id.tilFirstName);
        tilPhone           = findViewById(R.id.tilPhone);
        tilEmail           = findViewById(R.id.tilEmail);
        tilStudentId       = findViewById(R.id.tilStudentId);

        etUsername         = findViewById(R.id.etUsername);
        etPassword         = findViewById(R.id.etPassword);
        etConfirmPassword  = findViewById(R.id.etConfirmPassword);
        etLastName         = findViewById(R.id.etLastName);
        etFirstName        = findViewById(R.id.etFirstName);
        etPhone            = findViewById(R.id.etPhone);
        etEmail            = findViewById(R.id.etEmail);
        etStudentId        = findViewById(R.id.etStudentId);
        tvStudentIdError   = findViewById(R.id.tvStudentIdError);

        clearErrorOnType(etUsername,        tilUsername);
        clearErrorOnType(etPassword,        tilPassword);
        clearErrorOnType(etConfirmPassword, tilConfirmPassword);
        clearErrorOnType(etLastName,        tilLastName);
        clearErrorOnType(etFirstName,       tilFirstName);
        clearErrorOnType(etPhone,           tilPhone);
        clearErrorOnType(etEmail,           tilEmail);
        clearErrorOnType(etStudentId,       tilStudentId);

        // ✅ FIX: Pre-fill fields HERE in onCreate so they are ready before the user taps Next
        if (UserSession.isFromGoogle) {
            if (UserSession.firstName != null) etFirstName.setText(UserSession.firstName);
            if (UserSession.lastName  != null) etLastName.setText(UserSession.lastName);
            if (UserSession.email     != null) etEmail.setText(UserSession.email);
        }

        MaterialButton btnNext = findViewById(R.id.btnNext);
        btnNext.setOnClickListener(v -> {
            if (validateFields()) {
                // Save all form values into UserSession
                UserSession.username  = getText(etUsername);
                UserSession.password  = getText(etPassword);
                UserSession.lastName  = getText(etLastName);
                UserSession.firstName = getText(etFirstName);
                UserSession.phone     = getText(etPhone);
                UserSession.email     = getText(etEmail);
                UserSession.studentId = getText(etStudentId);

                startActivity(new Intent(SignUpPage1Activity.this, SignUpPage2Activity.class));
            }
        });
    }

    private boolean validateFields() {
        boolean valid = true;

        // Username
        if (isEmpty(etUsername)) {
            tilUsername.setError("Username is required");
            valid = false;
        }

        // Password
        String password = getText(etPassword);
        if (password.isEmpty()) {
            tilPassword.setError("Password is required");
            valid = false;
        } else if (password.length() < 8) {
            tilPassword.setError("Password must be at least 8 characters");
            valid = false;
        } else if (!password.matches(".*[A-Z].*")) {
            tilPassword.setError("Password must contain at least 1 uppercase letter");
            valid = false;
        } else if (!password.matches(".*[a-z].*")) {
            tilPassword.setError("Password must contain at least 1 lowercase letter");
            valid = false;
        } else if (!password.matches(".*[0-9].*")) {
            tilPassword.setError("Password must contain at least 1 number");
            valid = false;
        }

        // Confirm Password
        if (isEmpty(etConfirmPassword)) {
            tilConfirmPassword.setError("Please confirm your password");
            valid = false;
        } else if (!getText(etPassword).equals(getText(etConfirmPassword))) {
            tilConfirmPassword.setError("Passwords do not match");
            valid = false;
        }

        // Last Name
        if (isEmpty(etLastName)) {
            tilLastName.setError("Last name is required");
            valid = false;
        }

        // First Name
        if (isEmpty(etFirstName)) {
            tilFirstName.setError("First name is required");
            valid = false;
        }

        // Phone — must start with 09 and be exactly 11 digits
        String phone = getText(etPhone);
        if (phone.isEmpty()) {
            tilPhone.setError("Phone number is required");
            valid = false;
        } else if (!phone.startsWith("09")) {
            tilPhone.setError("Phone number must start with 09");
            valid = false;
        } else if (phone.length() != 11) {
            tilPhone.setError("Phone number must be exactly 11 digits");
            valid = false;
        } else if (!phone.matches("[0-9]+")) {
            tilPhone.setError("Phone number must contain digits only");
            valid = false;
        }

        // Email
        if (isEmpty(etEmail)) {
            tilEmail.setError("Email is required");
            valid = false;
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(getText(etEmail)).matches()) {
            tilEmail.setError("Please enter a valid email address");
            valid = false;
        }

        // Student ID — must follow format: 8 digits, a hyphen, then 1 uppercase letter (e.g. 20240391-C)
        String studentId = getText(etStudentId);
        if (studentId.isEmpty()) {
            tilStudentId.setError("Student ID is required");
            tvStudentIdError.setVisibility(View.VISIBLE);
            valid = false;
        } else if (!studentId.matches("\\d{8}-[A-Z]")) {
            tilStudentId.setError("Invalid format. Use: 20240391-C");
            tvStudentIdError.setVisibility(View.VISIBLE);
            valid = false;
        } else {
            tvStudentIdError.setVisibility(View.GONE);
        }

        return valid;
    }

    private void clearErrorOnType(TextInputEditText et, TextInputLayout til) {
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                til.setError(null);
                if (til.getId() == R.id.tilStudentId) {
                    tvStudentIdError.setVisibility(View.GONE);
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private boolean isEmpty(TextInputEditText et) {
        return getText(et).isEmpty();
    }

    private String getText(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }
}