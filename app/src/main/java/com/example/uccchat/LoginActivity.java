package com.example.uccchat;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.UnderlineSpan;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;
    private MaterialButton btnLogin;
    private ImageButton btnTogglePassword;      // 👈 added
    private boolean isPasswordVisible = false;  // 👈 added
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_page);

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        tilEmail          = findViewById(R.id.tilEmail);
        tilPassword       = findViewById(R.id.tilPassword);
        etEmail           = findViewById(R.id.etEmail);
        etPassword        = findViewById(R.id.etPassword);
        btnLogin          = findViewById(R.id.btnLogin);
        btnTogglePassword = findViewById(R.id.btnTogglePassword);  // 👈 added
        TextView tvSignIn = findViewById(R.id.tvSignIn);

        // ── Password show / hide toggle ──────────────────────────────────── 👈 added
        btnTogglePassword.setOnClickListener(v -> {
            isPasswordVisible = !isPasswordVisible;

            if (isPasswordVisible) {
                // Show password
                etPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                btnTogglePassword.setImageResource(R.drawable.visible); // your "eye open" drawable
            } else {
                // Hide password
                etPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
                btnTogglePassword.setImageResource(R.drawable.hide); // your "eye closed" drawable
            }

            // Keep cursor at the end after toggling
            etPassword.setSelection(etPassword.getText() != null
                    ? etPassword.getText().length() : 0);
        });
        // ────────────────────────────────────────────────────────────────────

        String fullText = "Don't have an account? Sign In";
        SpannableString spannable = new SpannableString(fullText);
        int start = fullText.indexOf("Sign In");
        spannable.setSpan(new UnderlineSpan(), start, fullText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tvSignIn.setText(spannable);
        tvSignIn.setTextColor(getColor(android.R.color.black));

        tvSignIn.setOnClickListener(v -> showPrivacyPolicyThenProceed());

        btnLogin.setOnClickListener(v -> {
            String input    = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
            String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

            tilEmail.setError(null);    tilEmail.setErrorEnabled(false);
            tilPassword.setError(null); tilPassword.setErrorEnabled(false);

            boolean hasError = false;

            if (input.isEmpty()) {
                tilEmail.setErrorEnabled(true);
                tilEmail.setError("Please enter your email or username");
                hasError = true;
            }
            if (password.isEmpty()) {
                tilPassword.setErrorEnabled(true);
                tilPassword.setError("Please enter your password");
                hasError = true;
            }

            if (!hasError) {
                btnLogin.setEnabled(false);

                if (input.contains("@")) {
                    // Looks like an email — sign in directly
                    signInWithEmail(input, password);
                } else {
                    // Treat as username — look up the email first
                    db.collection("users")
                            .whereEqualTo("username", input)
                            .limit(1)
                            .get()
                            .addOnSuccessListener(query -> {
                                if (!query.isEmpty()) {
                                    String email = query.getDocuments().get(0).getString("email");
                                    signInWithEmail(email, password);
                                } else {
                                    btnLogin.setEnabled(true);
                                    tilEmail.setErrorEnabled(true);
                                    tilEmail.setError("Username not found");
                                }
                            })
                            .addOnFailureListener(e -> {
                                btnLogin.setEnabled(true);
                                Toast.makeText(LoginActivity.this,
                                        "Error looking up username.", Toast.LENGTH_SHORT).show();
                            });
                }
            }
        });
    }
    private void showPrivacyPolicyThenProceed() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        bg.setCornerRadius(42f);
        root.setBackground(bg);
        root.setClipToOutline(true);
        root.setPadding(48, 48, 48, 0);

        android.widget.TextView tvTitle = new android.widget.TextView(this);
        tvTitle.setText("Privacy Policy");
        tvTitle.setTextSize(18f);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(0xFF000000);
        tvTitle.setGravity(android.view.Gravity.CENTER);
        tvTitle.setPadding(0, 0, 0, 16);
        root.addView(tvTitle);

        android.widget.TextView tvSub = new android.widget.TextView(this);
        tvSub.setText("Please scroll to the bottom before continuing.");
        tvSub.setTextSize(13f);
        tvSub.setTextColor(0xFF888888);
        tvSub.setGravity(android.view.Gravity.CENTER);
        tvSub.setPadding(0, 0, 0, 16);
        root.addView(tvSub);

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        int maxH = (int)(getResources().getDisplayMetrics().density * 280);
        scrollView.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, maxH));

        android.widget.TextView tvContent = new android.widget.TextView(this);
        tvContent.setPadding(0, 0, 0, 16);
        tvContent.setTextColor(0xFF333333);
        tvContent.setTextSize(13f);
        tvContent.setText(
                "UCChat Privacy Policy\n\nLast updated: April 16, 2026\n\n" +
                        "Information We Collect\n" +
                        "UCChat collects your name, email, student ID, and profile photo.\n\n" +
                        "How We Use Your Information\n" +
                        "Your information is used solely to provide the UCChat messaging service " +
                        "to University of Cebu students and staff.\n\n" +
                        "Data Storage\n" +
                        "Your data is stored securely using Google Firebase services.\n\n" +
                        "Third Party Services\n" +
                        "We use Firebase (Google) and Cloudinary for data storage and media hosting.\n\n" +
                        "By tapping Agree, you confirm that you have read and agree to our " +
                        "Privacy Policy and Terms of Use."
        );
        scrollView.addView(tvContent);
        root.addView(scrollView);

        android.view.View divider = new android.view.View(this);
        divider.setBackgroundColor(0xFFE0E0E0);
        divider.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 2));
        root.addView(divider);

        android.widget.LinearLayout btnRow = new android.widget.LinearLayout(this);
        btnRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        btnRow.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 130));

        android.widget.TextView btnCancel = new android.widget.TextView(this);
        btnCancel.setText("Cancel");
        btnCancel.setGravity(android.view.Gravity.CENTER);
        btnCancel.setTextSize(16f);
        btnCancel.setTextColor(0xFF888888);
        btnCancel.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        android.view.View vDiv = new android.view.View(this);
        vDiv.setBackgroundColor(0xFFE0E0E0);
        vDiv.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                2, android.widget.LinearLayout.LayoutParams.MATCH_PARENT));

        android.widget.TextView btnAgree = new android.widget.TextView(this);
        btnAgree.setText("Agree");
        btnAgree.setGravity(android.view.Gravity.CENTER);
        btnAgree.setTextSize(16f);
        btnAgree.setTextColor(0xFFFFFFFF);
        btnAgree.setTypeface(null, android.graphics.Typeface.BOLD);
        btnAgree.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        android.graphics.drawable.GradientDrawable agreeBg =
                new android.graphics.drawable.GradientDrawable();
        agreeBg.setColor(0xFF4CAF50);
        btnAgree.setBackground(agreeBg);
        btnAgree.setEnabled(false);
        btnAgree.setAlpha(0.4f);

        scrollView.getViewTreeObserver().addOnScrollChangedListener(() -> {
            android.view.View child = scrollView.getChildAt(0);
            if (child != null) {
                int diff = child.getBottom() -
                        (scrollView.getHeight() + scrollView.getScrollY());
                if (diff <= 10) {
                    btnAgree.setEnabled(true);
                    btnAgree.setAlpha(1f);
                }
            }
        });

        btnAgree.setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(new Intent(LoginActivity.this, SignUpPage1Activity.class));
        });

        btnRow.addView(btnCancel);
        btnRow.addView(vDiv);
        btnRow.addView(btnAgree);
        root.addView(btnRow);

        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(
                            android.graphics.Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    (int)(getResources().getDisplayMetrics().widthPixels * 0.92f),
                    android.view.WindowManager.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
    }
    private void signInWithEmail(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    db.collection("users").document(uid).get()
                            .addOnSuccessListener(doc -> {
                                if (doc.exists()) {
                                    UserSession.firstName  = doc.getString("firstName");
                                    UserSession.lastName   = doc.getString("lastName");
                                    UserSession.username   = doc.getString("username");
                                    UserSession.email      = doc.getString("email");
                                    UserSession.phone      = doc.getString("phone");
                                    UserSession.studentId  = doc.getString("studentId");
                                    UserSession.photoUrl   = doc.getString("photoUrl");
                                    UserSession.firebaseUid = uid;
                                }
                                Toast.makeText(LoginActivity.this,
                                        "Welcome back, " + UserSession.firstName + "! 👋",
                                        Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(LoginActivity.this, ChatHomeActivity.class));
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                btnLogin.setEnabled(true);
                                Toast.makeText(LoginActivity.this,
                                        "Failed to load user data.", Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    btnLogin.setEnabled(true);
                    tilEmail.setErrorEnabled(true);
                    tilEmail.setError("Invalid email/username or password");
                    tilPassword.setErrorEnabled(true);
                    tilPassword.setError("Invalid email/username or password");
                });
    }
}