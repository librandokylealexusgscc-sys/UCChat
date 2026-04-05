package com.example.uccchat;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.UnderlineSpan;
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
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_page);

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        tilEmail    = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        etEmail     = findViewById(R.id.etEmail);
        etPassword  = findViewById(R.id.etPassword);
        btnLogin    = findViewById(R.id.btnLogin);
        TextView tvSignIn = findViewById(R.id.tvSignIn);

        String fullText = "Don't have an account? Sign In";
        SpannableString spannable = new SpannableString(fullText);
        int start = fullText.indexOf("Sign In");
        spannable.setSpan(new UnderlineSpan(), start, fullText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tvSignIn.setText(spannable);
        tvSignIn.setTextColor(getColor(android.R.color.black));

        tvSignIn.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, SignUpPage1Activity.class)));

        btnLogin.setOnClickListener(v -> {
            String email    = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
            String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

            tilEmail.setError(null);    tilEmail.setErrorEnabled(false);
            tilPassword.setError(null); tilPassword.setErrorEnabled(false);

            boolean hasError = false;

            if (email.isEmpty()) {
                tilEmail.setErrorEnabled(true);
                tilEmail.setError("Please enter your email");
                hasError = true;
            }
            if (password.isEmpty()) {
                tilPassword.setErrorEnabled(true);
                tilPassword.setError("Please enter your password");
                hasError = true;
            }

            if (!hasError) {
                btnLogin.setEnabled(false);

                mAuth.signInWithEmailAndPassword(email, password)
                        .addOnSuccessListener(authResult -> {
                            String uid = authResult.getUser().getUid();
                            db.collection("users").document(uid).get()
                                    .addOnSuccessListener(doc -> {
                                        if (doc.exists()) {
                                            UserSession.firstName = doc.getString("firstName");
                                            UserSession.lastName  = doc.getString("lastName");
                                            UserSession.username  = doc.getString("username");
                                            UserSession.email     = doc.getString("email");
                                            UserSession.phone     = doc.getString("phone");
                                            UserSession.studentId = doc.getString("studentId");
                                            UserSession.photoUrl  = doc.getString("photoUrl");
                                            UserSession.firebaseUid = uid;
                                        }
                                        Toast.makeText(LoginActivity.this,
                                                "Welcome back, " + UserSession.firstName + "! 👋",
                                                Toast.LENGTH_SHORT).show();
                                        startActivity(new Intent(LoginActivity.this, WelcomePageActivity.class));
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
                            tilEmail.setError("Invalid email or password");
                            tilPassword.setErrorEnabled(true);
                            tilPassword.setError("Invalid email or password");
                        });
            }
        });
    }
}