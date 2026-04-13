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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Arrays;

public class WelcomePageActivity extends AppCompatActivity {

    private View dimBackground;
    private ConstraintLayout cardModal;
    private GoogleSignInClient googleSignInClient;
    private CallbackManager callbackManager;

    private final ActivityResultLauncher<Intent> googleSignInLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        Task<GoogleSignInAccount> task =
                                GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        try {
                            GoogleSignInAccount account = task.getResult(ApiException.class);
                            handleGoogleAccount(account);
                        } catch (ApiException e) {
                            Toast.makeText(WelcomePageActivity.this,
                                    "Google sign-in failed: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.welcome_page);

        // ✅ Add requestIdToken — replace YOUR_WEB_CLIENT_ID with the one from google-services.json
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(
                GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id)) // ✅ add this
                .requestEmail()
                .requestProfile()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);
        callbackManager = CallbackManager.Factory.create();

        dimBackground = findViewById(R.id.dimBackground);
        cardModal     = findViewById(R.id.cardModal);

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

        MaterialButton btnGoogle = findViewById(R.id.btnGoogle);
        btnGoogle.setOnClickListener(v ->
                googleSignInClient.signOut().addOnCompleteListener(task -> {
                    Intent signInIntent = googleSignInClient.getSignInIntent();
                    googleSignInLauncher.launch(signInIntent);
                }));

        MaterialButton btnFacebook = findViewById(R.id.btnFacebook);
        btnFacebook.setOnClickListener(v -> {
            LoginManager.getInstance().logOut();
            LoginManager.getInstance().logInWithReadPermissions(
                    this, callbackManager,
                    Arrays.asList("public_profile", "email"));
        });

