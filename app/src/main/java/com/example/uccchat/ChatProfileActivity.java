package com.example.uccchat;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.LayoutInflater;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ChatProfileActivity extends AppCompatActivity {

    // ── Intent data ───────────────────────────────────────────
    private String chatId;
    private String chatName;
    private String chatPhoto;
    private boolean isGroup;
    private String otherUid; // for 1-on-1 only

    // ── State ─────────────────────────────────────────────────
    private boolean isMuted   = false;
    private boolean isBlocked = false;
    private List<String> participants = new ArrayList<>();

    // ── Views ─────────────────────────────────────────────────
    private ImageView imgProfile;
    private TextView tvName, tvBlockLabel;
    private LinearLayout btnMute, btnSearchChat;
    private LinearLayout btnBlock, btnClearChat;
    private LinearLayout btnAddMembers, btnLeaveGroup, btnDeleteGroup;

    // ── Search overlay views ──────────────────────────────────
    private FrameLayout searchOverlay;
    private EditText searchChatInput;
    private TextView searchEmptyText;
    private ScrollView searchScrollView;
    private LinearLayout searchResultsContainer;

    // ── Auth ──────────────────────────────────────────────────
    private String myUid;

    // ── Image picker for group photo ──────────────────────────
    private final ActivityResultLauncher<String> imagePicker =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> { if (uri != null) uploadGroupPhoto(uri); });

    // ════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.clickchatprofile);

        chatId    = getIntent().getStringExtra("chatId");
        chatName  = getIntent().getStringExtra("chatName");
        chatPhoto = getIntent().getStringExtra("chatPhoto");
        isGroup   = getIntent().getBooleanExtra("isGroup", false);
        otherUid  = getIntent().getStringExtra("otherUid");

        myUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        bindViews();
        setupHeader();
        loadParticipants();
        setupSharedButtons();
        setupSearchOverlay();

        if (isGroup) {
            setupGroupMode();
        } else {
            setupOneOnOneMode();
        }
    }

    // ════════════════════════════════════════════════════════
    //  BIND
    // ════════════════════════════════════════════════════════

    private void bindViews() {
        imgProfile    = findViewById(R.id.imgProfileInfo);
        tvName        = findViewById(R.id.studentName);
        tvBlockLabel  = findViewById(R.id.tvBlockLabel);

        btnMute       = findViewById(R.id.btnMute);
        btnSearchChat = findViewById(R.id.btnSearchChat);

        btnBlock      = findViewById(R.id.btnBlock);
        btnClearChat  = findViewById(R.id.btnClearChat);

        btnAddMembers  = findViewById(R.id.btnAddMembers);
        btnLeaveGroup  = findViewById(R.id.btnLeaveGroup);
        btnDeleteGroup = findViewById(R.id.btnDeleteGroup);

        // Search overlay
        searchOverlay          = findViewById(R.id.searchOverlay);
        searchChatInput        = findViewById(R.id.searchChatInput);
        searchEmptyText        = findViewById(R.id.searchEmptyText);
        searchScrollView       = findViewById(R.id.searchScrollView);
        searchResultsContainer = findViewById(R.id.searchResultsContainer);
    }

    // ════════════════════════════════════════════════════════
    //  HEADER
    // ════════════════════════════════════════════════════════

    private void setupHeader() {
        tvName.setText(chatName != null ? chatName : "");

        loadPhoto(chatPhoto);

        // Back button
        View backBtn = findViewById(R.id.backBtn);
        if (backBtn != null) backBtn.setOnClickListener(v -> finish());

        // Group photo — tap to change
        if (isGroup) {
            imgProfile.setOnClickListener(v -> imagePicker.launch("image/*"));
            // Show a small edit hint
            Toast.makeText(this,
                    "Tap the photo to change it",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void loadPhoto(String url) {
        if (url != null && !url.isEmpty()) {
            Glide.with(this)
                    .load(url)
                    .transform(new CircleCrop())
                    .placeholder(R.drawable.circle_grey_bg)
                    .into(imgProfile);
        } else {
            imgProfile.setImageResource(R.drawable.circle_grey_bg);
        }
    }

    // ════════════════════════════════════════════════════════
    //  LOAD PARTICIPANTS
    // ════════════════════════════════════════════════════════

    private void loadParticipants() {
        FirebaseFirestore.getInstance()
                .collection(FirestoreHelper.COL_CHATS)
                .document(chatId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    List<String> p = (List<String>) doc.get("participants");
                    if (p != null) participants = p;
                });
    }

    // ════════════════════════════════════════════════════════
    //  SHARED BUTTONS (Mute + Search)
    // ════════════════════════════════════════════════════════

    private void setupSharedButtons() {
        // ── MUTE ──────────────────────────────────────────────
        FirestoreHelper.get().isChatMuted(myUid, chatId, isMutedNow -> {
            isMuted = isMutedNow;
            updateMuteButton();
        });

        if (btnMute != null) {
            btnMute.setOnClickListener(v -> toggleMute());
        }

        // ── SEARCH ────────────────────────────────────────────
        if (btnSearchChat != null) {
            btnSearchChat.setOnClickListener(v -> openSearchOverlay());
        }
    }

    private void updateMuteButton() {
        if (btnMute == null) return;
        TextView tvMuteLabel = btnMute.findViewWithTag("muteLabel");

        // Find the TextView inside btnMute
        for (int i = 0; i < btnMute.getChildCount(); i++) {
            View child = btnMute.getChildAt(i);
            if (child instanceof TextView) {
                ((TextView) child).setText(isMuted ? "Unmute" : "Mute");
            }
        }
    }

    private void toggleMute() {
        if (isMuted) {
            FirestoreHelper.get().unmuteChat(myUid, chatId,
                    new FirestoreHelper.OnActionComplete() {
                        @Override public void onSuccess() {
                            isMuted = false;
                            updateMuteButton();
                            Toast.makeText(ChatProfileActivity.this,
                                    "Chat unmuted.", Toast.LENGTH_SHORT).show();
                        }
                        @Override public void onFailure(String e) {
                            Toast.makeText(ChatProfileActivity.this,
                                    "Failed to unmute.", Toast.LENGTH_SHORT).show();
                        }
                    });
        } else {
            FirestoreHelper.get().muteChat(myUid, chatId,
                    new FirestoreHelper.OnActionComplete() {
                        @Override public void onSuccess() {
                            isMuted = true;
                            updateMuteButton();
                            Toast.makeText(ChatProfileActivity.this,
                                    "Chat muted.", Toast.LENGTH_SHORT).show();
                        }
                        @Override public void onFailure(String e) {
                            Toast.makeText(ChatProfileActivity.this,
                                    "Failed to mute.", Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    // ════════════════════════════════════════════════════════
    //  SEARCH OVERLAY
    // ════════════════════════════════════════════════════════

    private void setupSearchOverlay() {
        if (searchOverlay == null) return;

        // Back button inside overlay
        View searchBackBtn = findViewById(R.id.searchBackBtn);
        if (searchBackBtn != null) {
            searchBackBtn.setOnClickListener(v -> closeSearchOverlay());
        }

        // Search input listener
        if (searchChatInput != null) {
            searchChatInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s,
                                                        int i, int c, int a) {}
                @Override public void afterTextChanged(Editable s) {}
                @Override
                public void onTextChanged(CharSequence s,
                                          int start, int b, int count) {
                    String query = s.toString().trim();
                    if (query.isEmpty()) {
                        searchScrollView.setVisibility(View.GONE);
                        searchEmptyText.setVisibility(View.VISIBLE);
                    } else {
                        searchMessages(query);
                    }
                }
            });
        }
    }

    private void openSearchOverlay() {
        if (searchOverlay != null) {
            searchOverlay.setVisibility(View.VISIBLE);
            if (searchChatInput != null) searchChatInput.requestFocus();
        }
    }

    private void closeSearchOverlay() {
        if (searchOverlay != null) {
            searchOverlay.setVisibility(View.GONE);
            if (searchChatInput != null) searchChatInput.setText("");
        }
    }

    private void searchMessages(String query) {
        // Fetch messages and filter by query
        FirebaseFirestore.getInstance()
                .collection(FirestoreHelper.COL_CHATS)
                .document(chatId)
                .collection(FirestoreHelper.COL_MESSAGES)
                .orderBy("timestamp",
                        com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshots -> {
                    List<MessageModel> results = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        MessageModel msg = doc.toObject(MessageModel.class);
                        if (msg != null
                                && msg.isTextMessage()
                                && msg.getText() != null
                                && msg.getText().toLowerCase()
                                .contains(query.toLowerCase())) {
                            results.add(msg);
                        }
                    }
                    showSearchResults(results);
                });
    }

    private void showSearchResults(List<MessageModel> results) {
        if (searchResultsContainer == null) return;
        searchResultsContainer.removeAllViews();

        if (results.isEmpty()) {
            searchScrollView.setVisibility(View.GONE);
            searchEmptyText.setVisibility(View.VISIBLE);
            searchEmptyText.setText("No messages found.");
            return;
        }

        searchEmptyText.setVisibility(View.GONE);
        searchScrollView.setVisibility(View.VISIBLE);

        for (MessageModel msg : results) {
            View item = LayoutInflater.from(this)
                    .inflate(R.layout.item_chat, searchResultsContainer, false);

            TextView tvName    = item.findViewById(R.id.tvName);
            TextView tvMsg     = item.findViewById(R.id.tvLastMessage);
            TextView tvTime    = item.findViewById(R.id.tvTimestamp);
            ImageView imgAv    = item.findViewById(R.id.imgAvatar);

            tvName.setText(msg.getSenderId().equals(myUid) ? "You" : chatName);
            tvMsg.setText(msg.getText());
            if (msg.getTimestamp() != null) {
                tvTime.setText(android.text.format.DateFormat
                        .format("h:mm a", msg.getTimestamp().toDate()));
            }

            searchResultsContainer.addView(item);
        }
    }

    // ════════════════════════════════════════════════════════
    //  1-ON-1 MODE
    // ════════════════════════════════════════════════════════

    private void setupOneOnOneMode() {
        if (btnBlock    != null) btnBlock.setVisibility(View.VISIBLE);
        if (btnClearChat!= null) btnClearChat.setVisibility(View.VISIBLE);
        if (btnAddMembers  != null) btnAddMembers.setVisibility(View.GONE);
        if (btnLeaveGroup  != null) btnLeaveGroup.setVisibility(View.GONE);
        if (btnDeleteGroup != null) btnDeleteGroup.setVisibility(View.GONE);

        // ── BLOCK ─────────────────────────────────────────────
        if (otherUid != null) {
            FirestoreHelper.get().isUserBlocked(myUid, otherUid, blocked -> {
                isBlocked = blocked;
                updateBlockButton();
            });
        }

        if (btnBlock != null) {
            btnBlock.setOnClickListener(v -> toggleBlock());
        }

        // ── DELETE CONVERSATION ───────────────────────────────
        if (btnClearChat != null) {
            btnClearChat.setOnClickListener(v ->
                    new IosDialog(this)
                            .setTitle("Delete Conversation")
                            .setMessage("This will delete the conversation for you. Continue?")
                            .setOkText("Delete")
                            .setDestructive()
                            .onOk(this::deleteConversation)
                            .show());
        }
    }

    private void updateBlockButton() {
        if (tvBlockLabel == null) return;
        String otherName = chatName != null ? chatName : "User";
        tvBlockLabel.setText(isBlocked
                ? "Unblock " + otherName
                : "Block " + otherName);
        tvBlockLabel.setTextColor(isBlocked
                ? 0xFF4CAF50  // green when blocked (to unblock)
                : 0xFFE53935); // red when not blocked
    }

    private void toggleBlock() {
        String otherName = chatName != null ? chatName : "User";
        if (isBlocked) {
            new IosDialog(this)
                    .setTitle("Unblock " + otherName)
                    .setMessage("They will be able to message you again.")
                    .setOkText("Unblock")
                    .onOk(() -> {
                        FirestoreHelper.get().unblockUser(myUid, otherUid,
                                new FirestoreHelper.OnActionComplete() {
                                    @Override public void onSuccess() {
                                        isBlocked = false;
                                        updateBlockButton();
                                        Toast.makeText(ChatProfileActivity.this,
                                                otherName + " unblocked.",
                                                Toast.LENGTH_SHORT).show();
                                    }
                                    @Override public void onFailure(String e) {
                                        Toast.makeText(ChatProfileActivity.this,
                                                "Failed to unblock.",
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
                    })
                    .show();
        } else {
            new IosDialog(this)
                    .setTitle("Block " + otherName)
                    .setMessage("They won't be able to send you messages.")
                    .setOkText("Block")
                    .setDestructive()
                    .onOk(() -> {
                        FirestoreHelper.get().blockUser(myUid, otherUid,
                                new FirestoreHelper.OnActionComplete() {
                                    @Override public void onSuccess() {
                                        isBlocked = true;
                                        updateBlockButton();
                                        Toast.makeText(ChatProfileActivity.this,
                                                otherName + " blocked.",
                                                Toast.LENGTH_SHORT).show();
                                    }
                                    @Override public void onFailure(String e) {
                                        Toast.makeText(ChatProfileActivity.this,
                                                "Failed to block.",
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
                    })
                    .show();
        }
    }

    private void deleteConversation() {
        FirestoreHelper.get().deleteConversation(
                chatId, myUid, participants,
                new FirestoreHelper.OnActionComplete() {
                    @Override public void onSuccess() {
                        Toast.makeText(ChatProfileActivity.this,
                                "Conversation deleted.",
                                Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(
                                ChatProfileActivity.this,
                                ChatHomeActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                        finish();
                    }
                    @Override public void onFailure(String e) {
                        Toast.makeText(ChatProfileActivity.this,
                                "Failed to delete.",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ════════════════════════════════════════════════════════
    //  GROUP MODE
    // ════════════════════════════════════════════════════════

    private void setupGroupMode() {
        if (btnBlock    != null) btnBlock.setVisibility(View.GONE);
        if (btnClearChat!= null) btnClearChat.setVisibility(View.GONE);
        if (btnAddMembers != null) btnAddMembers.setVisibility(View.VISIBLE);
        if (btnLeaveGroup != null) btnLeaveGroup.setVisibility(View.VISIBLE);

        // ── ADD MEMBERS ───────────────────────────────────────
        if (btnAddMembers != null) {
            btnAddMembers.setOnClickListener(v -> openAddMembersSheet());
        }

        // ── LEAVE GROUP ───────────────────────────────────────
        if (btnLeaveGroup != null) {
            btnLeaveGroup.setOnClickListener(v ->
                    new IosDialog(this)
                            .setTitle("Leave Group")
                            .setMessage("You will no longer receive messages from this group.")
                            .setOkText("Leave")
                            .setDestructive()
                            .onOk(this::leaveGroup)
                            .show());
        }

        // ── DELETE GROUP (creator only) ───────────────────────
        checkIfCreator();
    }

    private void checkIfCreator() {
        FirebaseFirestore.getInstance()
                .collection(FirestoreHelper.COL_CHATS)
                .document(chatId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    String createdBy = doc.getString("createdBy");
                    if (myUid != null && myUid.equals(createdBy)) {
                        if (btnDeleteGroup != null) {
                            btnDeleteGroup.setVisibility(View.VISIBLE);
                            btnDeleteGroup.setOnClickListener(v ->
                                    new IosDialog(this)
                                            .setTitle("Delete Group")
                                            .setMessage("This will permanently delete the group for everyone.")
                                            .setOkText("Delete")
                                            .setDestructive()
                                            .onOk(this::deleteGroup)
                                            .show());
                        }
                    }
                });
    }

    // ════════════════════════════════════════════════════════
    //  ADD MEMBERS SHEET
    // ════════════════════════════════════════════════════════

    private void openAddMembersSheet() {
        FirebaseFirestore.getInstance()
                .collection(FirestoreHelper.COL_USERS)
                .get()
                .addOnSuccessListener(snapshots -> {
                    List<UserModel> available = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        if (doc.getId().equals(myUid)) continue;
                        if (participants.contains(doc.getId())) continue;
                        UserModel user = doc.toObject(UserModel.class);
                        if (user != null) {
                            user.setUid(doc.getId());
                            available.add(user);
                        }
                    }

                    if (available.isEmpty()) {
                        new IosDialog(this)
                                .setTitle("No users to add")
                                .setMessage("Everyone is already in this group!")
                                .setOkText("OK")
                                .show();
                        return;
                    }

                    showAddMemberDialog(available);
                });
    }

    private void showAddMemberDialog(List<UserModel> available) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_add_member, null);
        dialog.setContentView(dialogView);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(
                            android.graphics.Color.TRANSPARENT));
        }

        // ✅ Fix 1 & 2 — use dialogView.findViewById instead of dialog.findViewById
        LinearLayout memberListContainer =
                dialogView.findViewById(R.id.memberListContainer);
        TextView btnCancel =
                dialogView.findViewById(R.id.btnCancelAddMember);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        for (UserModel user : available) {
            View item = LayoutInflater.from(this)
                    .inflate(R.layout.item_add_member,
                            memberListContainer, false);

            ImageView imgAvatar = item.findViewById(R.id.imgMemberAvatar);
            TextView tvName     = item.findViewById(R.id.tvMemberName);

            tvName.setText(user.getFirstName() + " " + user.getLastName());

            if (user.getPhotoUrl() != null && !user.getPhotoUrl().isEmpty()) {
                Glide.with(this)
                        .load(user.getPhotoUrl())
                        .transform(new CircleCrop())
                        .placeholder(R.drawable.circle_grey_bg)
                        .into(imgAvatar);
            } else {
                imgAvatar.setImageResource(R.drawable.circle_grey_bg);
            }

            item.setOnClickListener(v -> {
                dialog.dismiss();
                new IosDialog(this)
                        .setTitle("Add Member")
                        .setMessage("Add " + user.getFirstName()
                                + " " + user.getLastName()
                                + " to this group?")
                        .setOkText("Add")
                        .onOk(() -> addMemberToGroup(user))
                        .show();
            });

            memberListContainer.addView(item);
        }

        dialog.show();
    }

    // ✅ Fix 3 — renamed to addMemberToGroup to avoid confusion
    private void addMemberToGroup(UserModel user) {
        FirestoreHelper.get().addMemberToGroup(chatId, user,
                new FirestoreHelper.OnActionComplete() {
                    @Override
                    public void onSuccess() {
                        participants.add(user.getUid());
                        Toast.makeText(ChatProfileActivity.this,
                                user.getFirstName() + " added! ✅",
                                Toast.LENGTH_SHORT).show();
                    }
                    @Override
                    public void onFailure(String e) {
                        Toast.makeText(ChatProfileActivity.this,
                                "Failed to add member.",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }



    private void addMember(UserModel user) {
        FirestoreHelper.get().addMemberToGroup(chatId, user,
                new FirestoreHelper.OnActionComplete() {
                    @Override public void onSuccess() {
                        participants.add(user.getUid());
                        Toast.makeText(ChatProfileActivity.this,
                                user.getFirstName() + " added to group!",
                                Toast.LENGTH_SHORT).show();
                    }
                    @Override public void onFailure(String e) {
                        Toast.makeText(ChatProfileActivity.this,
                                "Failed to add member.",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ════════════════════════════════════════════════════════
    //  LEAVE / DELETE GROUP
    // ════════════════════════════════════════════════════════

    private void leaveGroup() {
        FirebaseFirestore.getInstance()
                .collection(FirestoreHelper.COL_CHATS)
                .document(chatId)
                .update("participants", FieldValue.arrayRemove(myUid))
                .addOnSuccessListener(u -> {
                    Toast.makeText(this,
                            "You left the group.", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(this, ChatHomeActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Failed to leave.", Toast.LENGTH_SHORT).show());
    }

    private void deleteGroup() {
        FirebaseFirestore.getInstance()
                .collection(FirestoreHelper.COL_CHATS)
                .document(chatId)
                .collection(FirestoreHelper.COL_MESSAGES)
                .get()
                .addOnSuccessListener(snap -> {
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        doc.getReference().delete();
                    }
                    FirebaseFirestore.getInstance()
                            .collection(FirestoreHelper.COL_CHATS)
                            .document(chatId)
                            .delete()
                            .addOnSuccessListener(u -> {
                                Toast.makeText(this,
                                        "Group deleted.",
                                        Toast.LENGTH_SHORT).show();
                                Intent intent = new Intent(
                                        this, ChatHomeActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                startActivity(intent);
                                finish();
                            });
                });
    }

    // ════════════════════════════════════════════════════════
    //  GROUP PHOTO UPLOAD
    // ════════════════════════════════════════════════════════

    private void uploadGroupPhoto(Uri uri) {
        Toast.makeText(this, "Uploading photo...", Toast.LENGTH_SHORT).show();

        MediaManager.get()
                .upload(uri)
                .option("public_id", "group_pictures/" + chatId)
                .option("upload_preset", "ucchat_profiles")
                .callback(new UploadCallback() {
                    @Override public void onStart(String r) {}
                    @Override public void onProgress(String r,
                                                     long b, long t) {}
                    @Override public void onReschedule(String r,
                                                       ErrorInfo e) {}

                    @Override
                    public void onSuccess(String requestId,
                                          Map resultData) {
                        String url = (String) resultData.get("secure_url");
                        FirestoreHelper.get().updateGroupPhoto(chatId, url,
                                new FirestoreHelper.OnActionComplete() {
                                    @Override public void onSuccess() {
                                        runOnUiThread(() -> {
                                            loadPhoto(url);
                                            Toast.makeText(
                                                    ChatProfileActivity.this,
                                                    "Group photo updated! ✅",
                                                    Toast.LENGTH_SHORT).show();
                                        });
                                    }
                                    @Override public void onFailure(String e) {
                                        runOnUiThread(() ->
                                                Toast.makeText(
                                                        ChatProfileActivity.this,
                                                        "Failed to update photo.",
                                                        Toast.LENGTH_SHORT).show());
                                    }
                                });
                    }

                    @Override
                    public void onError(String r, ErrorInfo e) {
                        runOnUiThread(() ->
                                Toast.makeText(ChatProfileActivity.this,
                                        "Upload failed.",
                                        Toast.LENGTH_SHORT).show());
                    }
                })
                .dispatch();
    }

    // ════════════════════════════════════════════════════════
    //  BACK PRESS
    // ════════════════════════════════════════════════════════

    @Override
    public void onBackPressed() {
        if (searchOverlay != null
                && searchOverlay.getVisibility() == View.VISIBLE) {
            closeSearchOverlay();
        } else {
            super.onBackPressed();
        }
    }
}