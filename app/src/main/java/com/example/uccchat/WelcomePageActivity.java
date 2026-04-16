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

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(
                GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
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

        // ✅ "Sign In" link now shows privacy policy first
        TextView tvSignIn = findViewById(R.id.tvSignIn);
        String signInText = "Don't have an account? Sign In";
        SpannableString signInSpannable = new SpannableString(signInText);
        int signInStart = signInText.indexOf("Sign In");
        signInSpannable.setSpan(new UnderlineSpan(),
                signInStart, signInText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        signInSpannable.setSpan(new ClickableSpan() {
            @Override public void onClick(@NonNull View widget) {
                // ✅ Show privacy policy before going to signup
                showPrivacyPolicyThenProceed(() ->
                        startActivity(new Intent(WelcomePageActivity.this,
                                SignUpPage1Activity.class)));
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
            // ✅ Show privacy policy before signup
            showPrivacyPolicyThenProceed(() ->
                    startActivity(new Intent(WelcomePageActivity.this,
                            SignUpPage1Activity.class)));
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        // ── Existing user — go straight to chat ──
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

                        String idToken = account.getIdToken();

                        if (idToken != null) {
                            com.google.firebase.auth.AuthCredential credential =
                                    com.google.firebase.auth.GoogleAuthProvider
                                            .getCredential(idToken, null);

                            FirebaseAuth.getInstance().signInWithCredential(credential)
                                    .addOnCompleteListener(task ->
                                            runOnUiThread(() -> goToChat()));
                        } else {
                            runOnUiThread(() -> goToChat());
                        }

                    } else {
                        // ── New Google user → show privacy policy first ──
                        runOnUiThread(() ->
                                showPrivacyPolicyThenProceed(() ->
                                        startActivity(new Intent(WelcomePageActivity.this,
                                                SignUpPage1Activity.class))));
                    }
                })
                .addOnFailureListener(e ->
                        runOnUiThread(() ->
                                Toast.makeText(WelcomePageActivity.this,
                                        "Could not verify account. Try again.",
                                        Toast.LENGTH_SHORT).show()));
    }

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

                // ── No email from Facebook ──
                if (email.isEmpty()) {
                    activity.runOnUiThread(() -> {
                        UserSession.firstName      = firstName;
                        UserSession.lastName       = lastName;
                        UserSession.isFromFacebook = true;
                        // ✅ Show privacy policy for new Facebook user
                        showPrivacyPolicyThenProceed(() ->
                                activity.startActivity(
                                        new Intent(activity, SignUpPage1Activity.class)));
                    });
                    return;
                }

                // ── Check if user already exists in Firestore ──
                activity.runOnUiThread(() ->
                        FirebaseFirestore.getInstance()
                                .collection("users")
                                .whereEqualTo("email", email)
                                .get()
                                .addOnSuccessListener(querySnapshot -> {

                                    if (!querySnapshot.isEmpty()) {
                                        // ── EXISTING USER → sign in directly ──
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

                                        com.google.firebase.auth.AuthCredential credential =
                                                com.google.firebase.auth.FacebookAuthProvider
                                                        .getCredential(accessToken.getToken());

                                        FirebaseAuth.getInstance()
                                                .signInWithCredential(credential)
                                                .addOnCompleteListener(task -> goToChat());

                                    } else {
                                        // ── NEW Facebook user → show privacy policy first ──
                                        UserSession.firstName      = firstName;
                                        UserSession.lastName       = lastName;
                                        UserSession.email          = email;
                                        UserSession.isFromFacebook = true;
                                        UserSession.facebookEmailAlreadyExists = false;
                                        showPrivacyPolicyThenProceed(() ->
                                                activity.startActivity(
                                                        new Intent(activity,
                                                                SignUpPage1Activity.class)));
                                    }
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(activity,
                                                "Could not verify account. Try again.",
                                                Toast.LENGTH_SHORT).show())
                );

            } catch (Exception e) {
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
    //  PRIVACY POLICY POPUP
    // ════════════════════════════════════════════════════════

    private void showPrivacyPolicyThenProceed(Runnable onAgree) {
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
        tvSub.setText("Please read and scroll to the bottom before continuing.");
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
                        "UCChat collects your name, email, student ID, and profile photo for " +
                        "account creation and identification within the app.\n\n" +
                        "How We Use Your Information\n" +
                        "Your information is used solely to provide the UCChat messaging service " +
                        "to University of Caloocan City students and staff.\n\n" +
                        "Data Storage\n" +
                        "Your data is stored securely using Google Firebase services.\n\n" +
                        "Third Party Services\n" +
                        "We use Firebase (Google) and Cloudinary for data storage and " +
                        "media hosting.\n\n" +
                        "User Rights\n" +
                        "You may delete your account and all associated data at any time " +
                        "through Menu → Delete Account.\n\n" +
                        "Contact\n" +
                        "For questions, contact: universityofcaloocancity@gmail.com\n\n" +
                        "By tapping Agree, you confirm that you have read and agree to our " +
                        "Privacy Policy and Terms of Use."
        );
        scrollView.addView(tvContent);
        root.addView(scrollView);

        // Divider
        android.view.View divider = new android.view.View(this);
        divider.setBackgroundColor(0xFFE0E0E0);
        divider.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 2));
        root.addView(divider);

        // Button row
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

        android.widget.TextView btnAgreeNew = new android.widget.TextView(this);
        btnAgreeNew.setText("Agree");
        btnAgreeNew.setGravity(android.view.Gravity.CENTER);
        btnAgreeNew.setTextSize(16f);
        btnAgreeNew.setTextColor(0xFFFFFFFF);
        btnAgreeNew.setTypeface(null, android.graphics.Typeface.BOLD);
        btnAgreeNew.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        android.graphics.drawable.GradientDrawable agreeBg =
                new android.graphics.drawable.GradientDrawable();
        agreeBg.setColor(0xFF4CAF50);
        btnAgreeNew.setBackground(agreeBg);
        btnAgreeNew.setEnabled(false);
        btnAgreeNew.setAlpha(0.4f);

        // ✅ Enable Agree only after scrolling to bottom
        scrollView.getViewTreeObserver().addOnScrollChangedListener(() -> {
            android.view.View child = scrollView.getChildAt(0);
            if (child != null) {
                int diff = child.getBottom() -
                        (scrollView.getHeight() + scrollView.getScrollY());
                if (diff <= 10) {
                    btnAgreeNew.setEnabled(true);
                    btnAgreeNew.setAlpha(1f);
                }
            }
        });

        btnAgreeNew.setOnClickListener(v -> {
            dialog.dismiss();
            onAgree.run();
        });

        btnRow.addView(btnCancel);
        btnRow.addView(vDiv);
        btnRow.addView(btnAgreeNew);
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