package com.example.uccchat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import android.view.LayoutInflater;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ChatActivity extends AppCompatActivity {

    // ── Presence ──────────────────────────────────────────────
    private View onlineDot;
    private TextView tvProgramId;
    private ListenerRegistration presenceListener;
    private String otherUid;

    // ── Image viewer views ────────────────────────────────────
    private LinearLayout viewImageLayout;
    private ImageView imgViewFull;
    private ImageView imgViewBackBtn;

    // ── Intent extras ─────────────────────────────────────────
    private String chatId;
    private String chatName;
    private String chatPhoto;
    private boolean isGroup;

    // ── Auth ──────────────────────────────────────────────────
    private String myUid;

    // ── Views ─────────────────────────────────────────────────
    private ImageView imgProfile;
    private TextView tvName, tvStatus;
    private LinearLayout emptyConversationLayout;
    private RecyclerView recyclerMessages;
    private EditText etTypingBox;
    private ImageView btnSendMessage, btnSendFile, btnBack;
    private LinearLayout popupLongPress;
    private LinearLayout btnReply, btnCopy, btnForward, btnDelete;

    // ── Adapter ───────────────────────────────────────────────
    private MessageAdapter messageAdapter;

    // ── Firebase ──────────────────────────────────────────────
    private ListenerRegistration messageListener;

    // ── Selected message for long press ───────────────────────
    private MessageModel selectedMessage;

    // ── Image picker ──────────────────────────────────────────
    private final ActivityResultLauncher<String> imagePicker =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> { if (uri != null) uploadImageAndSend(uri); });

    // ════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ════════════════════════════════════════════════════════

    private final ActivityResultLauncher<String> videoPicker =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> { if (uri != null) handleVideoPicked(uri); });

    private void handleVideoPicked(Uri uri) {
        String fileName     = getFileName(uri);
        String videoDuration = getVideoDuration(uri);

        Toast.makeText(this, "Uploading video...",
                Toast.LENGTH_SHORT).show();

        MediaManager.get()
                .upload(uri)
                .option("resource_type", "video")
                .option("upload_preset", "ucchat_profiles")
                .option("public_id", "videos/" + chatId + "/"
                        + System.currentTimeMillis())
                .callback(new com.cloudinary.android.callback.UploadCallback() {
                    @Override public void onStart(String r) {}
                    @Override public void onReschedule(String r,
                                                       com.cloudinary.android.callback.ErrorInfo e) {}

                    @Override
                    public void onProgress(String requestId,
                                           long bytes, long totalBytes) {
                        int percent = (int) ((bytes * 100) / totalBytes);
                        runOnUiThread(() ->
                                Toast.makeText(ChatActivity.this,
                                        "Uploading... " + percent + "%",
                                        Toast.LENGTH_SHORT).show());
                    }

                    @Override
                    public void onSuccess(String requestId,
                                          java.util.Map resultData) {
                        String videoUrl    = (String) resultData.get("secure_url");
                        String thumbnailUrl = videoUrl
                                .replace("/upload/",
                                        "/upload/w_400,h_300,c_fill/")
                                .replace(".mp4", ".jpg")
                                .replace(".mov", ".jpg");

                        getOtherParticipants(otherUids ->
                                FirestoreHelper.get().sendVideoMessage(
                                        chatId, myUid,
                                        videoUrl, thumbnailUrl,
                                        videoDuration, otherUids,
                                        new FirestoreHelper.OnMessageSent() {
                                            @Override
                                            public void onSent(String id) {
                                                runOnUiThread(() ->
                                                        Toast.makeText(ChatActivity.this,
                                                                "Video sent! ✅",
                                                                Toast.LENGTH_SHORT).show());
                                            }
                                            @Override
                                            public void onFailure(String e) {
                                                runOnUiThread(() ->
                                                        Toast.makeText(ChatActivity.this,
                                                                "Failed to send video.",
                                                                Toast.LENGTH_SHORT).show());
                                            }
                                        }));
                    }

                    @Override
                    public void onError(String r,
                                        com.cloudinary.android.callback.ErrorInfo e) {
                        runOnUiThread(() ->
                                Toast.makeText(ChatActivity.this,
                                        "Upload failed.",
                                        Toast.LENGTH_SHORT).show());
                    }
                })
                .dispatch();
    }

    private String getVideoDuration(Uri uri) {
        try {
            android.media.MediaMetadataRetriever retriever =
                    new android.media.MediaMetadataRetriever();
            retriever.setDataSource(this, uri);
            String durationMs = retriever.extractMetadata(
                    android.media.MediaMetadataRetriever
                            .METADATA_KEY_DURATION);
            retriever.release();

            if (durationMs != null) {
                long totalSecs = Long.parseLong(durationMs) / 1000;
                long mins      = totalSecs / 60;
                long secs      = totalSecs % 60;
                return String.format(java.util.Locale.getDefault(),
                        "%d:%02d", mins, secs);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mainchat);

        chatId    = getIntent().getStringExtra("chatId");
        chatName  = getIntent().getStringExtra("chatName");
        chatPhoto = getIntent().getStringExtra("chatPhoto");
        isGroup   = getIntent().getBooleanExtra("isGroup", false);

        myUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (myUid == null || chatId == null) {
            Toast.makeText(this,
                    "Something went wrong.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        bindViews();
        setupImageViewer();
        replaceScrollViewWithRecyclerView();
        setupHeader();
        setupInputBar();
        setupLongPressPopup();
        startListeningToMessages();
        FirestoreHelper.get().markChatAsRead(chatId, myUid);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (messageListener  != null) messageListener.remove();
        if (presenceListener != null) presenceListener.remove(); // ✅ Add this
        // Set offline when leaving chat
        App.setOnlineStatus(false);
    }

    // ════════════════════════════════════════════════════════
    //  BIND VIEWS
    // ════════════════════════════════════════════════════════

    private void bindViews() {
        imgProfile              = findViewById(R.id.ImgProfile);
        tvName                  = findViewById(R.id.studentName);
        tvStatus                = findViewById(R.id.status);
        emptyConversationLayout = findViewById(R.id.emptyConversationLayout);
        etTypingBox             = findViewById(R.id.etTypingBox);
        btnSendMessage          = findViewById(R.id.SendMessage);
        btnSendFile             = findViewById(R.id.SendFile);
        btnBack                 = findViewById(R.id.btnviewImageback);
        popupLongPress          = findViewById(R.id.popup_afterlongpress);
        btnReply                = findViewById(R.id.btnReply);
        btnCopy                 = findViewById(R.id.btnCopy);
        btnForward              = findViewById(R.id.btnForward);
        btnDelete               = findViewById(R.id.btnDelete);
        onlineDot   = findViewById(R.id.onlineDot);
        tvProgramId = findViewById(R.id.tvProgramId);
    }

    // ════════════════════════════════════════════════════════
    //  REPLACE SCROLLVIEW WITH RECYCLERVIEW
    // ════════════════════════════════════════════════════════

    private void replaceScrollViewWithRecyclerView() {
        // mainchat.xml uses a ScrollView for messages
        // We hide it and programmatically add a RecyclerView
        ScrollView scrollView = findViewById(R.id.scrollViewMessages);
        FrameLayout container = findViewById(R.id.messageContainer);

        if (scrollView != null) scrollView.setVisibility(View.GONE);

        recyclerMessages = new RecyclerView(this);

        if (container != null) {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT);
            recyclerMessages.setLayoutParams(params);
            container.addView(recyclerMessages);
        }

        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        recyclerMessages.setLayoutManager(lm);

        messageAdapter = new MessageAdapter(this, chatPhoto);
        recyclerMessages.setAdapter(messageAdapter);

        // ✅ Wire up image click → full screen viewer
        messageAdapter.setOnImageClickListener(imageUrl ->
                openImageViewer(imageUrl));

        messageAdapter.setOnMessageLongClickListener((message, anchor) -> {
            selectedMessage = message;
            showLongPressPopup();


        });
    }


    private String getOtherUidFromChat() {
        // Get from participants — we already have chatId
        // Use a cached value if available
        return getIntent().getStringExtra("otherUid") != null
                ? getIntent().getStringExtra("otherUid") : "";
    }

    // ════════════════════════════════════════════════════════
    //  HEADER
    // ════════════════════════════════════════════════════════

    private void setupHeader() {
        tvName.setText(chatName != null ? chatName : "Chat");

        if (chatPhoto != null && !chatPhoto.isEmpty()) {
            Glide.with(this)
                    .load(chatPhoto)
                    .transform(new CircleCrop())
                    .placeholder(R.drawable.circle_grey_bg)
                    .into(imgProfile);
        } else {
            imgProfile.setImageResource(R.drawable.circle_grey_bg);
        }

        btnBack.setOnClickListener(v -> finish());

        if (isGroup) {
            // Group chat — show member count instead of status
            tvStatus.setText("Group Chat");
            tvStatus.setTextColor(0xFF888888);
            if (onlineDot != null) onlineDot.setVisibility(View.GONE);
            loadGroupMemberCount();
        } else {
            // 1-on-1 — listen to presence
            otherUid = getIntent().getStringExtra("otherUid");
            if (otherUid != null) {
                loadOtherUserInfo(otherUid);
                startPresenceListener(otherUid);
            }
        }

        // Tap profile → ChatProfileActivity
        imgProfile.setOnClickListener(v -> {
            Intent intent = new Intent(this, ChatProfileActivity.class);
            intent.putExtra("chatId",    chatId);
            intent.putExtra("chatName",  chatName);
            intent.putExtra("chatPhoto", chatPhoto);
            intent.putExtra("isGroup",   isGroup);
            if (!isGroup && otherUid != null) {
                intent.putExtra("otherUid", otherUid);
            }
            startActivity(intent);
        });
    }

    private void loadOtherUserInfo(String uid) {
        FirestoreHelper.get().getUser(uid, new FirestoreHelper.OnUserFetched() {
            @Override
            public void onSuccess(UserModel user) {
                // Show Course - StudentID below name
                if (tvProgramId != null) {
                    String info = "";
                    if (user.getCourse() != null && !user.getCourse().isEmpty()) {
                        info += user.getCourse();
                    }
                    if (user.getStudentId() != null
                            && !user.getStudentId().isEmpty()) {
                        info += (info.isEmpty() ? "" : " · ")
                                + user.getStudentId();
                    }
                    if (!info.isEmpty()) {
                        tvProgramId.setText(info);
                        tvProgramId.setVisibility(View.VISIBLE);
                    }
                }
            }
            @Override
            public void onFailure(String error) {}
        });
    }

    private void startPresenceListener(String uid) {
        presenceListener = PresenceHelper.listenToPresence(uid,
                (isOnline, statusText) -> runOnUiThread(() -> {
                    tvStatus.setText(statusText);
                    if (onlineDot != null) {
                        onlineDot.setVisibility(
                                isOnline ? View.VISIBLE : View.GONE);
                    }
                    tvStatus.setTextColor(isOnline
                            ? 0xFF4CAF50   // green when online
                            : 0xFF888888); // grey when offline
                }));
    }

    private void loadGroupMemberCount() {
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection(FirestoreHelper.COL_CHATS)
                .document(chatId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    java.util.List<String> p =
                            (java.util.List<String>) doc.get("participants");
                    if (p != null && tvProgramId != null) {
                        tvProgramId.setText(p.size() + " members");
                        tvProgramId.setVisibility(View.VISIBLE);
                    }
                });
    }

    // ════════════════════════════════════════════════════════
    //  INPUT BAR
    // ════════════════════════════════════════════════════════

    private void setupInputBar() {
        btnSendMessage.setOnClickListener(v -> sendTextMessage());
        btnSendFile.setOnClickListener(v -> showAttachmentDialog());

        // Dismiss popup when tapping the input
        etTypingBox.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) dismissLongPressPopup();
        });
    }

    // ════════════════════════════════════════════════════════
    //  LONG PRESS POPUP
    // ════════════════════════════════════════════════════════

    private void setupLongPressPopup() {
        btnReply.setOnClickListener(v -> {
            Toast.makeText(this, "Reply coming soon!", Toast.LENGTH_SHORT).show();
            dismissLongPressPopup();
        });

        btnCopy.setOnClickListener(v -> {
            if (selectedMessage != null && selectedMessage.isTextMessage()) {
                ClipboardManager cb =
                        (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cb.setPrimaryClip(ClipData.newPlainText(
                        "message", selectedMessage.getText()));
                Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show();
            }
            dismissLongPressPopup();
        });

        btnForward.setOnClickListener(v -> {
            Toast.makeText(this, "Forward coming soon!", Toast.LENGTH_SHORT).show();
            dismissLongPressPopup();
        });

        btnDelete.setOnClickListener(v -> {
            if (selectedMessage != null) {
                new IosDialog(this)
                        .setTitle("Delete Message")
                        .setMessage("Delete this message for you?")
                        .setOkText("Delete")
                        .setDestructive()
                        .onOk(() -> {
                            FirestoreHelper.get().deleteMessageFor(
                                    chatId,
                                    selectedMessage.getMessageId(),
                                    myUid);
                            Toast.makeText(this,
                                    "Message deleted.", Toast.LENGTH_SHORT).show();
                        })
                        .show();
            }
            dismissLongPressPopup();
        });
    }

    private void showAttachmentDialog() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_attachment, null);
        dialog.setContentView(dialogView);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(
                            android.graphics.Color.TRANSPARENT));
        }

        dialogView.findViewById(R.id.btnAttachImage)
                .setOnClickListener(v -> {
                    dialog.dismiss();
                    imagePicker.launch("image/*");
                });

        dialogView.findViewById(R.id.btnAttachVideo)
                .setOnClickListener(v -> {
                    dialog.dismiss();
                    videoPicker.launch("video/*");
                });

        dialogView.findViewById(R.id.btnAttachFile)
                .setOnClickListener(v -> {
                    dialog.dismiss();
                    filePicker.launch(new String[]{
                            "application/pdf",
                            "application/msword",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "application/vnd.ms-excel",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/vnd.ms-powerpoint",
                            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                            "text/plain",
                            "application/zip"
                    });
                });

        dialogView.findViewById(R.id.btnAttachCancel)
                .setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void handleFilePicked(Uri uri) {
        // Get file name and size
        String fileName = getFileName(uri);
        String fileSize = getFileSize(uri);
        String fileType = getFileExtension(fileName);

        Toast.makeText(this, "Uploading " + fileName + "...",
                Toast.LENGTH_SHORT).show();

        // Upload to Cloudinary as raw file
        MediaManager.get()
                .upload(uri)
                .option("resource_type", "raw")
                .option("upload_preset", "ucchat_profiles")
                .option("public_id", "files/" + chatId + "/"
                        + System.currentTimeMillis() + "_" + fileName)
                .callback(new com.cloudinary.android.callback.UploadCallback() {
                    @Override public void onStart(String r) {}
                    @Override public void onProgress(String r,
                                                     long b, long t) {}
                    @Override public void onReschedule(String r,
                                                       com.cloudinary.android.callback.ErrorInfo e) {}

                    @Override
                    public void onSuccess(String requestId,
                                          java.util.Map resultData) {
                        String fileUrl = (String) resultData.get("secure_url");
                        getOtherParticipants(otherUids ->
                                FirestoreHelper.get().sendFileMessage(
                                        chatId, myUid, fileUrl,
                                        fileName, fileSize, fileType,
                                        otherUids,
                                        new FirestoreHelper.OnMessageSent() {
                                            @Override public void onSent(String id) {
                                                runOnUiThread(() ->
                                                        Toast.makeText(ChatActivity.this,
                                                                "File sent! ✅",
                                                                Toast.LENGTH_SHORT).show());
                                            }
                                            @Override public void onFailure(String e) {
                                                runOnUiThread(() ->
                                                        Toast.makeText(ChatActivity.this,
                                                                "Failed to send file.",
                                                                Toast.LENGTH_SHORT).show());
                                            }
                                        }));
                    }

                    @Override
                    public void onError(String r,
                                        com.cloudinary.android.callback.ErrorInfo e) {
                        runOnUiThread(() ->
                                Toast.makeText(ChatActivity.this,
                                        "Upload failed: " + e.getDescription(),
                                        Toast.LENGTH_SHORT).show());
                    }
                })
                .dispatch();
    }

