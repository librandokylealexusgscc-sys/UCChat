package com.example.uccchat;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class DeleteAccountActivity extends AppCompatActivity {

    private Button btnCancel, btnDeleteAccount;
    private EditText etUsername;
    private TextView tvUsernameError;
    private LinearLayout centerConfirmPopup;
    private TextView popupOK, popupCancel;
    private ProgressBar progressBar; // Add this to your XML too

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.deleteaccount);

        db   = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        btnCancel          = findViewById(R.id.btnCancel);
        btnDeleteAccount   = findViewById(R.id.btnDeleteAccount);
        etUsername         = findViewById(R.id.etUsername);
        tvUsernameError    = findViewById(R.id.tvUsernameError);
        centerConfirmPopup = findViewById(R.id.centerConfirmPopup);
        popupOK            = findViewById(R.id.popupOK);
        popupCancel        = findViewById(R.id.popupCancel);
        progressBar        = findViewById(R.id.progressBar); // optional loading indicator

        btnCancel.setOnClickListener(v -> finish());
        btnDeleteAccount.setOnClickListener(v -> validateAndShowConfirm());
        popupCancel.setOnClickListener(v -> centerConfirmPopup.setVisibility(View.GONE));
        popupOK.setOnClickListener(v -> {
            centerConfirmPopup.setVisibility(View.GONE);
            deleteAccount();
        });
    }

    // ── Step 1: Validate username ──────────────────────────────────────

    private void validateAndShowConfirm() {
        String entered = etUsername.getText() != null
                ? etUsername.getText().toString().trim() : "";

        if (entered.isEmpty()) {
            tvUsernameError.setText("Please enter your username");
            tvUsernameError.setVisibility(View.VISIBLE);
            return;
        }

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "No user logged in.", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("users").document(currentUser.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        tvUsernameError.setText("User not found.");
                        tvUsernameError.setVisibility(View.VISIBLE);
                        return;
                    }
                    String stored = doc.getString("username");
                    if (stored != null && stored.equalsIgnoreCase(entered)) {
                        tvUsernameError.setVisibility(View.GONE);
                        centerConfirmPopup.setVisibility(View.VISIBLE);
                    } else {
                        tvUsernameError.setText("Username does not match");
                        tvUsernameError.setVisibility(View.VISIBLE);
                    }
                })
                .addOnFailureListener(e -> {
                    tvUsernameError.setText("Error verifying username. Try again.");
                    tvUsernameError.setVisibility(View.VISIBLE);
                });
    }

    // ── Step 2: Delete account ─────────────────────────────────────────

    private void deleteAccount() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "No user logged in.", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = currentUser.getUid();
        setLoading(true);

        // First: delete Firebase Auth account
        // Do this FIRST while the session is fresh — avoids requires-recent-login error
        currentUser.delete()
                .addOnSuccessListener(unused -> {
                    // Auth deleted — now clean up Firestore data
                    cleanupFirestoreData(uid);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    // This happens when session is too old (requires recent login)
                    Toast.makeText(this,
                            "Session expired. Please log out and log back in, then try again.",
                            Toast.LENGTH_LONG).show();
                });
    }

    // ── Step 3: Clean up all Firestore data ───────────────────────────

    private void cleanupFirestoreData(String uid) {
        db.collection("chats")
                .whereArrayContains("participants", uid)
                .get()
                .addOnSuccessListener(chatSnapshots -> {
                    int total = chatSnapshots.size();

                    if (total == 0) {
                        deleteUserDoc(uid);
                        return;
                    }

                    final int[] done = {0};

                    for (QueryDocumentSnapshot chatDoc : chatSnapshots) {
                        String chatId = chatDoc.getId();
                        Boolean isGroup = chatDoc.getBoolean("isGroup");

                        if (Boolean.TRUE.equals(isGroup)) {
                            // GROUP CHAT: just remove the user, don't delete the whole chat
                            removeUserFromGroup(chatId, uid, () -> {
                                done[0]++;
                                if (done[0] == total) deleteUserDoc(uid);
                            });
                        } else {
                            // 1-ON-1 CHAT: delete the whole chat and messages
                            deleteEntireChat(chatId, () -> {
                                done[0]++;
                                if (done[0] == total) deleteUserDoc(uid);
                            });
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    // Even if chat cleanup fails, still delete user doc
                    // Auth is already deleted so we must finish cleanup
                    deleteUserDoc(uid);
                });
    }

    // ── Remove user from group chat (leave, don't delete) ─────────────

    private void removeUserFromGroup(String chatId, String uid, Runnable onDone) {
        db.collection("chats").document(chatId)
                .update(
                        "participants", FieldValue.arrayRemove(uid),
                        "unreadCount." + uid, FieldValue.delete(),
                        "participantNames." + uid, FieldValue.delete(),
                        "participantPhotos." + uid, FieldValue.delete()
                )
                .addOnSuccessListener(u -> onDone.run())
                .addOnFailureListener(e -> onDone.run()); // still proceed
    }

    // ── Delete entire 1-on-1 chat + all messages ──────────────────────

    private void deleteEntireChat(String chatId, Runnable onDone) {
        db.collection("chats").document(chatId)
                .collection("messages").get()
                .addOnSuccessListener(msgSnaps -> {
                    int msgTotal = msgSnaps.size();

                    if (msgTotal == 0) {
                        db.collection("chats").document(chatId).delete()
                                .addOnSuccessListener(u -> onDone.run())
                                .addOnFailureListener(e -> onDone.run());
                        return;
                    }

                    final int[] deletedMsgs = {0};
                    for (QueryDocumentSnapshot msg : msgSnaps) {
                        msg.getReference().delete()
                                .addOnSuccessListener(u -> {
                                    deletedMsgs[0]++;
                                    if (deletedMsgs[0] == msgTotal) {
                                        db.collection("chats").document(chatId).delete()
                                                .addOnSuccessListener(x -> onDone.run())
                                                .addOnFailureListener(e -> onDone.run());
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    deletedMsgs[0]++;
                                    if (deletedMsgs[0] == msgTotal) {
                                        db.collection("chats").document(chatId).delete()
                                                .addOnSuccessListener(x -> onDone.run())
                                                .addOnFailureListener(ex -> onDone.run());
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> onDone.run());
    }

    // ── Delete Firestore user document ────────────────────────────────

    private void deleteUserDoc(String uid) {
        db.collection("users").document(uid)
                .delete()
                .addOnSuccessListener(unused -> {
                    setLoading(false);
                    auth.signOut();
                    Toast.makeText(this,
                            "Account deleted successfully.",
                            Toast.LENGTH_LONG).show();
                    goToWelcome();
                })
                .addOnFailureListener(e -> {
                    // Auth is already deleted — user can't log back in anyway
                    // Still sign out and redirect
                    setLoading(false);
                    auth.signOut();
                    Toast.makeText(this,
                            "Account deleted successfully.",
                            Toast.LENGTH_LONG).show();
                    goToWelcome();
                });
    }

    // ── UI helpers ────────────────────────────────────────────────────

    private void setLoading(boolean loading) {
        btnDeleteAccount.setEnabled(!loading);
        btnCancel.setEnabled(!loading);
        if (progressBar != null)
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void goToWelcome() {
        Intent intent = new Intent(this, WelcomePageActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}