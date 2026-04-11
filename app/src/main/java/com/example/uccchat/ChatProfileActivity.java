package com.example.uccchat;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
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
import java.util.concurrent.atomic.AtomicInteger;

public class ChatProfileActivity extends AppCompatActivity {

    private static final String TAG = "ChatProfileActivity";

    private String chatId;
    private String chatName;
    private String chatPhoto;
    private boolean isGroup;
    private String otherUid;

    private boolean isMuted   = false;
    private boolean isBlocked = false;
    private List<String> participants = new ArrayList<>();

    private ImageView imgProfile;
    private TextView tvName, tvBlockLabel;
    private LinearLayout btnMute, btnSearchChat;
    private LinearLayout btnBlock, btnClearChat;
    private LinearLayout btnAddMembers, btnLeaveGroup, btnDeleteGroup;

    private FrameLayout searchOverlay;
    private EditText searchChatInput;
    private TextView searchEmptyText;
    private ScrollView searchScrollView;
    private LinearLayout searchResultsContainer;

    private String myUid;

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

        Log.d(TAG, "=== ChatProfileActivity started ===");
        Log.d(TAG, "chatId: " + chatId);
        Log.d(TAG, "chatPhoto: [" + chatPhoto + "]");
        Log.d(TAG, "isGroup: " + isGroup);

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
        searchOverlay          = findViewById(R.id.searchOverlay);
        searchChatInput        = findViewById(R.id.searchChatInput);
        searchEmptyText        = findViewById(R.id.searchEmptyText);
        searchScrollView       = findViewById(R.id.searchScrollView);
        searchResultsContainer = findViewById(R.id.searchResultsContainer);
        Log.d(TAG, "imgProfile found: " + (imgProfile != null));
    }

    // ════════════════════════════════════════════════════════
    //  HEADER
    // ════════════════════════════════════════════════════════

    private void setupHeader() {
        tvName.setText(chatName != null ? chatName : "");

        if (isGroup) {
            boolean hasCustomPhoto = chatPhoto != null
                    && !chatPhoto.isEmpty()
                    && !chatPhoto.equals("null");

            if (hasCustomPhoto) {
                Log.d(TAG, "Loading custom group photo");
                Glide.with(this)
                        .load(chatPhoto)
                        .transform(new CircleCrop())
                        .placeholder(R.drawable.circle_grey_bg)
                        .into(imgProfile);
            } else {
                Log.d(TAG, "No custom photo — building composite");
                loadCompositeGroupPhoto();
            }
            imgProfile.setOnClickListener(v -> imagePicker.launch("image/*"));

        } else {
            loadPhoto(chatPhoto);
        }

        View backBtn = findViewById(R.id.backBtn);
        if (backBtn != null) backBtn.setOnClickListener(v -> finish());
    }

    private void loadPhoto(String url) {
        if (url != null && !url.isEmpty() && !url.equals("null")) {
            Glide.with(this).load(url)
                    .transform(new CircleCrop())
                    .placeholder(R.drawable.circle_grey_bg)
                    .into(imgProfile);
        } else {
            imgProfile.setImageResource(R.drawable.circle_grey_bg);
        }
    }

    // ════════════════════════════════════════════════════════
    //  COMPOSITE GROUP PHOTO
    // ════════════════════════════════════════════════════════

    private void loadCompositeGroupPhoto() {
        Log.d(TAG, "loadCompositeGroupPhoto() chatId=" + chatId);

        FirebaseFirestore.getInstance()
                .collection(FirestoreHelper.COL_CHATS)
                .document(chatId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        imgProfile.setImageResource(R.drawable.circle_grey_bg);
                        return;
                    }

                    List<String> pList = (List<String>) doc.get("participants");
                    Log.d(TAG, "participants: " + pList);

                    if (pList == null || pList.isEmpty()) {
                        imgProfile.setImageResource(R.drawable.circle_grey_bg);
                        return;
                    }

                    List<String> uidsToUse = new ArrayList<>();
                    for (String uid : pList) {
                        uidsToUse.add(uid);
                        if (uidsToUse.size() == 4) break;
                    }

                    Map<String, String> photoMap =
                            (Map<String, String>) doc.get("participantPhotos");
                    Log.d(TAG, "participantPhotos: " + photoMap);

                    List<String> cachedUrls = new ArrayList<>();
                    if (photoMap != null) {
                        for (String uid : uidsToUse) {
                            String url = photoMap.get(uid);
                            if (url != null && !url.isEmpty()) {
                                cachedUrls.add(url);
                            }
                        }
                    }

                    Log.d(TAG, "cachedUrls count: " + cachedUrls.size());

                    if (cachedUrls.size() >= 1) {
                        downloadAndComposite(cachedUrls);
                    } else {
                        fetchUserPhotosAndComposite(uidsToUse);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed: " + e.getMessage());
                    imgProfile.setImageResource(R.drawable.circle_grey_bg);
                });
    }

    private void fetchUserPhotosAndComposite(List<String> uids) {
        Log.d(TAG, "fetchUserPhotosAndComposite() " + uids.size() + " uids");
        List<String> photoUrls = new ArrayList<>();
        AtomicInteger done = new AtomicInteger(0);
        int total = uids.size();

        for (String uid : uids) {
            FirebaseFirestore.getInstance()
                    .collection(FirestoreHelper.COL_USERS)
                    .document(uid)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null
                                && task.getResult().exists()) {
                            String url = task.getResult().getString("photoUrl");
                            Log.d(TAG, "uid=" + uid + " photoUrl=" + url);
                            if (url != null && !url.isEmpty()) {
                                synchronized (photoUrls) { photoUrls.add(url); }
                            }
                        }
                        if (done.incrementAndGet() == total) {
                            Log.d(TAG, "All fetched, urls=" + photoUrls.size());
                            runOnUiThread(() -> downloadAndComposite(photoUrls));
                        }
                    });
        }
    }

    private void downloadAndComposite(List<String> photoUrls) {
        Log.d(TAG, "downloadAndComposite() urls=" + photoUrls.size());

        if (photoUrls.isEmpty()) {
            imgProfile.setImageResource(R.drawable.circle_grey_bg);
            return;
        }

        if (photoUrls.size() == 1) {
            Glide.with(this).load(photoUrls.get(0))
                    .transform(new CircleCrop())
                    .placeholder(R.drawable.circle_grey_bg)
                    .into(imgProfile);
            return;
        }

        // Tile to always fill 4 slots:
        // 2 photos → [0,1,0,1]
        // 3 photos → [0,1,2,0]
        // 4 photos → [0,1,2,3]
        final List<String> tiled = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tiled.add(photoUrls.get(i % photoUrls.size()));
        }

        final int size     = 400;
        final int cellSize = size / 2;
        final Bitmap[] bitmaps = new Bitmap[4];
        final AtomicInteger loaded = new AtomicInteger(0);

        for (int i = 0; i < 4; i++) {
            final int index = i;
            Glide.with(this)
                    .asBitmap()
                    .load(tiled.get(i))
                    .override(cellSize, cellSize)
                    .centerCrop()
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(Bitmap resource,
                                                    Transition<? super Bitmap> transition) {
                            Log.d(TAG, "bitmap " + index + " OK");
                            bitmaps[index] = resource;
                            if (loaded.incrementAndGet() == 4)
                                runOnUiThread(() -> buildAndSetComposite(bitmaps, size));
                        }
                        @Override
                        public void onLoadCleared(android.graphics.drawable.Drawable p) {}
                        @Override
                        public void onLoadFailed(android.graphics.drawable.Drawable e) {
                            Log.e(TAG, "bitmap " + index + " failed");
                            if (loaded.incrementAndGet() == 4)
                                runOnUiThread(() -> buildAndSetComposite(bitmaps, size));
                        }
                    });
        }
    }

    private void buildAndSetComposite(Bitmap[] bitmaps, int size) {
        Log.d(TAG, "buildAndSetComposite()");

        int half = size / 2;
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        // Grey background for empty slots
        Paint bgPaint = new Paint();
        bgPaint.setColor(0xFFCCCCCC);
        canvas.drawRect(0, 0, size, size, bgPaint);

        int gap = 3;
        int[][] positions = {
                {0,    0   },
                {half, 0   },
                {0,    half},
                {half, half}
        };

        Paint bmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        for (int i = 0; i < 4; i++) {
            if (bitmaps[i] == null) continue;
            int left   = positions[i][0] + (i % 2 == 1 ? gap / 2 : 0);
            int top    = positions[i][1] + (i >= 2     ? gap / 2 : 0);
            int right  = positions[i][0] + half - (i % 2 == 0 ? gap / 2 : 0);
            int bottom = positions[i][1] + half - (i <  2     ? gap / 2 : 0);
            canvas.drawBitmap(bitmaps[i], null,
                    new Rect(left, top, right, bottom), bmpPaint);
        }

        // Divider lines
        Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(0x33000000);
        linePaint.setStrokeWidth(gap);
        canvas.drawLine(half, 0, half, size, linePaint);
        canvas.drawLine(0, half, size, half, linePaint);

        // Circular mask — DST_IN keeps content only inside circle
        Bitmap circleMask = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas maskCanvas = new Canvas(circleMask);
        Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        maskPaint.setColor(0xFFFFFFFF);
        maskCanvas.drawOval(new RectF(0, 0, size, size), maskPaint);

        Paint maskApplyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        maskApplyPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        canvas.drawBitmap(circleMask, 0, 0, maskApplyPaint);

        Log.d(TAG, "Composite done — setting bitmap");
        imgProfile.setImageBitmap(output);
        imgProfile.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imgProfile.setPadding(0, 0, 0, 0);
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
    //  SHARED BUTTONS
    // ════════════════════════════════════════════════════════

    private void setupSharedButtons() {
        FirestoreHelper.get().isChatMuted(myUid, chatId, isMutedNow -> {
            isMuted = isMutedNow;
            updateMuteButton();
        });
        if (btnMute != null) btnMute.setOnClickListener(v -> toggleMute());
        if (btnSearchChat != null) btnSearchChat.setOnClickListener(v -> openSearchOverlay());
    }

    private void updateMuteButton() {
        if (btnMute == null) return;
        for (int i = 0; i < btnMute.getChildCount(); i++) {
            View child = btnMute.getChildAt(i);
            if (child instanceof TextView)
                ((TextView) child).setText(isMuted ? "Unmute" : "Mute");
        }
    }

    private void toggleMute() {
        if (isMuted) {
            FirestoreHelper.get().unmuteChat(myUid, chatId,
                    new FirestoreHelper.OnActionComplete() {
                        @Override public void onSuccess() {
                            isMuted = false; updateMuteButton();
                            Toast.makeText(ChatProfileActivity.this,
                                    "Chat unmuted.", Toast.LENGTH_SHORT).show();
                        }
                        @Override public void onFailure(String e) {
                            Toast.makeText(ChatProfileActivity.this,
                                    "Failed.", Toast.LENGTH_SHORT).show();
                        }
                    });
        } else {
            FirestoreHelper.get().muteChat(myUid, chatId,
                    new FirestoreHelper.OnActionComplete() {
                        @Override public void onSuccess() {
                            isMuted = true; updateMuteButton();
                            Toast.makeText(ChatProfileActivity.this,
                                    "Chat muted.", Toast.LENGTH_SHORT).show();
                        }
                        @Override public void onFailure(String e) {
                            Toast.makeText(ChatProfileActivity.this,
                                    "Failed.", Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    // ════════════════════════════════════════════════════════
    //  SEARCH OVERLAY
    // ════════════════════════════════════════════════════════

    private void setupSearchOverlay() {
        if (searchOverlay == null) return;
        View searchBackBtn = findViewById(R.id.searchBackBtn);
        if (searchBackBtn != null)
            searchBackBtn.setOnClickListener(v -> closeSearchOverlay());

        if (searchChatInput != null) {
            searchChatInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
                @Override public void afterTextChanged(Editable s) {}
                @Override public void onTextChanged(CharSequence s, int start, int b, int count) {
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

                        if (msg != null) {
                            msg.setMessageId(doc.getId());
                        }

                        if (msg != null && msg.isTextMessage()
                                && msg.getText() != null
                                && msg.getText().toLowerCase().contains(query.toLowerCase())) {
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
            TextView tvNameV  = item.findViewById(R.id.tvName);
            TextView tvMsg    = item.findViewById(R.id.tvLastMessage);
            TextView tvTime   = item.findViewById(R.id.tvTimestamp);
            ImageView imgAvatar = item.findViewById(R.id.imgAvatar);

            boolean isMine = msg.getSenderId().equals(myUid);
            tvNameV.setText(isMine ? "You" : chatName);
            tvMsg.setText(msg.getText());

            // Load profile photo
            if (imgAvatar != null) {
                if (isMine) {
                    // Load my own photo from Firestore
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            .collection(FirestoreHelper.COL_USERS)
                            .document(myUid)
                            .get()
                            .addOnSuccessListener(doc -> {
                                String myPhoto = doc.getString("photoUrl");
                                if (myPhoto != null && !myPhoto.isEmpty()) {
                                    Glide.with(this).load(myPhoto)
                                            .transform(new CircleCrop())
                                            .placeholder(R.drawable.circle_grey_bg)
                                            .into(imgAvatar);
                                } else {
                                    imgAvatar.setImageResource(R.drawable.circle_grey_bg);
                                }
                            });
                } else {
                    // Load the other person's photo (already available as chatPhoto)
                    if (chatPhoto != null && !chatPhoto.isEmpty()) {
                        Glide.with(this).load(chatPhoto)
                                .transform(new CircleCrop())
                                .placeholder(R.drawable.circle_grey_bg)
                                .into(imgAvatar);
                    } else {
                        imgAvatar.setImageResource(R.drawable.circle_grey_bg);
                    }
                }
            }

            if (msg.getTimestamp() != null) {
                tvTime.setText(android.text.format.DateFormat
                        .format("h:mm a", msg.getTimestamp().toDate()));
            }

            item.setOnClickListener(v -> {
                Intent intent = new Intent(ChatProfileActivity.this, ChatActivity.class);
                intent.putExtra("chatId", chatId);
                intent.putExtra("chatName", chatName);
                intent.putExtra("chatPhoto", chatPhoto);
                intent.putExtra("isGroup", isGroup);
                intent.putExtra("otherUid", otherUid);
                intent.putExtra("highlightMessageId", msg.getMessageId());
                startActivity(intent);
            });
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

        if (otherUid != null) {
            FirestoreHelper.get().isUserBlocked(myUid, otherUid, blocked -> {
                isBlocked = blocked;
                updateBlockButton();
            });
        }
        if (btnBlock != null) btnBlock.setOnClickListener(v -> toggleBlock());
        if (btnClearChat != null) {
            btnClearChat.setOnClickListener(v ->
                    new IosDialog(this)
                            .setTitle("Delete Conversation")
                            .setMessage("This will delete the conversation for you. Continue?")
                            .setOkText("Delete").setDestructive()
                            .onOk(this::deleteConversation).show());
        }
    }

    private void updateBlockButton() {
        if (tvBlockLabel == null) return;
        String otherName = chatName != null ? chatName : "User";
        tvBlockLabel.setText(isBlocked ? "Unblock " + otherName : "Block " + otherName);
        tvBlockLabel.setTextColor(isBlocked ? 0xFF4CAF50 : 0xFFE53935);
    }

    private void toggleBlock() {
        String otherName = chatName != null ? chatName : "User";
        if (isBlocked) {
            new IosDialog(this).setTitle("Unblock " + otherName)
                    .setMessage("They will be able to message you again.")
                    .setOkText("Unblock")
                    .onOk(() -> FirestoreHelper.get().unblockUser(myUid, otherUid,
                            new FirestoreHelper.OnActionComplete() {
                                @Override public void onSuccess() {
                                    isBlocked = false; updateBlockButton();
                                    Toast.makeText(ChatProfileActivity.this,
                                            otherName + " unblocked.", Toast.LENGTH_SHORT).show();
                                }
                                @Override public void onFailure(String e) {
                                    Toast.makeText(ChatProfileActivity.this,
                                            "Failed.", Toast.LENGTH_SHORT).show();
                                }
                            })).show();
        } else {
            new IosDialog(this).setTitle("Block " + otherName)
                    .setMessage("They won't be able to send you messages.")
                    .setOkText("Block").setDestructive()
                    .onOk(() -> FirestoreHelper.get().blockUser(myUid, otherUid,
                            new FirestoreHelper.OnActionComplete() {
                                @Override public void onSuccess() {
                                    isBlocked = true; updateBlockButton();
                                    Toast.makeText(ChatProfileActivity.this,
                                            otherName + " blocked.", Toast.LENGTH_SHORT).show();
                                }
                                @Override public void onFailure(String e) {
                                    Toast.makeText(ChatProfileActivity.this,
                                            "Failed.", Toast.LENGTH_SHORT).show();
                                }
                            })).show();
        }
    }

    private void deleteConversation() {
        FirestoreHelper.get().deleteConversation(chatId, myUid, participants,
                new FirestoreHelper.OnActionComplete() {
                    @Override public void onSuccess() {
                        Toast.makeText(ChatProfileActivity.this,
                                "Conversation deleted.", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(ChatProfileActivity.this, ChatHomeActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent); finish();
                    }
                    @Override public void onFailure(String e) {
                        Toast.makeText(ChatProfileActivity.this,
                                "Failed to delete.", Toast.LENGTH_SHORT).show();
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

        if (btnAddMembers != null)
            btnAddMembers.setOnClickListener(v -> openAddMembersSheet());

        if (btnLeaveGroup != null) {
            btnLeaveGroup.setOnClickListener(v ->
                    new IosDialog(this).setTitle("Leave Group")
                            .setMessage("You will no longer receive messages from this group.")
                            .setOkText("Leave").setDestructive()
                            .onOk(this::leaveGroup).show());
        }

        if (tvName != null) tvName.setOnClickListener(v -> showRenameGroupDialog());
        checkIfCreator();
    }

    private void checkIfCreator() {
        FirebaseFirestore.getInstance()
                .collection(FirestoreHelper.COL_CHATS)
                .document(chatId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    String createdBy = doc.getString("createdBy");
                    if (myUid != null && myUid.equals(createdBy) && btnDeleteGroup != null) {
                        btnDeleteGroup.setVisibility(View.VISIBLE);
                        btnDeleteGroup.setOnClickListener(v ->
                                new IosDialog(this).setTitle("Delete Group")
                                        .setMessage("This will permanently delete the group for everyone.")
                                        .setOkText("Delete").setDestructive()
                                        .onOk(this::deleteGroup).show());
                    }
                });
    }

    // ════════════════════════════════════════════════════════
    //  ADD MEMBERS
    // ════════════════════════════════════════════════════════

    private void openAddMembersSheet() {
        FirebaseFirestore.getInstance()
                .collection(FirestoreHelper.COL_USERS).get()
                .addOnSuccessListener(snapshots -> {
                    List<UserModel> available = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        if (doc.getId().equals(myUid)) continue;
                        if (participants.contains(doc.getId())) continue;
                        UserModel user = doc.toObject(UserModel.class);
                        if (user != null) { user.setUid(doc.getId()); available.add(user); }
                    }
                    if (available.isEmpty()) {
                        new IosDialog(this).setTitle("No users to add")
                                .setMessage("Everyone is already in this group!")
                                .setOkText("OK").show();
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
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));

        LinearLayout memberListContainer = dialogView.findViewById(R.id.memberListContainer);
        TextView btnCancel = dialogView.findViewById(R.id.btnCancelAddMember);
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        for (UserModel user : available) {
            View item = LayoutInflater.from(this)
                    .inflate(R.layout.item_add_member, memberListContainer, false);
            ImageView imgAvatar = item.findViewById(R.id.imgMemberAvatar);
            TextView tvNameV    = item.findViewById(R.id.tvMemberName);
            tvNameV.setText(user.getFirstName() + " " + user.getLastName());
            if (user.getPhotoUrl() != null && !user.getPhotoUrl().isEmpty()) {
                Glide.with(this).load(user.getPhotoUrl())
                        .transform(new CircleCrop())
                        .placeholder(R.drawable.circle_grey_bg)
                        .into(imgAvatar);
            } else {
                imgAvatar.setImageResource(R.drawable.circle_grey_bg);
            }
            item.setOnClickListener(v -> {
                dialog.dismiss();
                new IosDialog(this).setTitle("Add Member")
                        .setMessage("Add " + user.getFirstName() + " " + user.getLastName() + "?")
                        .setOkText("Add").onOk(() -> addMemberToGroup(user)).show();
            });
            memberListContainer.addView(item);
        }
        dialog.show();
    }

    private void addMemberToGroup(UserModel user) {
        FirestoreHelper.get().addMemberToGroup(chatId, user,
                new FirestoreHelper.OnActionComplete() {
                    @Override public void onSuccess() {
                        participants.add(user.getUid());
                        Toast.makeText(ChatProfileActivity.this,
                                user.getFirstName() + " added! ✅", Toast.LENGTH_SHORT).show();
                    }
                    @Override public void onFailure(String e) {
                        Toast.makeText(ChatProfileActivity.this,
                                "Failed to add member.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ════════════════════════════════════════════════════════
    //  LEAVE / DELETE GROUP
    // ════════════════════════════════════════════════════════

    private void leaveGroup() {
        FirebaseFirestore.getInstance()
                .collection(FirestoreHelper.COL_CHATS).document(chatId)
                .update("participants", FieldValue.arrayRemove(myUid))
                .addOnSuccessListener(u -> {
                    Toast.makeText(this, "You left the group.", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(this, ChatHomeActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent); finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to leave.", Toast.LENGTH_SHORT).show());
    }

    private void deleteGroup() {
        FirebaseFirestore.getInstance()
                .collection(FirestoreHelper.COL_CHATS).document(chatId)
                .collection(FirestoreHelper.COL_MESSAGES).get()
                .addOnSuccessListener(snap -> {
                    for (DocumentSnapshot doc : snap.getDocuments()) doc.getReference().delete();
                    FirebaseFirestore.getInstance()
                            .collection(FirestoreHelper.COL_CHATS).document(chatId)
                            .delete()
                            .addOnSuccessListener(u -> {
                                Toast.makeText(this, "Group deleted.", Toast.LENGTH_SHORT).show();
                                Intent intent = new Intent(this, ChatHomeActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                startActivity(intent); finish();
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
                    @Override public void onProgress(String r, long b, long t) {}
                    @Override public void onReschedule(String r, ErrorInfo e) {}

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        String url = (String) resultData.get("secure_url");
                        FirestoreHelper.get().updateGroupPhoto(chatId, url,
                                new FirestoreHelper.OnActionComplete() {
                                    @Override public void onSuccess() {
                                        runOnUiThread(() -> {
                                            Glide.with(ChatProfileActivity.this)
                                                    .load(url)
                                                    .transform(new CircleCrop())
                                                    .placeholder(R.drawable.circle_grey_bg)
                                                    .into(imgProfile);
                                            chatPhoto = url;
                                            Toast.makeText(ChatProfileActivity.this,
                                                    "Group photo updated! ✅",
                                                    Toast.LENGTH_SHORT).show();
                                        });
                                    }
                                    @Override public void onFailure(String e) {
                                        runOnUiThread(() -> Toast.makeText(ChatProfileActivity.this,
                                                "Failed to update photo.", Toast.LENGTH_SHORT).show());
                                    }
                                });
                    }

                    @Override
                    public void onError(String r, ErrorInfo e) {
                        runOnUiThread(() -> Toast.makeText(ChatProfileActivity.this,
                                "Upload failed.", Toast.LENGTH_SHORT).show());
                    }
                }).dispatch();
    }

    // ════════════════════════════════════════════════════════
    //  RENAME GROUP DIALOG
    // ════════════════════════════════════════════════════════

    private void showRenameGroupDialog() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        bg.setCornerRadius(42f);
        root.setBackground(bg);
        root.setClipToOutline(true);

        android.widget.TextView tvTitle = new android.widget.TextView(this);
        tvTitle.setText("Change Chat Name");
        tvTitle.setTextSize(18f);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(0xFF000000);
        tvTitle.setGravity(android.view.Gravity.CENTER);
        tvTitle.setPadding(48, 52, 48, 10);

        android.widget.TextView tvSub = new android.widget.TextView(this);
        tvSub.setText("Changing the name of a group chat changes it for everyone.");
        tvSub.setTextSize(13f);
        tvSub.setTextColor(0xFF888888);
        tvSub.setGravity(android.view.Gravity.CENTER);
        tvSub.setPadding(48, 0, 48, 28);

        android.widget.LinearLayout inputContainer = new android.widget.LinearLayout(this);
        inputContainer.setOrientation(android.widget.LinearLayout.VERTICAL);
        inputContainer.setPadding(48, 0, 48, 36);

        android.widget.TextView tvHint = new android.widget.TextView(this);
        tvHint.setText("Chat name");
        tvHint.setTextSize(12f);
        tvHint.setTextColor(0xFF4CAF50);
        android.widget.LinearLayout.LayoutParams hintLp =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        hintLp.setMargins(16, 0, 0, 4);

        android.widget.EditText etName = new android.widget.EditText(this);
        etName.setText(chatName != null ? chatName : "");
        etName.setTextSize(15f);
        etName.setTextColor(0xFF000000);
        etName.setSingleLine(true);
        android.graphics.drawable.GradientDrawable inputBg =
                new android.graphics.drawable.GradientDrawable();
        inputBg.setColor(0xFFFFFFFF);
        inputBg.setStroke(4, 0xFF4CAF50);
        inputBg.setCornerRadius(16f);
        etName.setBackground(inputBg);
        etName.setPadding(32, 28, 32, 28);

        inputContainer.addView(tvHint, hintLp);
        inputContainer.addView(etName, new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

        android.view.View divider = new android.view.View(this);
        divider.setBackgroundColor(0xFFE0E0E0);
        divider.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 2));

        android.widget.LinearLayout btnRow = new android.widget.LinearLayout(this);
        btnRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        btnRow.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 140));

        android.widget.TextView btnCancel = new android.widget.TextView(this);
        btnCancel.setText("Cancel");
        btnCancel.setGravity(android.view.Gravity.CENTER);
        btnCancel.setTextSize(16f);
        btnCancel.setTextColor(0xFF4CAF50);
        btnCancel.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        android.graphics.drawable.GradientDrawable cancelBg =
                new android.graphics.drawable.GradientDrawable();
        cancelBg.setColor(0xFFFFFFFF);
        cancelBg.setCornerRadii(new float[]{0,0,0,0,0,0,42f,42f});
        btnCancel.setBackground(cancelBg);
        btnCancel.setClickable(true);
        btnCancel.setFocusable(true);
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        android.view.View vDivider = new android.view.View(this);
        vDivider.setBackgroundColor(0xFFE0E0E0);
        vDivider.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                2, android.widget.LinearLayout.LayoutParams.MATCH_PARENT));

        android.widget.TextView btnSave = new android.widget.TextView(this);
        btnSave.setText("Save");
        btnSave.setGravity(android.view.Gravity.CENTER);
        btnSave.setTextSize(16f);
        btnSave.setTextColor(0xFFFFFFFF);
        btnSave.setTypeface(null, android.graphics.Typeface.BOLD);
        btnSave.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        android.graphics.drawable.GradientDrawable saveBg =
                new android.graphics.drawable.GradientDrawable();
        saveBg.setColor(0xFF4CAF50);
        saveBg.setCornerRadii(new float[]{0,0,0,0,42f,42f,0,0});
        btnSave.setBackground(saveBg);
        btnSave.setClickable(true);
        btnSave.setFocusable(true);
        btnSave.setOnClickListener(v -> {
            String newName = etName.getText().toString().trim();
            if (newName.isEmpty()) {
                Toast.makeText(this, "Name cannot be empty.", Toast.LENGTH_SHORT).show();
                return;
            }
            FirebaseFirestore.getInstance()
                    .collection(FirestoreHelper.COL_CHATS).document(chatId)
                    .update("groupName", newName)
                    .addOnSuccessListener(u -> {
                        chatName = newName;
                        tvName.setText(newName);
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("updatedChatName", newName);
                        setResult(RESULT_OK, resultIntent);
                        Toast.makeText(this, "Group name updated! ✅", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Failed.", Toast.LENGTH_SHORT).show());
        });

        btnRow.addView(btnCancel);
        btnRow.addView(vDivider);
        btnRow.addView(btnSave);

        root.addView(tvTitle);
        root.addView(tvSub);
        root.addView(inputContainer);
        root.addView(divider);
        root.addView(btnRow);

        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.92f),
                    android.view.WindowManager.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
        etName.requestFocus();
        etName.setSelection(etName.getText().length());
    }

    // ════════════════════════════════════════════════════════
    //  BACK PRESS
    // ════════════════════════════════════════════════════════

    @Override
    public void onBackPressed() {
        if (searchOverlay != null && searchOverlay.getVisibility() == View.VISIBLE) {
            closeSearchOverlay();
        } else {
            super.onBackPressed();
        }
    }
}