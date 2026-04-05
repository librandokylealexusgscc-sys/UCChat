package com.example.uccchat;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.UnderlineSpan;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;

public class WelcomePageActivity extends AppCompatActivity {

    private View dimBackground;
    private ConstraintLayout cardModal;
    private GoogleSignInClient googleSignInClient;

    private final ActivityResultLauncher<Intent> googleSignInLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        try {
                            GoogleSignInAccount account = task.getResult(ApiException.class);
                            handleGoogleAccount(account);
                        } catch (ApiException e) {
                            Toast.makeText(this, "Google sign-in failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.welcome_page);

        // Setup Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        dimBackground = findViewById(R.id.dimBackground);
        cardModal = findViewById(R.id.cardModal);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (cardModal.getVisibility() == View.VISIBLE) {
                    hideTermsModal();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        MaterialButton btnAgree = findViewById(R.id.btnAgree);
        btnAgree.setOnClickListener(v -> hideTermsModal());
        dimBackground.setOnClickListener(v -> hideTermsModal());

        // Google button
        MaterialButton btnGoogle = findViewById(R.id.btnGoogle);
        btnGoogle.setOnClickListener(v -> {
            googleSignInClient.signOut().addOnCompleteListener(task -> {
                Intent signInIntent = googleSignInClient.getSignInIntent();
                googleSignInLauncher.launch(signInIntent);
            });
        });

        // Facebook button — coming soon
        MaterialButton btnFacebook = findViewById(R.id.btnFacebook);
        btnFacebook.setOnClickListener(v ->
                Toast.makeText(this, "Facebook sign-up coming soon!", Toast.LENGTH_SHORT).show());

        MaterialButton btnHaveAccount = findViewById(R.id.btnHaveAccount);
        btnHaveAccount.setOnClickListener(v ->
                startActivity(new Intent(WelcomePageActivity.this, LoginActivity.class)));

        TextView tvSignIn = findViewById(R.id.tvSignIn);
        String signInText = "Don't have an account? Sign In";
        SpannableString signInSpannable = new SpannableString(signInText);
        int signInStart = signInText.indexOf("Sign In");
        signInSpannable.setSpan(new UnderlineSpan(), signInStart, signInText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        signInSpannable.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                startActivity(new Intent(WelcomePageActivity.this, SignUpPage1Activity.class));
            }
            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                ds.setColor(Color.BLACK);
                ds.setUnderlineText(true);
            }
        }, signInStart, signInText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tvSignIn.setText(signInSpannable);
        tvSignIn.setTextColor(Color.BLACK);
        tvSignIn.setMovementMethod(LinkMovementMethod.getInstance());
        tvSignIn.setHighlightColor(Color.TRANSPARENT);

        TextView tvTerms = findViewById(R.id.tvTerms);
        String termsText = "By continuing you confirm that you agree to our Terms of Service, Privacy Policy and good behavior in chat with users. (make this a safe space :))";
        SpannableString termsSpannable = new SpannableString(termsText);
        int tosStart = termsText.indexOf("Terms of Service");
        int tosEnd = tosStart + "Terms of Service".length();
        termsSpannable.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                showTermsModal();
            }
            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                ds.setColor(Color.BLACK);
                ds.setUnderlineText(true);
            }
        }, tosStart, tosEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tvTerms.setText(termsSpannable);
        tvTerms.setTextColor(Color.BLACK);
        tvTerms.setMovementMethod(LinkMovementMethod.getInstance());
        tvTerms.setHighlightColor(Color.TRANSPARENT);
    }

    private void handleGoogleAccount(GoogleSignInAccount account) {
        // Pre-fill UserSession with Google data
        String fullName = account.getDisplayName();
        if (fullName != null && fullName.contains(" ")) {
            String[] parts = fullName.split(" ", 2);
            UserSession.firstName = parts[0];
            UserSession.lastName  = parts[1];
        } else {
            UserSession.firstName = fullName;
            UserSession.lastName  = "";
        }

        UserSession.email      = account.getEmail();
        UserSession.googlePhotoUrl = account.getPhotoUrl() != null
                ? account.getPhotoUrl().toString() : null;
        UserSession.isFromGoogle = true;

        // Go straight to SignUpPage1 with fields pre-filled
        startActivity(new Intent(WelcomePageActivity.this, SignUpPage1Activity.class));
    }

    private void showTermsModal() {
        dimBackground.setVisibility(View.VISIBLE);
        cardModal.setVisibility(View.VISIBLE);
    }

    private void hideTermsModal() {
        dimBackground.setVisibility(View.GONE);
        cardModal.setVisibility(View.GONE);
    }
}