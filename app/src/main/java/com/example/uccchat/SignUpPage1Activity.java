package com.example.uccchat;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
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
    private Spinner spinnerCourse;
    private TextView tvStudentIdError, tvCourseError;

    // First item is a prompt, the rest are actual courses
    private final String[] COURSES = {
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
        spinnerCourse      = findViewById(R.id.spinnerCourse);
        tvStudentIdError   = findViewById(R.id.tvStudentIdError);
        tvCourseError      = findViewById(R.id.tvCourseError);

        // Set up Spinner adapter
        ArrayAdapter<String> courseAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                COURSES
        );
        courseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCourse.setAdapter(courseAdapter);

        // Clear course error when user selects a real course
        spinnerCourse.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    tvCourseError.setVisibility(View.GONE);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        clearErrorOnType(etUsername,        tilUsername);
        clearErrorOnType(etPassword,        tilPassword);
        clearErrorOnType(etConfirmPassword, tilConfirmPassword);
        clearErrorOnType(etLastName,        tilLastName);
        clearErrorOnType(etFirstName,       tilFirstName);
        clearErrorOnType(etPhone,           tilPhone);
        clearErrorOnType(etEmail,           tilEmail);
        clearErrorOnType(etStudentId,       tilStudentId);

        // Pre-fill fields from Google if applicable
        if (UserSession.isFromGoogle) {
            if (UserSession.firstName != null) etFirstName.setText(UserSession.firstName);
            if (UserSession.lastName  != null) etLastName.setText(UserSession.lastName);
            if (UserSession.email     != null) etEmail.setText(UserSession.email);
        }
        // Pre-fill fields from Facebook if applicable
        if (UserSession.isFromFacebook) {
            if (UserSession.firstName != null) etFirstName.setText(UserSession.firstName);
            if (UserSession.lastName  != null) etLastName.setText(UserSession.lastName);
            if (UserSession.email     != null) etEmail.setText(UserSession.email);

            if (UserSession.facebookEmailAlreadyExists) {
                // Show error on the email field so user knows to change it or log in instead
                tilEmail.setError("This email is already registered. Please use a different email or log in with your existing account.");
                tilEmail.requestFocus();
            }
        }

        // Restore course selection if returning from back navigation
        if (UserSession.course != null) {
            for (int i = 0; i < COURSES.length; i++) {
                if (COURSES[i].equals(UserSession.course)) {
                    spinnerCourse.setSelection(i);
                    break;
                }
            }
        }

        MaterialButton btnNext = findViewById(R.id.btnNext);
        btnNext.setOnClickListener(v -> {
            if (validateFields()) {
                UserSession.username  = getText(etUsername);
                UserSession.password  = getText(etPassword);
                UserSession.lastName  = getText(etLastName);
                UserSession.firstName = getText(etFirstName);
                UserSession.phone     = getText(etPhone);
                UserSession.email     = getText(etEmail);
                UserSession.studentId = getText(etStudentId);
                UserSession.course    = spinnerCourse.getSelectedItem().toString();

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

        // Phone
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

        // Student ID
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

        // Course — position 0 is the "Select course" prompt
        if (spinnerCourse.getSelectedItemPosition() == 0) {
            tvCourseError.setVisibility(View.VISIBLE);
            valid = false;
        } else {
            tvCourseError.setVisibility(View.GONE);
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