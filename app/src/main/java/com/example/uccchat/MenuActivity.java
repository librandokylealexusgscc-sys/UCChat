package com.example.uccchat;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class MenuActivity extends AppCompatActivity {

    // Bottom nav buttons
    private LinearLayout btnTabChats, btnTabSearch, btnTabMenu;

    // Menu option buttons
    private LinearLayout btnPersonalDetails, btnPrivacyPolicy, btnTerms, btnDltAcc, btnLogout;

    // Student details layout views
    private LinearLayout studentDeets;
    private TextView studentName, course_studentNum;

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.menu);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initViews();
        loadStudentDetails();
        setListeners();
    }

    private void initViews() {

        // Bottom nav
        btnTabChats  = findViewById(R.id.btnTabChats);
        btnTabSearch = findViewById(R.id.btnTabSearch);
        btnTabMenu   = findViewById(R.id.btnTabMenu);

        // Menu options
        btnPersonalDetails = findViewById(R.id.btnPersonalDetails);
        btnPrivacyPolicy   = findViewById(R.id.btnPrivacyPolicy);
        btnTerms           = findViewById(R.id.btnTerms);
        btnDltAcc          = findViewById(R.id.btnDltAcc);
        btnLogout          = findViewById(R.id.btnLogout);

        // Student details
        studentDeets      = findViewById(R.id.studentDeets);
        studentName       = findViewById(R.id.studentName);
        course_studentNum = findViewById(R.id.course_studentNum);
    }

    private void loadStudentDetails() {

        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            navigateTo(WelcomePageActivity.class, true);
            return;
        }

        String uid = currentUser.getUid();

        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {

                    if (documentSnapshot.exists()) {

                        String firstName = documentSnapshot.getString("firstName");
                        String lastName  = documentSnapshot.getString("lastName");
                        String course    = documentSnapshot.getString("course");
                        String studentId = documentSnapshot.getString("studentId");

                        String fullName = "";

                        if (firstName != null) fullName += firstName;
                        if (lastName != null) fullName += " " + lastName;

                        studentName.setText(
                                fullName.trim().isEmpty() ? "—" : fullName.trim()
                        );

                        course_studentNum.setText(
                                (course != null ? course : "—")
                                        + " | "
                                        + (studentId != null ? studentId : "—")
                        );

                    } else {

                        studentName.setText("Unknown Student");
                        course_studentNum.setText("—");

                    }
                })

                .addOnFailureListener(e ->
                        Toast.makeText(
                                this,
                                "Failed to load student details: " + e.getMessage(),
                                Toast.LENGTH_SHORT
                        ).show()
                );
    }

    private void setListeners() {

        // Bottom navigation

        btnTabChats.setOnClickListener(v ->
                navigateTo(ChatActivity.class, false));

        btnTabSearch.setOnClickListener(v ->
                navigateTo(SearchActivity.class, false));

        btnTabMenu.setOnClickListener(v -> {
            // already in MenuActivity
        });


        // Menu options

        btnPersonalDetails.setOnClickListener(v ->
                navigateTo(PersonalDetailsActivity.class, false));

        btnPrivacyPolicy.setOnClickListener(v ->
                navigateTo(PrivacyPolicyActivity.class, false));

        btnTerms.setOnClickListener(v ->
                navigateTo(TermsOfUseActivity.class, false));

        btnDltAcc.setOnClickListener(v ->
                navigateTo(DeleteAccountActivity.class, false));


        // Logout

        btnLogout.setOnClickListener(v -> {

            mAuth.signOut();

            navigateTo(WelcomePageActivity.class, true);

        });
    }


    /**
     * Navigation helper
     */
    private void navigateTo(Class<?> destination, boolean clearStack) {

        // Prevent reopening same activity
        if (this.getClass().equals(destination)) return;

        Intent intent = new Intent(this, destination);

        if (clearStack) {

            intent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK
            );

        }

        startActivity(intent);

        if (!clearStack)
            overridePendingTransition(0, 0);
    }
}