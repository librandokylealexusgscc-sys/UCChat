package com.example.uccchat;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class NewChatActivity extends AppCompatActivity {

    // ── Views from newchat.xml ────────────────────────────────
    private EditText etSearch;
    private LinearLayout containerResults;
    private TextView tvSuggestedLabel;
    private String myCourse = "";

    // ── Firebase ──────────────────────────────────────────────
    private String myUid;
    private final List<UserModel> allUsers  = new ArrayList<>();
    private final List<UserModel> displayed = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.newchat);

        myUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (myUid == null) {
            finish();
            return;
        }

        bindViews();
        setupBackButton();
        setupSearch();
        loadAllUsers();
    }

    // ════════════════════════════════════════════════════════
    //  SETUP
    // ════════════════════════════════════════════════════════

    private void bindViews() {
        etSearch         = findViewById(R.id.SearchStudent);
        containerResults = findViewById(R.id.newChatScrollview);

        // ✅ Hide group name input — not needed for 1-on-1 chat
        View groupNameLayout = findViewById(R.id.ForwardMessage);
        if (groupNameLayout != null) groupNameLayout.setVisibility(View.GONE);

        // Also hide the divider line below it
        // Find the parent LinearLayout and hide it
        View groupNameContainer = findViewById(R.id.ForwardMessage);
        if (groupNameContainer != null) {
            View parent = (View) groupNameContainer.getParent();
            if (parent != null) parent.setVisibility(View.GONE);
        }

        // Hide Create Group button
        View btnNewChat = findViewById(R.id.btnNewchat);
        if (btnNewChat != null) btnNewChat.setVisibility(View.GONE);
    }

    private void setupBackButton() {
        View backBtn = findViewById(R.id.backBtn);
        if (backBtn != null) {
            backBtn.setOnClickListener(v -> finish());
        }
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterUsers(s.toString().trim());
            }
        });
    }

    // ════════════════════════════════════════════════════════
    //  LOAD USERS FROM FIRESTORE
    // ════════════════════════════════════════════════════════

    private void loadAllUsers() {
        FirebaseFirestore.getInstance()
                .collection(FirestoreHelper.COL_USERS)
                .document(myUid)
                .get()
                .addOnSuccessListener(doc -> {

                    myCourse = doc.getString("course");

                    FirebaseFirestore.getInstance()
                            .collection(FirestoreHelper.COL_USERS)
                            .get()
                            .addOnSuccessListener(querySnapshot -> {

                                allUsers.clear();

                                for (DocumentSnapshot d : querySnapshot.getDocuments()) {
                                    if (d.getId().equals(myUid)) continue;

                                    UserModel user = d.toObject(UserModel.class);
                                    if (user != null) {
                                        user.setUid(d.getId());
                                        allUsers.add(user);
                                    }
                                }

                                filterUsers(""); // show suggested
                            });
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Failed to load users.",
                                Toast.LENGTH_SHORT).show());
    }

    // ════════════════════════════════════════════════════════
    //  FILTER + RENDER
    // ════════════════════════════════════════════════════════

    private void filterUsers(String query) {
        displayed.clear();

        if (query.isEmpty()) {
            // 🔥 SUGGESTED: same course only
            for (UserModel user : allUsers) {
                if (user.getCourse() != null &&
                        user.getCourse().equals(myCourse)) {
                    displayed.add(user);
                }
            }
        } else {
            String lower = query.toLowerCase();

            for (UserModel user : allUsers) {
                if ((user.getFirstName() != null &&
                        user.getFirstName().toLowerCase().contains(lower))
                        || (user.getLastName() != null &&
                        user.getLastName().toLowerCase().contains(lower))
                        || (user.getUsername() != null &&
                        user.getUsername().toLowerCase().contains(lower))
                        || (user.getStudentId() != null &&
                        user.getStudentId().toLowerCase().contains(lower))) {

                    displayed.add(user);
                }
            }
        }

        renderUserList();
    }


    private void renderUserList() {
        // Clear existing views (except the static student item placeholder)
        containerResults.removeAllViews();

        if (displayed.isEmpty()) {
            // Show "no results" message
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText("No users found.");
            tvEmpty.setTextColor(0xFF888888);
            tvEmpty.setPadding(32, 24, 32, 24);
            containerResults.addView(tvEmpty);
            return;
        }

        for (UserModel user : displayed) {
            View itemView = LayoutInflater.from(this)
                    .inflate(R.layout.item_user, containerResults, false);

            ImageView imgProfile = itemView.findViewById(R.id.userProfile);
            TextView tvName      = itemView.findViewById(R.id.studentName);

            // Full name
            tvName.setText(user.getFirstName() + " " + user.getLastName());

            // Profile photo
            if (user.getPhotoUrl() != null && !user.getPhotoUrl().isEmpty()) {
                Glide.with(this)
                        .load(user.getPhotoUrl())
                        .transform(new CircleCrop())
                        .placeholder(R.drawable.circle_grey_bg)
                        .into(imgProfile);
            } else {
                imgProfile.setImageResource(R.drawable.circle_grey_bg);
            }

            // Click → open or create chat
            itemView.setOnClickListener(v -> startChatWith(user));

            containerResults.addView(itemView);
        }
    }

    // ════════════════════════════════════════════════════════
    //  START CHAT
    // ════════════════════════════════════════════════════════

    private void startChatWith(UserModel otherUser) {
        // First get current user's full data
        FirestoreHelper.get().getUser(myUid,
                new FirestoreHelper.OnUserFetched() {
                    @Override
                    public void onSuccess(UserModel currentUser) {
                        // Get or create a chat between the two users
                        FirestoreHelper.get().getOrCreateChat(
                                currentUser,
                                otherUser,
                                new FirestoreHelper.OnChatReady() {
                                    @Override
                                    public void onReady(String chatId) {
                                        // Open ChatActivity
                                        Intent intent = new Intent(
                                                NewChatActivity.this,
                                                ChatActivity.class);
                                        intent.putExtra("chatId",
                                                chatId);
                                        intent.putExtra("chatName",
                                                otherUser.getFirstName()
                                                        + " "
                                                        + otherUser.getLastName());
                                        intent.putExtra("chatPhoto",
                                                otherUser.getPhotoUrl());
                                        intent.putExtra("isGroup", false);
                                        startActivity(intent);
                                        finish();
                                    }

                                    @Override
                                    public void onFailure(String error) {
                                        Toast.makeText(NewChatActivity.this,
                                                "Could not start chat.",
                                                Toast.LENGTH_SHORT).show();
                                    }
                                }
                        );
                    }

                    @Override
                    public void onFailure(String error) {
                        Toast.makeText(NewChatActivity.this,
                                "Could not load your profile.",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}