        LoginManager.getInstance().registerCallback(callbackManager,
                new FacebookCallback<LoginResult>() {
                    @Override
                    public void onSuccess(LoginResult loginResult) {
                        UserSession.facebookToken = loginResult.getAccessToken().getToken();
                        fetchFacebookProfile(loginResult.getAccessToken());
                    }
                    @Override
                    public void onCancel() {
                        Toast.makeText(WelcomePageActivity.this,
                                "Facebook login cancelled.", Toast.LENGTH_SHORT).show();
                    }
                    @Override
                    public void onError(@NonNull FacebookException error) {
                        Toast.makeText(WelcomePageActivity.this,
                                "Facebook login failed: " + error.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });

        MaterialButton btnHaveAccount = findViewById(R.id.btnHaveAccount);
        btnHaveAccount.setOnClickListener(v ->
                startActivity(new Intent(WelcomePageActivity.this, LoginActivity.class)));

        TextView tvSignIn = findViewById(R.id.tvSignIn);
        String signInText = "Don't have an account? Sign In";
        SpannableString signInSpannable = new SpannableString(signInText);
        int signInStart = signInText.indexOf("Sign In");
        signInSpannable.setSpan(new UnderlineSpan(),
                signInStart, signInText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        signInSpannable.setSpan(new ClickableSpan() {
            @Override public void onClick(@NonNull View widget) {
                startActivity(new Intent(WelcomePageActivity.this, SignUpPage1Activity.class));
            }
            @Override public void updateDrawState(@NonNull TextPaint ds) {
                ds.setColor(Color.BLACK);
                ds.setUnderlineText(true);
            }
        }, signInStart, signInText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tvSignIn.setText(signInSpannable);
        tvSignIn.setTextColor(Color.BLACK);
        tvSignIn.setMovementMethod(LinkMovementMethod.getInstance());
        tvSignIn.setHighlightColor(Color.TRANSPARENT);

        TextView tvTerms = findViewById(R.id.tvTerms);
        String termsText = "By continuing you confirm that you agree to our Terms of Service, " +
                "Privacy Policy and good behavior in chat with users. (make this a safe space :))";
        SpannableString termsSpannable = new SpannableString(termsText);
        int tosStart = termsText.indexOf("Terms of Service");
        int tosEnd   = tosStart + "Terms of Service".length();
        termsSpannable.setSpan(new ClickableSpan() {
            @Override public void onClick(@NonNull View widget) { showTermsModal(); }
            @Override public void updateDrawState(@NonNull TextPaint ds) {
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
        callbackManager.onActivityResult(requestCode, resultCode, data);
        super.onActivityResult(requestCode, resultCode, data);
    }

    // ════════════════════════════════════════════════════════
    //  GOOGLE
    // ════════════════════════════════════════════════════════

    private void handleGoogleAccount(GoogleSignInAccount account) {
        String email    = account.getEmail();
        String fullName = account.getDisplayName();

        if (fullName != null && fullName.contains(" ")) {
            String[] parts = fullName.split(" ", 2);
            UserSession.firstName = parts[0];
            UserSession.lastName  = parts[1];
        } else {
            UserSession.firstName = fullName;
            UserSession.lastName  = "";
        }
        UserSession.email          = email;
        UserSession.googlePhotoUrl = account.getPhotoUrl() != null
                ? account.getPhotoUrl().toString() : null;
        UserSession.isFromGoogle   = true;

        if (email == null || email.isEmpty()) {
            startActivity(new Intent(WelcomePageActivity.this, SignUpPage1Activity.class));
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        // ── Existing user ──
                        com.google.firebase.firestore.DocumentSnapshot doc =
                                querySnapshot.getDocuments().get(0);

                        UserSession.firstName   = doc.getString("firstName");
                        UserSession.lastName    = doc.getString("lastName");
                        UserSession.username    = doc.getString("username");
                        UserSession.email       = doc.getString("email");
                        UserSession.phone       = doc.getString("phone");
                        UserSession.studentId   = doc.getString("studentId");
                        UserSession.photoUrl    = doc.getString("photoUrl");
                        UserSession.firebaseUid = doc.getId();

                        // ✅ Use the ID token from the account — no null crash
                        String idToken = account.getIdToken();

                        if (idToken != null) {
                            com.google.firebase.auth.AuthCredential credential =
                                    com.google.firebase.auth.GoogleAuthProvider
                                            .getCredential(idToken, null);

                            FirebaseAuth.getInstance().signInWithCredential(credential)
                                    .addOnCompleteListener(task -> {
                                        // ✅ Navigate regardless of success/failure
                                        runOnUiThread(() -> goToChat());
                                    });
                        } else {
                            // No ID token — navigate anyway, session is loaded
                            runOnUiThread(() -> goToChat());
                        }

                    } else {
                        // ── New user ──
                        runOnUiThread(() ->
                                startActivity(new Intent(WelcomePageActivity.this,
                                        SignUpPage1Activity.class)));
                    }
                })
                .addOnFailureListener(e ->
                        runOnUiThread(() ->
                                Toast.makeText(WelcomePageActivity.this,
                                        "Could not verify account. Try again.",
                                        Toast.LENGTH_SHORT).show()));
    }

    // ✅ Helper to avoid repeating navigation code
    private void goToChat() {
        Intent intent = new Intent(WelcomePageActivity.this, ChatHomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    // ════════════════════════════════════════════════════════
    //  FACEBOOK
    // ════════════════════════════════════════════════════════

    private void fetchFacebookProfile(AccessToken accessToken) {
        WelcomePageActivity activity = this;

        GraphRequest request = GraphRequest.newMeRequest(accessToken, (object, response) -> {
            try {
                String firstName = object.optString("first_name", "");
                String lastName  = object.optString("last_name",  "");
                String email     = object.optString("email",      "");

                android.util.Log.d("FB_EMAIL", "Facebook returned email: '" + email + "'");

                // ── No email returned by Facebook ──
                if (email.isEmpty()) {
                    activity.runOnUiThread(() -> {
                        UserSession.firstName      = firstName;
                        UserSession.lastName       = lastName;
                        UserSession.isFromFacebook = true;
                        UserSession.facebookEmailAlreadyExists = false;
                        activity.startActivity(
                                new Intent(activity, SignUpPage1Activity.class));
                    });
                    return;
                }

                // ── Check Firestore on main thread ──
                activity.runOnUiThread(() ->
                        FirebaseFirestore.getInstance()
                                .collection("users")
                                .whereEqualTo("email", email)
                                .get()
                                .addOnSuccessListener(querySnapshot -> {
                                    android.util.Log.d("FB_EMAIL",
                                            "Firestore results: " + querySnapshot.size());

                                    if (!querySnapshot.isEmpty()) {
                                        // ── Existing user → load session ──
                                        com.google.firebase.firestore.DocumentSnapshot doc =
                                                querySnapshot.getDocuments().get(0);

                                        UserSession.firstName   = doc.getString("firstName");
                                        UserSession.lastName    = doc.getString("lastName");
                                        UserSession.username    = doc.getString("username");
                                        UserSession.email       = doc.getString("email");
                                        UserSession.phone       = doc.getString("phone");
                                        UserSession.studentId   = doc.getString("studentId");
                                        UserSession.photoUrl    = doc.getString("photoUrl");
                                        UserSession.firebaseUid = doc.getId();
                                        UserSession.isFromFacebook = true;

                                        android.util.Log.d("FB_EMAIL",
                                                "Found user: " + UserSession.firstName
                                                        + " uid: " + UserSession.firebaseUid);

                                        // ── Sign in using Firebase Auth with email + password ──
                                        // Facebook users registered with createUserWithEmailAndPassword
                                        // in SignUpPage3, so we sign in the same way.
                                        // We use the stored password from UserSession if available,
                                        // otherwise skip Firebase Auth and go straight to chat.
                                        if (UserSession.password != null
                                                && !UserSession.password.isEmpty()) {
                                            FirebaseAuth.getInstance()
                                                    .signInWithEmailAndPassword(
                                                            email, UserSession.password)
                                                    .addOnCompleteListener(task ->
                                                            goToChat());
                                        } else {
                                            // ── No password in session — try Facebook credential ──
                                            com.google.firebase.auth.AuthCredential credential =
                                                    com.google.firebase.auth.FacebookAuthProvider
                                                            .getCredential(
                                                                    accessToken.getToken());
                                            FirebaseAuth.getInstance()
                                                    .signInWithCredential(credential)
                                                    .addOnCompleteListener(task -> {
                                                        android.util.Log.d("FB_EMAIL",
                                                                "Firebase Auth result: "
                                                                        + task.isSuccessful());
                                                        // ✅ Go to chat regardless
                                                        goToChat();
                                                    });
                                        }

                                    } else {
                                        // ── New user → sign-up flow ──
                                        android.util.Log.d("FB_EMAIL",
                                                "No existing user found, going to sign up");
                                        UserSession.firstName      = firstName;
                                        UserSession.lastName       = lastName;
                                        UserSession.email          = email;
                                        UserSession.isFromFacebook = true;
                                        UserSession.facebookEmailAlreadyExists = false;
                                        activity.startActivity(
                                                new Intent(activity, SignUpPage1Activity.class));
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    android.util.Log.e("FB_EMAIL",
                                            "Firestore query failed: " + e.getMessage());
                                    Toast.makeText(activity,
                                            "Could not verify account. Try again.",
                                            Toast.LENGTH_SHORT).show();
                                })
                );

            } catch (Exception e) {
                android.util.Log.e("FB_EMAIL", "Exception: " + e.getMessage());
                activity.runOnUiThread(() ->
                        Toast.makeText(activity,
                                "Failed to get Facebook profile.",
                                Toast.LENGTH_SHORT).show());
            }
        });

        Bundle parameters = new Bundle();
        parameters.putString("fields", "first_name,last_name,email");
        request.setParameters(parameters);
        request.executeAsync();
    }
    // ════════════════════════════════════════════════════════
    //  TERMS MODAL
    // ════════════════════════════════════════════════════════

    private void showTermsModal() {
        dimBackground.setVisibility(View.VISIBLE);
        cardModal.setVisibility(View.VISIBLE);
    }

    private void hideTermsModal() {
        dimBackground.setVisibility(View.GONE);
        cardModal.setVisibility(View.GONE);
    }
}