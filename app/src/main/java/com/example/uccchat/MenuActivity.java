package com.example.uccchat;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class MenuActivity extends AppCompatActivity {
    private LinearLayout centerConfirmPopup;
    private TextView popupOK, popupCancel;

    private LinearLayout btnTabChats, btnTabSearch, btnTabMenu;
    private LinearLayout btnPersonalDetails, btnPrivacyPolicy, btnTerms, btnDltAcc, btnLogout;
    private LinearLayout studentDeets;
    private TextView studentName, course_studentNum;

    private FirebaseAuth mAuth;
    private ImageView ImgProfile;
    private FirebaseFirestore db;
    private boolean isNavigating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.menu);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initViews();
        loadStudentDetails();
        setListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        isNavigating = false;
        loadStudentDetails();
    }

    private void initViews() {
        btnTabChats  = findViewById(R.id.btnTabChats);
        btnTabSearch = findViewById(R.id.btnTabSearch);
        btnTabMenu   = findViewById(R.id.btnTabMenu);
        ImgProfile  = findViewById(R.id.ImgProfile);

        btnPersonalDetails = findViewById(R.id.btnPersonalDetails);
        btnPrivacyPolicy   = findViewById(R.id.btnPrivacyPolicy);
        btnTerms           = findViewById(R.id.btnTerms);
        btnDltAcc          = findViewById(R.id.btnDltAcc);
        btnLogout          = findViewById(R.id.btnLogout);

        studentDeets      = findViewById(R.id.studentDeets);
        studentName       = findViewById(R.id.studentName);
        course_studentNum = findViewById(R.id.course_studentNum);
        centerConfirmPopup = findViewById(R.id.centerConfirmPopup);
        popupOK = findViewById(R.id.popupOK);
        popupCancel = findViewById(R.id.popupCancel);
        centerConfirmPopup.setVisibility(View.GONE);

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
                        String photoUrl = documentSnapshot.getString("photoUrl");
                        if (photoUrl != null && !photoUrl.isEmpty()) {
                            Glide.with(this)
                                    .load(photoUrl + "?t=" + System.currentTimeMillis())
                                    .circleCrop()
                                    .into(ImgProfile);
                        }
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
        btnTabChats.setOnClickListener(v ->
                navigateTo(ChatHomeActivity.class, false));

        btnTabSearch.setOnClickListener(v ->
                navigateTo(SearchActivity.class, false));

        btnTabMenu.setOnClickListener(v -> {
            // already here
        });

        btnPersonalDetails.setOnClickListener(v ->
                navigateTo(PersonalDetailsActivity.class, false));

        btnPrivacyPolicy.setOnClickListener(v ->
                navigateTo(PrivacyPolicyActivity.class, false));

        btnTerms.setOnClickListener(v ->
                navigateTo(TermsOfUseActivity.class, false));

        btnDltAcc.setOnClickListener(v ->
                navigateTo(DeleteAccountActivity.class, false));

        btnLogout.setOnClickListener(v -> {
            centerConfirmPopup.setVisibility(View.VISIBLE);
        });

        popupOK.setOnClickListener(v -> {
            mAuth.signOut();
            navigateTo(WelcomePageActivity.class, true);
        });

        popupCancel.setOnClickListener(v -> {
            centerConfirmPopup.setVisibility(View.GONE);
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
}