// ── File helpers ──────────────────────────────────────────

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = getContentResolver()
                    .query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(
                            android.provider.OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) result = cursor.getString(idx);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result != null ? result.lastIndexOf('/') : -1;
            if (cut != -1) result = result.substring(cut + 1);
        }
        return result != null ? result : "file";
    }

    private String getFileSize(Uri uri) {
        try (android.database.Cursor cursor = getContentResolver()
                .query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIdx = cursor.getColumnIndex(
                        android.provider.OpenableColumns.SIZE);
                if (sizeIdx >= 0) {
                    long bytes = cursor.getLong(sizeIdx);
                    if (bytes < 1024)
                        return bytes + " B";
                    else if (bytes < 1024 * 1024)
                        return String.format(java.util.Locale.getDefault(),
                                "%.1f KB", bytes / 1024.0);
                    else
                        return String.format(java.util.Locale.getDefault(),
                                "%.1f MB", bytes / (1024.0 * 1024));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    private String getFileExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "";
    }

    private void showLongPressPopup() {
        if (popupLongPress != null)
            popupLongPress.setVisibility(View.VISIBLE);
    }

    private void dismissLongPressPopup() {
        if (popupLongPress != null)
            popupLongPress.setVisibility(View.GONE);
        selectedMessage = null;
    }

    // ════════════════════════════════════════════════════════
    //  FIRESTORE — REAL TIME MESSAGES
    // ════════════════════════════════════════════════════════

    private void startListeningToMessages() {
        messageListener = FirestoreHelper.get()
                .listenToMessages(chatId)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;

                    List<MessageModel> msgs = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        MessageModel msg = doc.toObject(MessageModel.class);
                        if (msg != null && !msg.isDeletedFor(myUid)) {
                            msgs.add(msg);
                        }
                    }

                    messageAdapter.setMessages(msgs);

                    if (msgs.isEmpty()) {
                        emptyConversationLayout.setVisibility(View.VISIBLE);
                        recyclerMessages.setVisibility(View.GONE);
                    } else {
                        emptyConversationLayout.setVisibility(View.GONE);
                        recyclerMessages.setVisibility(View.VISIBLE);
                        recyclerMessages.scrollToPosition(msgs.size() - 1);
                    }

                    FirestoreHelper.get().markChatAsRead(chatId, myUid);
                });
    }

    // ════════════════════════════════════════════════════════
    //  SEND TEXT
    // ════════════════════════════════════════════════════════

    private void sendTextMessage() {
        String text = etTypingBox.getText().toString().trim();
        if (text.isEmpty()) return;
        etTypingBox.setText("");

        getOtherParticipants(otherUids ->
                FirestoreHelper.get().sendTextMessage(
                        chatId, myUid, text, otherUids,
                        new FirestoreHelper.OnMessageSent() {
                            @Override public void onSent(String id) {}
                            @Override public void onFailure(String e) {
                                Toast.makeText(ChatActivity.this,
                                        "Failed to send.", Toast.LENGTH_SHORT).show();
                            }
                        }));
    }

    // ════════════════════════════════════════════════════════
    //  SEND IMAGE
    // ════════════════════════════════════════════════════════

    private void uploadImageAndSend(Uri imageUri) {
        Toast.makeText(this, "Uploading image...", Toast.LENGTH_SHORT).show();

        MediaManager.get()
                .upload(imageUri)
                .option("upload_preset", "ucchat_profiles")
                .callback(new UploadCallback() {
                    @Override public void onStart(String r) {}
                    @Override public void onProgress(String r, long b, long t) {}
                    @Override public void onReschedule(String r, ErrorInfo e) {}

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        String url = (String) resultData.get("secure_url");
                        getOtherParticipants(otherUids ->
                                FirestoreHelper.get().sendImageMessage(
                                        chatId, myUid, url, otherUids,
                                        new FirestoreHelper.OnMessageSent() {
                                            @Override public void onSent(String id) {}
                                            @Override public void onFailure(String e) {
                                                Toast.makeText(ChatActivity.this,
                                                        "Failed to send image.",
                                                        Toast.LENGTH_SHORT).show();
                                            }
                                        }));
                    }

                    @Override
                    public void onError(String r, ErrorInfo e) {
                        runOnUiThread(() ->
                                Toast.makeText(ChatActivity.this,
                                        "Upload failed.", Toast.LENGTH_SHORT).show());
                    }
                })
                .dispatch();
    }

    //IMAGE VIEWER VIEWS

    private void setupImageViewer() {
        viewImageLayout = findViewById(R.id.viewImageLayout);
        imgViewFull     = findViewById(R.id.imgViewFull);
        imgViewBackBtn  = findViewById(R.id.imgViewBackBtn);

        // Back button closes the viewer
        if (imgViewBackBtn != null) {
            imgViewBackBtn.setOnClickListener(v -> closeImageViewer());
        }

        // Save button — stub for member
        View btnSave = findViewById(R.id.btnSaveDoc);
        if (btnSave != null) {
            btnSave.setOnClickListener(v ->
                    Toast.makeText(this,
                            "Save coming soon!", Toast.LENGTH_SHORT).show());
        }

        // Forward button — stub for member
        View btnForward = findViewById(R.id.btnForwardViewImage);
        if (btnForward != null) {
            btnForward.setOnClickListener(v ->
                    Toast.makeText(this,
                            "Forward coming soon!", Toast.LENGTH_SHORT).show());


        }

    }

    @Override
    public void onBackPressed() {
        if (viewImageLayout != null
                && viewImageLayout.getVisibility() == View.VISIBLE) {
            closeImageViewer();
        } else {
            super.onBackPressed();
        }
    }

    public void openImageViewer(String imageUrl) {
        if (viewImageLayout == null || imgViewFull == null) return;

        Glide.with(this)
                .load(imageUrl)
                .placeholder(R.drawable.circle_grey_bg)
                .into(imgViewFull);

        viewImageLayout.setVisibility(View.VISIBLE);
    }

    private void closeImageViewer() {
        if (viewImageLayout != null) {
            viewImageLayout.setVisibility(View.GONE);
        }
    }

    private final ActivityResultLauncher<String[]> filePicker =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(),
                    uri -> { if (uri != null) handleFilePicked(uri); });

    // ════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════

    private void getOtherParticipants(OnParticipantsFetched callback) {
        FirebaseFirestore.getInstance()
                .collection(FirestoreHelper.COL_CHATS)
                .document(chatId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    List<String> participants =
                            (List<String>) doc.get("participants");
                    List<String> others = new ArrayList<>();
                    if (participants != null) {
                        for (String uid : participants) {
                            if (!uid.equals(myUid)) others.add(uid);
                        }
                    }
                    callback.onFetched(others);
                });
    }

    interface OnParticipantsFetched {
        void onFetched(List<String> otherUids);
    }


}