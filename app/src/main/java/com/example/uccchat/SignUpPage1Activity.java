package com.example.uccchat;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseApp;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SignUpPage1Activity extends AppCompatActivity {

    private TextInputLayout tilUsername, tilPassword, tilConfirmPassword,
            tilLastName, tilFirstName, tilPhone, tilEmail, tilStudentId;
    private TextInputEditText etUsername, etPassword, etConfirmPassword,
            etLastName, etFirstName, etPhone, etEmail, etStudentId;
    private Spinner spinnerDepartment, spinnerCourse;
    private TextView tvStudentIdError, tvCourseError, tvDepartmentError;

    private static final int MIN_YEAR = 2020;

    private final LinkedHashMap<String, String[]> DEPARTMENT_COURSES =
            new LinkedHashMap<String, String[]>() {{
                put("COLLEGE OF BUSINESS AND ACCOUNTANCY", new String[]{
                        "Select course",
                        "Bachelor of Science in Accountancy",
                        "Bachelor of Science in Accounting Information System",
                        "Bachelor of Science in Business Administration, Major in Financial Management",
                        "Bachelor of Science in Business Administration, Major in Human Resource Management",
                        "Bachelor of Science in Business Administration, Major in Marketing Management",
                        "Bachelor of Science in Entrepreneurship",
                        "Bachelor of Science in Hospitality Management",
                        "Bachelor of Science in Office Administration",
                        "Bachelor of Science in Tourism Management"
                });
                put("COLLEGE OF CRIMINAL JUSTICE EDUCATION", new String[]{
                        "Select course",
                        "Bachelor of Science in Criminology",
                        "Bachelor of Science in Industrial Security Management"
                });
                put("COLLEGE OF EDUCATION", new String[]{
                        "Select course",
                        "Bachelor in Secondary Education Major in English",
                        "Bachelor in Secondary Education Major in English - Chinese",
                        "Bachelor in Secondary Education Major in Science",
                        "Bachelor in Secondary Education Major in Technology and Livelihood Education",
                        "Bachelor of Early Childhood Education"
                });
                put("COLLEGE OF ENGINEERING", new String[]{
                        "Select course",
                        "Bachelor of Science in Computer Engineering",
                        "Bachelor of Science in Electrical Engineering",
                        "Bachelor of Science in Electronics Engineering",
                        "Bachelor of Science in Industrial Engineering"
                });
                put("COLLEGE OF LAW", new String[]{
                        "Select course",
                        "Law"
                });
                put("COLLEGE OF LIBERAL ARTS AND SCIENCES", new String[]{
                        "Select course",
                        "AB Political Science",
                        "BA Communication",
                        "Bachelor of Public Administration",
                        "Bachelor of Public Administration (SPECIAL PROGRAM)",
                        "Bachelor of Science in Computer Science",
                        "Bachelor of Science in Entertainment and Multimedia Computing",
                        "Bachelor of Science in Information System",
                        "Bachelor of Science in Information Technology",
                        "Bachelor of Science in Mathematics",
                        "Bachelor of Science in Psychology"
                });
            }};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.signup_page_1);
        FirebaseApp.initializeApp(this);

        // ── Bind views ────────────────────────────────────────
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

        spinnerDepartment  = findViewById(R.id.spinnerDepartment);
        spinnerCourse      = findViewById(R.id.spinnerCourse);

        tvStudentIdError   = findViewById(R.id.tvStudentIdError);
        tvCourseError      = findViewById(R.id.tvCourseError);
        tvDepartmentError  = findViewById(R.id.tvDepartmentError);

        // ── Department Spinner ────────────────────────────────
        List<String> departments = new ArrayList<>();
        departments.add("Select department");
        departments.addAll(DEPARTMENT_COURSES.keySet());

        ArrayAdapter<String> deptAdapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, departments) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView tv = view.findViewById(android.R.id.text1);
                tv.setTextColor(0xFF000000);
                tv.setBackgroundColor(0xFFFFFFFF);
                tv.setTextSize(14f);
                tv.setPadding(16, 0, 16, 0);
                return view;
            }
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView tv = view.findViewById(android.R.id.text1);
                tv.setTextColor(0xFF000000);
                tv.setBackgroundColor(0xFFFFFFFF);
                tv.setTextSize(14f);
                tv.setPadding(32, 24, 32, 24);
                return view;
            }
        };
        deptAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDepartment.setAdapter(deptAdapter);

        updateCourseSpinner(null);

        spinnerDepartment.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int position, long id) {
                if (position > 0) {
                    tvDepartmentError.setVisibility(View.GONE);
                    updateCourseSpinner(departments.get(position));
                } else {
                    updateCourseSpinner(null);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerCourse.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int position, long id) {
                if (position > 0) tvCourseError.setVisibility(View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // ── Clear errors on type ──────────────────────────────
        clearErrorOnType(etUsername,        tilUsername);
        clearErrorOnType(etPassword,        tilPassword);
        clearErrorOnType(etConfirmPassword, tilConfirmPassword);
        clearErrorOnType(etLastName,        tilLastName);
        clearErrorOnType(etFirstName,       tilFirstName);
        clearErrorOnType(etPhone,           tilPhone);
        clearErrorOnType(etEmail,           tilEmail);
        clearErrorOnType(etStudentId,       tilStudentId);

        // ── Pre-fill from Google ──────────────────────────────
        if (UserSession.isFromGoogle) {
            if (UserSession.firstName != null) etFirstName.setText(UserSession.firstName);
            if (UserSession.lastName  != null) etLastName.setText(UserSession.lastName);
            if (UserSession.email     != null) etEmail.setText(UserSession.email);
        }

        // ── Pre-fill from Facebook ────────────────────────────
        if (UserSession.isFromFacebook) {
            if (UserSession.firstName != null) etFirstName.setText(UserSession.firstName);
            if (UserSession.lastName  != null) etLastName.setText(UserSession.lastName);
            if (UserSession.email     != null) etEmail.setText(UserSession.email);
            if (UserSession.facebookEmailAlreadyExists) {
                tilEmail.setError("This email is already registered.");
                tilEmail.requestFocus();
            }
        }

        // ── Restore session on back navigation ────────────────
        if (UserSession.course != null) {
            for (Map.Entry<String, String[]> entry : DEPARTMENT_COURSES.entrySet()) {
                String[] courses = entry.getValue();
                for (int i = 1; i < courses.length; i++) {
                    if (courses[i].equals(UserSession.course)) {
                        String dept = entry.getKey();
                        int deptIndex = departments.indexOf(dept);
                        if (deptIndex >= 0) spinnerDepartment.setSelection(deptIndex);
                        updateCourseSpinner(dept);
                        final int courseIndex = i;
                        spinnerCourse.post(() -> spinnerCourse.setSelection(courseIndex));
                        break;
                    }
                }
            }
        }

        // ── Next Button ───────────────────────────────────────
        MaterialButton btnNext = findViewById(R.id.btnNext);
        btnNext.setOnClickListener(v -> {
            if (!validateFields()) return;

            btnNext.setEnabled(false);
            btnNext.setText("Checking...");

            String enteredUsername  = getText(etUsername);
            String enteredStudentId = getText(etStudentId);
            String enteredFirstName = getText(etFirstName);
            String enteredLastName  = getText(etLastName);
            String enteredEmail     = getText(etEmail);
            String enteredCourse    = spinnerCourse.getSelectedItem().toString();

            final boolean[] usernameTaken   = {false};
            final boolean[] studentIdTaken  = {false};
            final boolean[] nameCourseTaken = {false};
            final boolean[] emailTaken      = {false};
            final int[] checksComplete      = {0};
            final int TOTAL_CHECKS          = 4;

            Runnable onAllChecksComplete = () -> {
                checksComplete[0]++;
                if (checksComplete[0] < TOTAL_CHECKS) return;

                runOnUiThread(() -> {
                    btnNext.setEnabled(true);
                    btnNext.setText("Next");

                    boolean hasError = false;

                    if (usernameTaken[0]) {
                        tilUsername.setErrorEnabled(true);
                        tilUsername.setError("Username is already taken");
                        hasError = true;
                    }
                    if (studentIdTaken[0]) {
                        tilStudentId.setErrorEnabled(true);
                        tilStudentId.setError("Student ID is already registered");
                        hasError = true;
                    }
                    if (nameCourseTaken[0]) {
                        tilFirstName.setErrorEnabled(true);
                        tilFirstName.setError(
                                "An account with this name and course already exists");
                        tilLastName.setErrorEnabled(true);
                        tilLastName.setError(
                                "An account with this name and course already exists");
                        hasError = true;
                    }
                    if (emailTaken[0]) {
                        tilEmail.setErrorEnabled(true);
                        tilEmail.setError(
                                "This email is already registered. Please use a different email.");
                        hasError = true;
                    }

                    if (!hasError) {
                        UserSession.username  = enteredUsername;
                        UserSession.password  = getText(etPassword);
                        UserSession.lastName  = enteredLastName;
                        UserSession.firstName = enteredFirstName;
                        UserSession.phone     = getText(etPhone);
                        UserSession.email     = enteredEmail;
                        UserSession.studentId = enteredStudentId;
                        UserSession.course    = enteredCourse;
                        startActivity(new Intent(SignUpPage1Activity.this,
                                SignUpPage2Activity.class));
                    }
                });
            };

            com.google.firebase.firestore.FirebaseFirestore db =
                    com.google.firebase.firestore.FirebaseFirestore.getInstance();

            db.collection("users").whereEqualTo("username", enteredUsername)
                    .limit(1).get()
                    .addOnSuccessListener(snap -> {
                        usernameTaken[0] = !snap.isEmpty();
                        onAllChecksComplete.run();
                    })
                    .addOnFailureListener(e -> onAllChecksComplete.run());

            db.collection("users").whereEqualTo("studentId", enteredStudentId)
                    .limit(1).get()
                    .addOnSuccessListener(snap -> {
                        studentIdTaken[0] = !snap.isEmpty();
                        onAllChecksComplete.run();
                    })
                    .addOnFailureListener(e -> onAllChecksComplete.run());

            db.collection("users")
                    .whereEqualTo("firstName", enteredFirstName)
                    .whereEqualTo("lastName",  enteredLastName)
                    .whereEqualTo("course",    enteredCourse)
                    .limit(1).get()
                    .addOnSuccessListener(snap -> {
                        nameCourseTaken[0] = !snap.isEmpty();
                        onAllChecksComplete.run();
                    })
                    .addOnFailureListener(e -> onAllChecksComplete.run());

            db.collection("users").whereEqualTo("email", enteredEmail)
                    .limit(1).get()
                    .addOnSuccessListener(snap -> {
                        emailTaken[0] = !snap.isEmpty();
                        onAllChecksComplete.run();
                    })
                    .addOnFailureListener(e -> onAllChecksComplete.run());
        });
    }

    // ════════════════════════════════════════════════════════
    //  COURSE SPINNER
    // ════════════════════════════════════════════════════════

    private void updateCourseSpinner(String department) {
        String[] courses;
        if (department == null || !DEPARTMENT_COURSES.containsKey(department)) {
            courses = new String[]{"Select course"};
        } else {
            courses = DEPARTMENT_COURSES.get(department);
        }

        ArrayAdapter<String> courseAdapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, courses) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView tv = view.findViewById(android.R.id.text1);
                tv.setTextColor(0xFF000000);
                tv.setBackgroundColor(0xFFFFFFFF);
                tv.setTextSize(14f);
                tv.setPadding(16, 0, 16, 0);
                return view;
            }
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView tv = view.findViewById(android.R.id.text1);
                tv.setTextColor(0xFF000000);
                tv.setBackgroundColor(0xFFFFFFFF);
                tv.setTextSize(14f);
                tv.setPadding(32, 24, 32, 24);
                return view;
            }
        };
        courseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCourse.setAdapter(courseAdapter);
    }

    // ════════════════════════════════════════════════════════
    //  VALIDATION
    // ════════════════════════════════════════════════════════

    private boolean validateFields() {
        boolean valid = true;

        if (isEmpty(etUsername)) {
            tilUsername.setError("Username is required");
            valid = false;
        }

        String password = getText(etPassword);
        if (password.isEmpty()) {
            tilPassword.setError("Password is required");
            valid = false;
        } else if (password.length() < 8) {
            tilPassword.setError("Password must be at least 8 characters");
            valid = false;
        } else if (!password.matches(".*[A-Z].*")) {
            tilPassword.setError("Must contain at least 1 uppercase letter");
            valid = false;
        } else if (!password.matches(".*[a-z].*")) {
            tilPassword.setError("Must contain at least 1 lowercase letter");
            valid = false;
        } else if (!password.matches(".*[0-9].*")) {
            tilPassword.setError("Must contain at least 1 number");
            valid = false;
        }

        if (isEmpty(etConfirmPassword)) {
            tilConfirmPassword.setError("Please confirm your password");
            valid = false;
        } else if (!getText(etPassword).equals(getText(etConfirmPassword))) {
            tilConfirmPassword.setError("Passwords do not match");
            valid = false;
        }

        if (isEmpty(etLastName)) {
            tilLastName.setError("Last name is required");
            valid = false;
        } else if (!getText(etLastName).matches("^[A-Za-z\\s]+$")) {
            tilLastName.setError("Last name must contain letters only");
            valid = false;
        }

        if (isEmpty(etFirstName)) {
            tilFirstName.setError("First name is required");
            valid = false;
        } else if (!getText(etFirstName).matches("^[A-Za-z\\s]+$")) {
            tilFirstName.setError("First name must contain letters only");
            valid = false;
        }

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

        if (isEmpty(etEmail)) {
            tilEmail.setError("Email is required");
            valid = false;
        } else if (!android.util.Patterns.EMAIL_ADDRESS
                .matcher(getText(etEmail)).matches()) {
            tilEmail.setError("Please enter a valid email address");
            valid = false;
        }

        // ── Student ID ────────────────────────────────────────
        String studentId = getText(etStudentId).toUpperCase();
        if (studentId.isEmpty()) {
            tilStudentId.setError("Student ID is required");
            tvStudentIdError.setVisibility(View.VISIBLE);
            valid = false;
        } else if (!studentId.matches("\\d{8}-[CNSB]")) {
            tilStudentId.setError("Format: 20240391-C  (ends with C, N, S, or B)");
            tvStudentIdError.setVisibility(View.VISIBLE);
            valid = false;
        } else {
            int startYear   = Integer.parseInt(studentId.substring(0, 4));
            int currentYear = Calendar.getInstance().get(Calendar.YEAR);

            if (startYear < MIN_YEAR) {
                tilStudentId.setError(
                        "Alumni accounts are not allowed. " +
                                "Only students enrolled from " + MIN_YEAR +
                                " onwards can register.");
                tvStudentIdError.setText(
                        "Your Student ID year (" + startYear + ") is below " +
                                MIN_YEAR + ". Alumni cannot sign up.");
                tvStudentIdError.setVisibility(View.VISIBLE);
                valid = false;
            } else if (startYear > currentYear) {
                tilStudentId.setError(
                        "Invalid year. Starting year cannot be in the future.");
                tvStudentIdError.setVisibility(View.VISIBLE);
                valid = false;
            } else {
                tvStudentIdError.setVisibility(View.GONE);
            }
        }

        if (spinnerDepartment.getSelectedItemPosition() == 0) {
            tvDepartmentError.setVisibility(View.VISIBLE);
            valid = false;
        } else {
            tvDepartmentError.setVisibility(View.GONE);
        }

        if (spinnerCourse.getSelectedItemPosition() == 0) {
            tvCourseError.setVisibility(View.VISIBLE);
            valid = false;
        } else {
            tvCourseError.setVisibility(View.GONE);
        }

        return valid;
    }

    // ════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════

    private void clearErrorOnType(TextInputEditText et, TextInputLayout til) {
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                til.setError(null);
                if (til.getId() == R.id.tilStudentId) {
                    tvStudentIdError.setVisibility(View.GONE);
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private boolean isEmpty(TextInputEditText et) { return getText(et).isEmpty(); }

    private String getText(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }
}