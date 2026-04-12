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

        tvSignIn.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, SignUpPage1Activity.class)));

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