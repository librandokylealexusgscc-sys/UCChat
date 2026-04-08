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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.GraphRequest;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;

public class WelcomePageActivity extends AppCompatActivity {

    private View dimBackground;
    private ConstraintLayout cardModal;
    private GoogleSignInClient googleSignInClient;
    private CallbackManager callbackManager;

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

        // Setup Facebook CallbackManager
        callbackManager = CallbackManager.Factory.create();

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

        // Facebook button
        MaterialButton btnFacebook = findViewById(R.id.btnFacebook);
        btnFacebook.setOnClickListener(v -> {
            LoginManager.getInstance().logOut();
            LoginManager.getInstance().logInWithReadPermissions(
                    this,
                    callbackManager,
                    Arrays.asList("public_profile", "email")
            );
        });

        LoginManager.getInstance().registerCallback(callbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                UserSession.facebookToken = loginResult.getAccessToken().getToken(); // add this
                fetchFacebookProfile(loginResult.getAccessToken());
            }

            @Override
            public void onCancel() {
                Toast.makeText(WelcomePageActivity.this, "Facebook login cancelled.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(@NonNull FacebookException error) {
                Toast.makeText(WelcomePageActivity.this, "Facebook login failed: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Required for Facebook SDK to handle its result
        callbackManager.onActivityResult(requestCode, resultCode, data);
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void fetchFacebookProfile(AccessToken accessToken) {
        GraphRequest request = GraphRequest.newMeRequest(accessToken, (object, response) -> {
            try {
                String firstName = object.optString("first_name", "");
                String lastName  = object.optString("last_name", "");
                String email     = object.optString("email", "");

                // Check if this FB email is already registered in Firestore
                FirebaseFirestore db = FirebaseFirestore.getInstance();
                db.collection("users")
                        .whereEqualTo("email", email)
                        .get()
                        .addOnSuccessListener(querySnapshot -> {
                            if (!querySnapshot.isEmpty() && !email.isEmpty()) {
                                // Email already exists — store flag and go to SignUpPage1
                                // SignUpPage1 will display the error message to the user
                                UserSession.firstName       = firstName;
                                UserSession.lastName        = lastName;
                                UserSession.email           = email;
                                UserSession.isFromFacebook  = true;
                                UserSession.facebookEmailAlreadyExists = true;
                                startActivity(new Intent(WelcomePageActivity.this, SignUpPage1Activity.class));
                            } else {
                                // Email is free — pre-fill and proceed normally
                                UserSession.firstName       = firstName;
                                UserSession.lastName        = lastName;
                                UserSession.email           = email;
                                UserSession.isFromFacebook  = true;
                                UserSession.facebookEmailAlreadyExists = false;
                                startActivity(new Intent(WelcomePageActivity.this, SignUpPage1Activity.class));
                            }
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(this, "Could not verify email. Try again.", Toast.LENGTH_SHORT).show();
                        });

            } catch (Exception e) {
                Toast.makeText(this, "Failed to get Facebook profile.", Toast.LENGTH_SHORT).show();
            }
        });

        Bundle parameters = new Bundle();
        parameters.putString("fields", "first_name,last_name,email");
        request.setParameters(parameters);
        request.executeAsync();
    }

    private void handleGoogleAccount(GoogleSignInAccount account) {
        String fullName = account.getDisplayName();
        if (fullName != null && fullName.contains(" ")) {
            String[] parts = fullName.split(" ", 2);
            UserSession.firstName = parts[0];
            UserSession.lastName  = parts[1];
        } else {
            UserSession.firstName = fullName;
            UserSession.lastName  = "";
        }

        UserSession.email         = account.getEmail();
        UserSession.googlePhotoUrl = account.getPhotoUrl() != null
                ? account.getPhotoUrl().toString() : null;
        UserSession.isFromGoogle   = true;

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