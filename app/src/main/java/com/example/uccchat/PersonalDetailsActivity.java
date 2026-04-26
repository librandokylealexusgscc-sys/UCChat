package com.example.uccchat;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.net.Uri;
import android.view.View;

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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class PersonalDetailsActivity extends AppCompatActivity {

    private EditText etFirstName, etLastName, etUsername,
            etPhoneNumber, etEmail, etStudentId;
    private Spinner spinnerProgram;
    private Button btnUpdateProfile;
    private LinearLayout btnTabChats, btnTabSearch, btnTabMenu;
    private TextView tvFirstNameEditsLeft, tvLastNameEditsLeft;
    private TextView tvCourseHint; // ✅ Course change hint

    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private String userId;

    // ── Name edit tracking ────────────────────────────────
    private int firstNameEditCount = 0;
    private int lastNameEditCount  = 0;
    private static final int MAX_NAME_EDITS = 3;
    private static final int MIN_NAME_LENGTH = 3;
    private static final int MAX_NAME_LENGTH = 36;

    // ── Original values ───────────────────────────────────
    private String originalFirstName = "";
    private String originalLastName  = "";

    // ── Course change tracking ────────────────────────────
    private Date lastCourseChangedDate = null;
    private boolean canChangeCourse    = true;

    private boolean isNavigating = false;

    private final String[] PROGRAMS = {
            "Select course",
            "Bachelor of Science in Accountancy",
            "Bachelor of Science in Accounting Information System",
            "Bachelor of Science in Business Administration, Major in Financial Management",
            "Bachelor of Science in Business Administration, Major in Human Resource Management",
            "Bachelor of Science in Business Administration, Major in Marketing Management",
            "Bachelor of Science in Entrepreneurship",
            "Bachelor of Science in Hospitality Management",
            "Bachelor of Science in Office Administration",
            "Bachelor of Science in Tourism Management",
            "Bachelor of Science in Criminology",
            "Bachelor of Science in Industrial Security Management",
            "Bachelor in Secondary Education Major in English",
            "Bachelor in Secondary Education Major in English - Chinese",
            "Bachelor in Secondary Education Major in Science",
            "Bachelor in Secondary Education Major in Technology and Livelihood Education",
            "Bachelor of Early Childhood Education",
            "Bachelor of Science in Computer Engineering",
            "Bachelor of Science in Electrical Engineering",
            "Bachelor of Science in Electronics Engineering",
            "Bachelor of Science in Industrial Engineering",
            "Law",
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
    };

    private ImageButton btnProfilePicture;
    private Uri newProfilePicUri  = null;
    private String currentPhotoUrl = null;

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

    // ════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.personaldetails);

        db          = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        userId = currentUser.getUid();

        // ── Bind views ────────────────────────────────────────
        etFirstName       = findViewById(R.id.etFirstName);
        etLastName        = findViewById(R.id.etLastName);
        etUsername        = findViewById(R.id.etUsername);
        etPhoneNumber     = findViewById(R.id.etPhoneNumber);
        etEmail           = findViewById(R.id.etEmail);
        etStudentId       = findViewById(R.id.etStudentId);
        spinnerProgram    = findViewById(R.id.spinnerProgram);
        btnUpdateProfile  = findViewById(R.id.btnUpdateProfile);
        btnTabChats       = findViewById(R.id.btnTabChats);
        btnTabSearch      = findViewById(R.id.btnTabSearch);
        btnTabMenu        = findViewById(R.id.btnTabMenu);
        btnProfilePicture = findViewById(R.id.btnProfilePicture);
        tvFirstNameEditsLeft = findViewById(R.id.tvFirstNameEditsLeft);
        tvLastNameEditsLeft  = findViewById(R.id.tvLastNameEditsLeft);
        tvCourseHint         = findViewById(R.id.tvCourseHint);

        // ✅ Student ID permanently read-only
        etStudentId.setFocusable(false);
        etStudentId.setFocusableInTouchMode(false);
        etStudentId.setClickable(false);
        etStudentId.setCursorVisible(false);
        etStudentId.setTextColor(
                getResources().getColor(R.color.text_secondary, getTheme()));

        // ✅ Spinner setup with color-aware adapter
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, PROGRAMS) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView tv = view.findViewById(android.R.id.text1);
                tv.setTextColor(getResources().getColor(
                        R.color.text_primary, getTheme()));
                return view;
            }
            @Override
            public View getDropDownView(int position, View convertView,
                                        ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView tv = view.findViewById(android.R.id.text1);
                tv.setTextColor(getResources().getColor(
                        R.color.text_primary, getTheme()));
                tv.setPadding(32, 24, 32, 24);
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProgram.setAdapter(adapter);

        setListeners();
        btnProfilePicture.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        loadUserData();

        btnUpdateProfile.setOnClickListener(v -> {
            if (validateFields()) {
                checkUniquenessAndUpdate();
            }
        });
    }

    // ════════════════════════════════════════════════════════
    //  LISTENERS
    // ════════════════════════════════════════════════════════

    private void setListeners() {
        btnTabChats.setOnClickListener(v -> navigateTo(ChatHomeActivity.class, false));
        btnTabSearch.setOnClickListener(v -> navigateTo(SearchActivity.class, false));
        btnTabMenu.setOnClickListener(v -> { });
    }

    private void navigateTo(Class<?> destination, boolean clearStack) {
        if (isNavigating) return;
        if (this.getClass().equals(destination)) return;
        isNavigating = true;
        Intent intent = new Intent(this, destination);
        if (clearStack) {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TASK);
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        }
        startActivity(intent);
        if (!clearStack) overridePendingTransition(0, 0);
    }

    // ════════════════════════════════════════════════════════
    //  LOAD USER DATA
    // ════════════════════════════════════════════════════════

    private void loadUserData() {
        db.collection("users").document(userId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;

                    currentPhotoUrl = doc.getString("photoUrl");
                    if (currentPhotoUrl != null && !currentPhotoUrl.isEmpty()) {
                        Glide.with(this)
                                .load(currentPhotoUrl + "?t=" + System.currentTimeMillis())
                                .transform(new CircleCrop())
                                .placeholder(R.drawable.bg_circle_gray)
                                .into(btnProfilePicture);
                    }

                    originalFirstName = doc.getString("firstName") != null
                            ? doc.getString("firstName") : "";
                    originalLastName  = doc.getString("lastName") != null
                            ? doc.getString("lastName") : "";

                    etFirstName.setText(originalFirstName);
                    etLastName.setText(originalLastName);
                    etUsername.setText(doc.getString("username"));
                    etPhoneNumber.setText(doc.getString("phone"));
                    etEmail.setText(doc.getString("email"));
                    etStudentId.setText(doc.getString("studentId"));

                    // ── Name edit counts ──────────────────────────────
                    Long fnEdits = doc.getLong("firstNameEditCount");
                    Long lnEdits = doc.getLong("lastNameEditCount");
                    firstNameEditCount = fnEdits != null ? fnEdits.intValue() : 0;
                    lastNameEditCount  = lnEdits != null ? lnEdits.intValue() : 0;
                    updateEditCountLabels();

                    if (firstNameEditCount >= MAX_NAME_EDITS) lockField(etFirstName);
                    if (lastNameEditCount  >= MAX_NAME_EDITS) lockField(etLastName);

                    // ── Course ────────────────────────────────────────
                    String savedCourse = doc.getString("course");
                    if (savedCourse != null) {
                        for (int i = 0; i < PROGRAMS.length; i++) {
                            if (PROGRAMS[i].equals(savedCourse)) {
                                spinnerProgram.setSelection(i);
                                break;
                            }
                        }
                    }

                    // ── Course change date ────────────────────────────
                    Timestamp courseChangedTs = doc.getTimestamp("lastCourseChangedAt");
                    if (courseChangedTs != null) {
                        lastCourseChangedDate = courseChangedTs.toDate();
                        long daysSince = TimeUnit.MILLISECONDS.toDays(
                                new Date().getTime() - lastCourseChangedDate.getTime());
                        canChangeCourse = daysSince >= 365;
                        updateCourseHint(daysSince);
                    } else {
                        // ✅ Never changed before — allowed
                        canChangeCourse = true;
                        updateCourseHint(-1);
                    }

                    // ✅ Disable spinner if course change not allowed
                    spinnerProgram.setEnabled(canChangeCourse);
                    spinnerProgram.setAlpha(canChangeCourse ? 1.0f : 0.4f);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load profile",
                                Toast.LENGTH_SHORT).show());
    }

    // ════════════════════════════════════════════════════════
    //  COURSE HINT
    // ════════════════════════════════════════════════════════

    private void updateCourseHint(long daysSince) {
        if (tvCourseHint == null) return;
        if (daysSince < 0) {
            // Never changed
            tvCourseHint.setText("You can change your course once a year.");
            tvCourseHint.setTextColor(0xFF888888);
        } else if (daysSince >= 365) {
            // Allowed
            tvCourseHint.setText("✅ You can change your course now.");
            tvCourseHint.setTextColor(0xFF4CAF50);
        } else {
            // Locked
            long daysLeft = 365 - daysSince;
            tvCourseHint.setText("🔒 Course locked. You can change it again in "
                    + daysLeft + " day" + (daysLeft == 1 ? "" : "s") + ".");
            tvCourseHint.setTextColor(0xFFE53935);
        }
    }

    // ════════════════════════════════════════════════════════
    //  NAME FIELD HELPERS
    // ════════════════════════════════════════════════════════

    private void lockField(EditText et) {
        et.setFocusable(false);
        et.setFocusableInTouchMode(false);
        et.setClickable(false);
        et.setCursorVisible(false);
        et.setTextColor(getResources().getColor(R.color.text_secondary, getTheme()));
    }

    private void updateEditCountLabels() {
        if (tvFirstNameEditsLeft != null) {
            int left = MAX_NAME_EDITS - firstNameEditCount;
            if (left <= 0) {
                tvFirstNameEditsLeft.setText("First name cannot be changed anymore");
                tvFirstNameEditsLeft.setTextColor(0xFFE53935);
            } else {
                tvFirstNameEditsLeft.setText("You can change first name " + left
                        + " more time" + (left == 1 ? "" : "s")
                        + "  •  min 3, max 36 characters");
                tvFirstNameEditsLeft.setTextColor(0xFF888888);
            }
        }
        if (tvLastNameEditsLeft != null) {
            int left = MAX_NAME_EDITS - lastNameEditCount;
            if (left <= 0) {
                tvLastNameEditsLeft.setText("Last name cannot be changed anymore");
                tvLastNameEditsLeft.setTextColor(0xFFE53935);
            } else {
                tvLastNameEditsLeft.setText("You can change last name " + left
                        + " more time" + (left == 1 ? "" : "s")
                        + "  •  min 3, max 36 characters");
                tvLastNameEditsLeft.setTextColor(0xFF888888);
            }
        }
    }

    // ════════════════════════════════════════════════════════
    //  URI TO FILE
    // ════════════════════════════════════════════════════════

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

    // ════════════════════════════════════════════════════════
    //  UNIQUENESS CHECK
    // ════════════════════════════════════════════════════════

    private void checkUniquenessAndUpdate() {
        btnUpdateProfile.setEnabled(false);
        btnUpdateProfile.setText("Checking...");

        String enteredUsername  = etUsername.getText().toString().trim();
        String enteredFirstName = etFirstName.getText().toString().trim();
        String enteredLastName  = etLastName.getText().toString().trim();
        String enteredEmail     = etEmail.getText().toString().trim();
        String enteredCourse    = spinnerProgram.getSelectedItem().toString();

        final int[]     done     = {0};
        final boolean[] hasError = {false};
        final int       TOTAL    = 3;

        Runnable onDone = () -> {
            done[0]++;
            if (done[0] < TOTAL) return;
            runOnUiThread(() -> {
                btnUpdateProfile.setEnabled(true);
                btnUpdateProfile.setText("Update Profile");
                if (!hasError[0]) updateProfile();
            });
        };

        db.collection("users").whereEqualTo("username", enteredUsername)
                .limit(1).get()
                .addOnSuccessListener(snap -> {
                    if (!snap.isEmpty()) {
                        String foundUid = snap.getDocuments().get(0).getId();
                        if (!foundUid.equals(userId)) {
                            hasError[0] = true;
                            runOnUiThread(() -> {
                                etUsername.setError("Username is already taken");
                                etUsername.requestFocus();
                            });
                        }
                    }
                    onDone.run();
                })
                .addOnFailureListener(e -> onDone.run());

        db.collection("users").whereEqualTo("email", enteredEmail)
                .limit(1).get()
                .addOnSuccessListener(snap -> {
                    if (!snap.isEmpty()) {
                        String foundUid = snap.getDocuments().get(0).getId();
                        if (!foundUid.equals(userId)) {
                            hasError[0] = true;
                            runOnUiThread(() -> {
                                etEmail.setError("This email is already registered");
                                etEmail.requestFocus();
                            });
                        }
                    }
                    onDone.run();
                })
                .addOnFailureListener(e -> onDone.run());

        db.collection("users")
                .whereEqualTo("firstName", enteredFirstName)
                .whereEqualTo("lastName",  enteredLastName)
                .whereEqualTo("course",    enteredCourse)
                .limit(1).get()
                .addOnSuccessListener(snap -> {
                    if (!snap.isEmpty()) {
                        String foundUid = snap.getDocuments().get(0).getId();
                        if (!foundUid.equals(userId)) {
                            hasError[0] = true;
                            runOnUiThread(() -> {
                                etFirstName.setError(
                                        "An account with this name and course already exists");
                                etLastName.setError(
                                        "An account with this name and course already exists");
                                etFirstName.requestFocus();
                            });
                        }
                    }
                    onDone.run();
                })
                .addOnFailureListener(e -> onDone.run());
    }

    // ════════════════════════════════════════════════════════
    //  UPDATE PROFILE
    // ════════════════════════════════════════════════════════

    private void updateProfile() {
        String firstName = etFirstName.getText().toString().trim();
        String lastName  = etLastName.getText().toString().trim();
        String username  = etUsername.getText().toString().trim();
        String phone     = etPhoneNumber.getText().toString().trim();
        String email     = etEmail.getText().toString().trim();
        String course    = spinnerProgram.getSelectedItem().toString();

        if (!firstName.equals(originalFirstName)) firstNameEditCount++;
        if (!lastName.equals(originalLastName))   lastNameEditCount++;

        Map<String, Object> updates = new HashMap<>();
        updates.put("firstName",          firstName);
        updates.put("lastName",           lastName);
        updates.put("username",           username);
        updates.put("phone",              phone);
        updates.put("email",              email);
        updates.put("course",             course);
        updates.put("firstNameEditCount", firstNameEditCount);
        updates.put("lastNameEditCount",  lastNameEditCount);

        // ✅ If course was changed, save the timestamp
        String savedCourse = (String) updates.get("course");
        boolean courseChanged = canChangeCourse
                && spinnerProgram.getSelectedItemPosition() > 0;
        // Detect actual course change by comparing with original
        db.collection("users").document(userId).get()
                .addOnSuccessListener(doc -> {
                    String originalCourse = doc.getString("course");
                    boolean actuallyChanged = !course.equals(originalCourse)
                            && !course.equals("Select course");
                    if (actuallyChanged && canChangeCourse) {
                        updates.put("lastCourseChangedAt", Timestamp.now());
                    }
                    proceedWithUpload(updates, firstName, lastName);
                })
                .addOnFailureListener(e -> proceedWithUpload(updates, firstName, lastName));
    }

    private void proceedWithUpload(Map<String, Object> updates,
                                   String firstName, String lastName) {
        if (newProfilePicUri != null) {
            File file = uriToFile(newProfilePicUri);
            if (file == null) {
                Toast.makeText(this,
                        "File conversion failed. Saving other details...",
                        Toast.LENGTH_SHORT).show();
                saveUpdatesToFirestore(updates);
                return;
            }

            Toast.makeText(this, "Uploading image...", Toast.LENGTH_SHORT).show();

            MediaManager.get()
                    .upload(file.getAbsolutePath())
                    .option("public_id",    "profile_pictures/" + userId)
                    .option("overwrite",    true)
                    .option("invalidate",   true)
                    .option("upload_preset", "ucchat_profiles")
                    .callback(new UploadCallback() {
                        @Override public void onStart(String r) {}
                        @Override public void onProgress(String r, long b, long t) {}
                        @Override public void onReschedule(String r, ErrorInfo e) {}

                        @Override
                        public void onSuccess(String requestId, Map resultData) {
                            String newUrl = (String) resultData.get("secure_url");
                            currentPhotoUrl = newUrl;
                            updates.put("photoUrl", newUrl);
                            Glide.with(PersonalDetailsActivity.this)
                                    .load(newUrl + "?t=" + System.currentTimeMillis())
                                    .transform(new CircleCrop())
                                    .into(btnProfilePicture);
                            saveUpdatesToFirestore(updates);
                        }

                        @Override
                        public void onError(String requestId, ErrorInfo error) {
                            Log.e("UPLOAD", "FAILED: " + error.getDescription());
                            Toast.makeText(PersonalDetailsActivity.this,
                                    "Image upload failed. Saving other details...",
                                    Toast.LENGTH_SHORT).show();
                            saveUpdatesToFirestore(updates);
                        }
                    })
                    .dispatch();
        } else {
            saveUpdatesToFirestore(updates);
        }
    }

    // ════════════════════════════════════════════════════════
    //  SAVE TO FIRESTORE
    // ════════════════════════════════════════════════════════

    private void saveUpdatesToFirestore(Map<String, Object> updates) {
        String firstName = updates.get("firstName").toString();
        String lastName  = updates.get("lastName").toString();
        String fullName  = firstName + " " + lastName;

        db.collection("users").document(userId)
                .update(updates)
                .addOnSuccessListener(unused -> {
                    db.collection("chats")
                            .whereArrayContains("participants", userId)
                            .get()
                            .addOnSuccessListener(querySnapshot -> {
                                for (com.google.firebase.firestore.DocumentSnapshot doc
                                        : querySnapshot.getDocuments()) {

                                    doc.getReference().update(
                                            "participantNames." + userId, fullName);

                                    if (updates.containsKey("photoUrl")) {
                                        doc.getReference().update(
                                                "participantPhotos." + userId,
                                                updates.get("photoUrl"));
                                    }

                                    Boolean isGroup       = doc.getBoolean("isGroup");
                                    Boolean manuallyNamed = doc.getBoolean("manuallyNamed");
                                    if (Boolean.TRUE.equals(isGroup)
                                            && !Boolean.TRUE.equals(manuallyNamed)) {
                                        java.util.Map<String, String> pNames =
                                                (java.util.Map<String, String>)
                                                        doc.get("participantNames");
                                        java.util.List<String> pUids =
                                                (java.util.List<String>)
                                                        doc.get("participants");
                                        if (pNames != null && pUids != null) {
                                            java.util.List<String> firstNames =
                                                    new java.util.ArrayList<>();
                                            for (String uid : pUids) {
                                                String n = uid.equals(userId)
                                                        ? fullName : pNames.get(uid);
                                                if (n != null && !n.isEmpty())
                                                    firstNames.add(n.split(" ")[0]);
                                            }
                                            doc.getReference().update("groupName",
                                                    android.text.TextUtils.join(
                                                            ", ", firstNames));
                                        }
                                    }
                                }

                                originalFirstName = firstName;
                                originalLastName  = lastName;
                                updateEditCountLabels();

                                Toast.makeText(this,
                                        "Profile updated successfully",
                                        Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(this, MenuActivity.class));
                                finish();
                            });
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Update failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    // ════════════════════════════════════════════════════════
    //  VALIDATION
    // ════════════════════════════════════════════════════════

    private boolean validateFields() {
        boolean valid = true;

        String firstName = etFirstName.getText().toString().trim();
        String lastName  = etLastName.getText().toString().trim();
        String username  = etUsername.getText().toString().trim();
        String phone     = etPhoneNumber.getText().toString().trim();
        String email     = etEmail.getText().toString().trim();

        // ── First Name ────────────────────────────────────────
        if (TextUtils.isEmpty(firstName)) {
            etFirstName.setError("First name is required");
            valid = false;
        } else if (firstName.length() < MIN_NAME_LENGTH) {
            etFirstName.setError("First name must be at least 3 characters");
            valid = false;
        } else if (firstName.length() > MAX_NAME_LENGTH) {
            etFirstName.setError("First name must be at most 36 characters");
            valid = false;
        } else if (!firstName.matches("^[A-Za-z]+(\\s[A-Za-z]+){0,2}$")) {
            etFirstName.setError("Letters only — no numbers or special characters");
            valid = false;
        } else if (!firstName.equals(originalFirstName)
                && firstNameEditCount >= MAX_NAME_EDITS) {
            etFirstName.setError(
                    "You have reached the maximum name change limit (3 times)");
            valid = false;
        }

        // ── Last Name ─────────────────────────────────────────
        if (TextUtils.isEmpty(lastName)) {
            etLastName.setError("Last name is required");
            valid = false;
        } else if (lastName.length() < MIN_NAME_LENGTH) {
            etLastName.setError("Last name must be at least 3 characters");
            valid = false;
        } else if (lastName.length() > MAX_NAME_LENGTH) {
            etLastName.setError("Last name must be at most 36 characters");
            valid = false;
        } else if (!lastName.matches("^[A-Za-z]+(\\s[A-Za-z]+){0,2}$")) {
            etLastName.setError("Letters only — no numbers or special characters");
            valid = false;
        } else if (!lastName.equals(originalLastName)
                && lastNameEditCount >= MAX_NAME_EDITS) {
            etLastName.setError(
                    "You have reached the maximum name change limit (3 times)");
            valid = false;
        }

        // ── Username ──────────────────────────────────────────
        if (TextUtils.isEmpty(username)) {
            etUsername.setError("Username is required");
            valid = false;
        }

        // ── Phone ─────────────────────────────────────────────
        if (TextUtils.isEmpty(phone)) {
            etPhoneNumber.setError("Phone number is required");
            valid = false;
        } else if (!phone.matches("^09\\d{9}$")) {
            etPhoneNumber.setError("Must be 11 digits starting with 09");
            valid = false;
        }

        // ── Email ─────────────────────────────────────────────
        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Email is required");
            valid = false;
        } else if (!email.matches(
                "^[A-Za-z][A-Za-z0-9._%+-]*@(gmail|yahoo|outlook|hotmail)\\.com$")) {
            etEmail.setError("Invalid email. Example: juan@gmail.com");
            valid = false;
        }

        // ── Course ────────────────────────────────────────────
        if (spinnerProgram.getSelectedItemPosition() == 0) {
            Toast.makeText(this, "Please select a course.", Toast.LENGTH_SHORT).show();
            valid = false;
        }

        return valid;
    }
}