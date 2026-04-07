package com.example.uccchat;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NewGroupChatActivity extends AppCompatActivity {

    // ── Views ─────────────────────────────────────────────────
    private EditText etGroupName, etSearch;
    private LinearLayout containerResults;
    private Button btnCreate;

    // ── Data ──────────────────────────────────────────────────
    private String myUid;
    private final List<UserModel> allUsers      = new ArrayList<>();
    private final List<UserModel> displayed     = new ArrayList<>();
    private final List<UserModel> selectedUsers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.newchat);

        myUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (myUid == null) { finish(); return; }

        bindViews();
        setupBackButton();
        setupSearch();
        setupCreateButton();
        loadAllUsers();
    }

    // ════════════════════════════════════════════════════════
    //  SETUP
    // ════════════════════════════════════════════════════════

    private void bindViews() {
        etGroupName      = findViewById(R.id.ForwardMessage);
        etSearch         = findViewById(R.id.SearchStudent);
        containerResults = findViewById(R.id.newChatScrollview);
        btnCreate        = findViewById(R.id.btnNewchat);

        // Show Create Group button
        if (btnCreate != null) btnCreate.setVisibility(View.VISIBLE);

        // Change hint to group name
        if (etGroupName != null) {
            etGroupName.setHint("Group name (required)");
        }
    }

    private void setupBackButton() {
        View backBtn = findViewById(R.id.backBtn);
        if (backBtn != null) backBtn.setOnClickListener(v -> finish());
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int b, int count) {
                filterUsers(s.toString().trim());
            }
        });
    }

    private void setupCreateButton() {
        if (btnCreate == null) return;
        btnCreate.setOnClickListener(v -> {
            if (selectedUsers.size() < 2) {
                Toast.makeText(this,
                        "Select at least 2 members.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            // Group name is optional — auto-generate if empty
            String groupName = etGroupName != null
                    ? etGroupName.getText().toString().trim() : "";
            createGroupChat(groupName);
        });
    }

    // ════════════════════════════════════════════════════════
    //  LOAD USERS
    // ════════════════════════════════════════════════════════

    private void loadAllUsers() {
        FirebaseFirestore.getInstance()
                .collection(FirestoreHelper.COL_USERS)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    allUsers.clear();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        if (doc.getId().equals(myUid)) continue;
                        UserModel user = doc.toObject(UserModel.class);
                        if (user != null) {
                            user.setUid(doc.getId());
                            allUsers.add(user);
                        }
                    }
                    filterUsers("");
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Failed to load users.", Toast.LENGTH_SHORT).show());
    }

    // ════════════════════════════════════════════════════════
    //  FILTER + RENDER
    // ════════════════════════════════════════════════════════

    private void filterUsers(String query) {
        displayed.clear();
        if (query.isEmpty()) {
            displayed.addAll(allUsers);
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
        containerResults.removeAllViews();

        // Show selected count header
        if (!selectedUsers.isEmpty()) {
            TextView tvSelected = new TextView(this);
            tvSelected.setText("Selected: " + selectedUsers.size() + " member(s)");
            tvSelected.setTextColor(0xFF4CAF50);
            tvSelected.setTypeface(null, android.graphics.Typeface.BOLD);
            tvSelected.setPadding(32, 12, 32, 12);
            containerResults.addView(tvSelected);
        }

        if (displayed.isEmpty()) {
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText("No users found.");
            tvEmpty.setTextColor(0xFF888888);
            tvEmpty.setPadding(32, 24, 32, 24);
            containerResults.addView(tvEmpty);
            return;
        }

        for (UserModel user : displayed) {
            View itemView = LayoutInflater.from(this)
                    .inflate(R.layout.item_user_select, containerResults, false);

            ImageView imgProfile = itemView.findViewById(R.id.userProfile);
            TextView tvName      = itemView.findViewById(R.id.studentName);
            RadioButton rbSelect = itemView.findViewById(R.id.rbSelectedStudent);

            // Name
            tvName.setText(user.getFirstName() + " " + user.getLastName());

            // Radio button state
            boolean isSelected = selectedUsers.contains(user);
            rbSelect.setChecked(isSelected);

            // Highlight background if selected
            itemView.setBackgroundColor(
                    isSelected ? 0xFFE8F5E9 : 0xFFFFFFFF);

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

            // Toggle selection on tap
            itemView.setOnClickListener(v -> {
                if (selectedUsers.contains(user)) {
                    selectedUsers.remove(user);
                    rbSelect.setChecked(false);
                    itemView.setBackgroundColor(0xFFFFFFFF);
                } else {
                    selectedUsers.add(user);
                    rbSelect.setChecked(true);
                    itemView.setBackgroundColor(0xFFE8F5E9);
                }
                // Refresh selected count header only
                renderUserList();
            });

            containerResults.addView(itemView);
        }
    }
    // ════════════════════════════════════════════════════════
    //  CREATE GROUP CHAT
    // ════════════════════════════════════════════════════════

    private void createGroupChat(String groupName) {
        btnCreate.setEnabled(false);

        FirestoreHelper.get().getUser(myUid, new FirestoreHelper.OnUserFetched() {
            @Override
            public void onSuccess(UserModel currentUser) {

                List<String> participantUids          = new ArrayList<>();
                Map<String, String> participantNames  = new HashMap<>();
                Map<String, String> participantPhotos = new HashMap<>();

                // Add myself
                participantUids.add(myUid);
                participantNames.put(myUid,
                        currentUser.getFirstName() + " " + currentUser.getLastName());
                participantPhotos.put(myUid,
                        currentUser.getPhotoUrl() != null
                                ? currentUser.getPhotoUrl() : "");

                // Add selected members
                for (UserModel user : selectedUsers) {
                    participantUids.add(user.getUid());
                    participantNames.put(user.getUid(),
                            user.getFirstName() + " " + user.getLastName());
                    participantPhotos.put(user.getUid(),
                            user.getPhotoUrl() != null
                                    ? user.getPhotoUrl() : "");
                }

                // ✅ Auto-generate group name if empty
                // e.g. "Eli Jarder, Kyle Librando, Xyrine Calip"
                if (groupName.isEmpty()) {
                    List<String> names = new ArrayList<>();
                    // Add current user's first name
                    names.add(currentUser.getFirstName());
                    // Add selected members' first names
                    for (UserModel user : selectedUsers) {
                        names.add(user.getFirstName());
                    }
                    String autoName = android.text.TextUtils.join(", ", names);
                    proceedCreateGroup(autoName, participantUids,
                            participantNames, participantPhotos);
                } else {
                    proceedCreateGroup(groupName, participantUids,
                            participantNames, participantPhotos);
                }
            }

            @Override
            public void onFailure(String error) {
                btnCreate.setEnabled(true);
                Toast.makeText(NewGroupChatActivity.this,
                        "Failed to load your profile.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void proceedCreateGroup(String finalGroupName,
                                    List<String> participantUids,
                                    Map<String, String> participantNames,
                                    Map<String, String> participantPhotos) {
        FirestoreHelper.get().createGroupChat(
                participantUids,
                participantNames,
                participantPhotos,
                finalGroupName,
                myUid,
                new FirestoreHelper.OnChatReady() {
                    @Override
                    public void onReady(String chatId) {
                        Intent intent = new Intent(
                                NewGroupChatActivity.this,
                                ChatActivity.class);
                        intent.putExtra("chatId",    chatId);
                        intent.putExtra("chatName",  finalGroupName);
                        intent.putExtra("chatPhoto", "");
                        intent.putExtra("isGroup",   true);
                        startActivity(intent);
                        finish();
                    }

                    @Override
                    public void onFailure(String error) {
                        btnCreate.setEnabled(true);
                        Toast.makeText(NewGroupChatActivity.this,
                                "Failed to create group.",
                                Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }
}