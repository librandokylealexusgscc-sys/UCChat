package com.example.uccchat;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ChatActivity extends AppCompatActivity {

    // ── Presence ──────────────────────────────────────────────
    private View onlineDot;
    private TextView tvProgramId;
    private ListenerRegistration presenceListener;
    private String otherUid;

    // ── Intent extras ─────────────────────────────────────────
    private String chatId;
    private String chatName;
    private String chatPhoto;
    private boolean isGroup;
    private String highlightMessageId;

    // ── Auth ──────────────────────────────────────────────────
    private String myUid;

    private static final int REQ_CHAT_PROFILE  = 201;
    private static final int REQ_WRITE_STORAGE = 202;

    // ── Views ─────────────────────────────────────────────────
    private ImageView imgProfile;
    private TextView tvName, tvStatus;
    private LinearLayout emptyConversationLayout;
    private RecyclerView recyclerMessages;
    private EditText etTypingBox;
    private ImageView btnSendMessage, btnSendFile, btnBack;

    // ── Image viewer ──────────────────────────────────────────
    private LinearLayout viewImageLayout;
    private ImageView imgViewFull, imgViewBackBtn;
    private String currentViewedImageUrl;

    // ── Reply bar ─────────────────────────────────────────────
    private LinearLayout replyBar;
    private TextView tvReplyName, tvReplyPreview;
    private ImageView btnCloseReply;
    private MessageModel replyingTo = null;

    // ── Long press ────────────────────────────────────────────
    private MessageModel selectedMessage;

    // ── Adapter ───────────────────────────────────────────────
    private MessageAdapter messageAdapter;
    private ListenerRegistration messageListener;

    // ── Block ─────────────────────────────────────────────────
    private LinearLayout inputBar;
    private TextView tvBlockedBanner;
    private ListenerRegistration blockListener; // ✅ declared ONCE only

    // ── Undo ──────────────────────────────────────────────────
    private String lastSentMessageId = null;

    // ── Pickers ───────────────────────────────────────────────
    private final ActivityResultLauncher<String> imagePicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(),
                    uri -> { if (uri != null) uploadImageAndSend(uri); });

    private final ActivityResultLauncher<String> videoPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(),
                    uri -> { if (uri != null) handleVideoPicked(uri); });

    private final ActivityResultLauncher<String[]> filePicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                    uri -> { if (uri != null) handleFilePicked(uri); });

    // ════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mainchat);

        chatId             = getIntent().getStringExtra("chatId");
        chatName           = getIntent().getStringExtra("chatName");
        chatPhoto          = getIntent().getStringExtra("chatPhoto");
        isGroup            = getIntent().getBooleanExtra("isGroup", false);
        highlightMessageId = getIntent().getStringExtra("highlightMessageId");
        otherUid           = getIntent().getStringExtra("otherUid");

        myUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (myUid == null || chatId == null) {
            Toast.makeText(this, "Something went wrong.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ChatNotificationService.activeChatId = chatId;

        bindViews();
        setupReplyBar();
        setupImageViewer();
        replaceScrollViewWithRecyclerView();
        setupHeader();
        setupInputBar();

        FirestoreHelper.get().markChatAsRead(chatId, myUid);

        // ✅ Block check — 1-on-1 only
        if (!isGroup && otherUid != null) {
            checkBlockStatus();
            startBlockListener();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (chatId != null && myUid != null) {
            startListeningToMessages();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (messageListener != null) {
            messageListener.remove();
            messageListener = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ChatNotificationService.activeChatId = chatId;
        if (chatId != null && myUid != null) {
            FirestoreHelper.get().markChatAsRead(chatId, myUid);
        }
        if (chatId != null) {
            fetchFreshChatData();
        }
        // ✅ Re-check block on resume in case it changed
        if (!isGroup && otherUid != null) {
            checkBlockStatus();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        ChatNotificationService.activeChatId = null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (messageListener  != null) messageListener.remove();
        if (presenceListener != null) presenceListener.remove();
        if (blockListener    != null) blockListener.remove();
        ChatNotificationService.activeChatId = null;
        App.setOnlineStatus(false);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CHAT_PROFILE
                && resultCode == RESULT_OK && data != null) {
            String updatedName = data.getStringExtra("updatedChatName");
            if (updatedName != null) {
                chatName = updatedName;
                tvName.setText(updatedName);
            }
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
        onlineDot               = findViewById(R.id.onlineDot);
        tvProgramId             = findViewById(R.id.tvProgramId);
        // ✅ Block UI
        inputBar                = findViewById(R.id.inputBar);
        tvBlockedBanner         = findViewById(R.id.tvBlockedBanner);
    }

    // ════════════════════════════════════════════════════════
    //  REPLY BAR
    // ════════════════════════════════════════════════════════

    private void setupReplyBar() {
        replyBar       = findViewById(R.id.replyBar);
        tvReplyName    = findViewById(R.id.tvReplyName);
        tvReplyPreview = findViewById(R.id.tvReplyPreview);
        btnCloseReply  = findViewById(R.id.btnCloseReply);
        if (btnCloseReply != null)
            btnCloseReply.setOnClickListener(v -> cancelReply());
    }

    private void startReply(MessageModel msg) {
        replyingTo = msg;
        if (replyBar != null) {
            replyBar.setVisibility(View.VISIBLE);
            if (tvReplyName != null)
                tvReplyName.setText(
                        msg.getSenderId().equals(myUid) ? "You" : chatName);
            if (tvReplyPreview != null)
                tvReplyPreview.setText(msg.getPreviewText());
            etTypingBox.requestFocus();
        }
    }

    private void cancelReply() {
        replyingTo = null;
        if (replyBar != null) replyBar.setVisibility(View.GONE);
    }

    // ════════════════════════════════════════════════════════
    //  BLOCK CHECK — declared ONCE
    // ════════════════════════════════════════════════════════

    private void checkBlockStatus() {
        if (otherUid == null) return;
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection(FirestoreHelper.COL_USERS)
                .document(myUid)
                .get()
                .addOnSuccessListener(myDoc -> {
                    List<String> myBlocked =
                            (List<String>) myDoc.get("blockedUsers");
                    boolean iBlockedThem = myBlocked != null
                            && myBlocked.contains(otherUid);

                    db.collection(FirestoreHelper.COL_USERS)
                            .document(otherUid)
                            .get()
                            .addOnSuccessListener(theirDoc -> {
                                List<String> theirBlocked =
                                        (List<String>) theirDoc.get("blockedUsers");
                                boolean theyBlockedMe = theirBlocked != null
                                        && theirBlocked.contains(myUid);

                                runOnUiThread(() ->
                                        updateBlockUI(iBlockedThem, theyBlockedMe));
                            });
                });
    }

    // ✅ declared ONCE
    private void updateBlockUI(boolean iBlockedThem, boolean theyBlockedMe) {
        if (iBlockedThem) {
            if (inputBar != null)
                inputBar.setVisibility(View.GONE);
            if (tvBlockedBanner != null) {
                tvBlockedBanner.setVisibility(View.VISIBLE);
                tvBlockedBanner.setText(
                        "You blocked " + chatName + ". Unblock to send messages.");
            }
        } else if (theyBlockedMe) {
            if (inputBar != null)
                inputBar.setVisibility(View.GONE);
            if (tvBlockedBanner != null) {
                tvBlockedBanner.setVisibility(View.VISIBLE);
                String name = chatName != null ? chatName : "This user";
                tvBlockedBanner.setText(name + " blocked you.");
            }
        } else {
            if (inputBar != null)
                inputBar.setVisibility(View.VISIBLE);
            if (tvBlockedBanner != null)
                tvBlockedBanner.setVisibility(View.GONE);
        }
    }

    // ✅ declared ONCE
    private void startBlockListener() {
        if (isGroup || otherUid == null) return;
        blockListener = FirebaseFirestore.getInstance()
                .collection(FirestoreHelper.COL_USERS)
                .document(myUid)
                .addSnapshotListener((doc, error) -> {
                    if (error != null || doc == null) return;
                    checkBlockStatus();
                });
    }

    // ════════════════════════════════════════════════════════
    //  RECYCLERVIEW + ADAPTER
    // ════════════════════════════════════════════════════════

    private void replaceScrollViewWithRecyclerView() {
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

        messageAdapter.setOnImageClickListener(
                (message, imageUrl) -> openImageViewer(message, imageUrl));

        messageAdapter.setOnMessageLongClickListener((message, anchor) -> {
            selectedMessage = message;
            showTextOptionsDialog(message);
        });

        messageAdapter.setOnImageLongClickListener((message, anchor, isMine) -> {
            selectedMessage = message;
            showMediaOptionsDialog(message, isMine);
        });

        if (isGroup) {
            messageAdapter.setGroupChat(true);
            loadGroupMemberPhotos();
        }
    }

    private void loadGroupMemberPhotos() {
        FirebaseFirestore.getInstance()
                .collection(FirestoreHelper.COL_CHATS)
                .document(chatId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    java.util.Map<String, Object> rawPhotos =
                            (java.util.Map<String, Object>) doc.get("participantPhotos");
                    if (rawPhotos != null) {
                        java.util.Map<String, String> photos = new java.util.HashMap<>();
                        for (java.util.Map.Entry<String, Object> entry
                                : rawPhotos.entrySet()) {
                            if (entry.getValue() != null)
                                photos.put(entry.getKey(),
                                        entry.getValue().toString());
                        }
                        messageAdapter.setMemberPhotos(photos);
                        messageAdapter.notifyDataSetChanged();
                    }
                });
    }

    // ════════════════════════════════════════════════════════
    //  LONG PRESS: TEXT OPTIONS
    // ════════════════════════════════════════════════════════

    private void showTextOptionsDialog(MessageModel msg) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        View v = LayoutInflater.from(this)
                .inflate(R.layout.dialog_message_options, null);
        dialog.setContentView(v);
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(
                            android.graphics.Color.TRANSPARENT));

        v.findViewById(R.id.optionReply).setOnClickListener(x -> {
            dialog.dismiss();
            startReply(msg);
        });

        View optionCopy = v.findViewById(R.id.optionCopy);
        if (msg.isTextMessage()) {
            optionCopy.setVisibility(View.VISIBLE);
            optionCopy.setOnClickListener(x -> {
                dialog.dismiss();
                ClipboardManager cb = (ClipboardManager)
                        getSystemService(Context.CLIPBOARD_SERVICE);
                cb.setPrimaryClip(
                        ClipData.newPlainText("message", msg.getText()));
                Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show();
            });
        } else {
            optionCopy.setVisibility(View.GONE);
        }

        v.findViewById(R.id.optionForward).setOnClickListener(x -> {
            dialog.dismiss();
            openForwardScreen(msg);
        });

        View optionDelete = v.findViewById(R.id.optionDelete);
        if (msg.getSenderId().equals(myUid)) {
            optionDelete.setVisibility(View.VISIBLE);
            optionDelete.setOnClickListener(x -> {
                dialog.dismiss();
                confirmDelete(msg);
            });
        } else {
            optionDelete.setVisibility(View.GONE);
        }

        View optionUndo = v.findViewById(R.id.optionUndo);
        if (optionUndo != null) {
            boolean isUndoable = msg.getSenderId().equals(myUid)
                    && msg.getMessageId() != null
                    && msg.getMessageId().equals(lastSentMessageId);
            optionUndo.setVisibility(isUndoable ? View.VISIBLE : View.GONE);
            if (isUndoable) {
                optionUndo.setOnClickListener(x -> {
                    dialog.dismiss();
                    FirestoreHelper.get().deleteMessagePermanently(
                            chatId, lastSentMessageId,
                            () -> runOnUiThread(() -> {
                                lastSentMessageId = null;
                                Toast.makeText(this, "Message unsent.",
                                        Toast.LENGTH_SHORT).show();
                            }),
                            err -> runOnUiThread(() ->
                                    Toast.makeText(this, "Could not undo.",
                                            Toast.LENGTH_SHORT).show()));
                });
            }
        }

        dialog.show();
    }

    // ════════════════════════════════════════════════════════
    //  LONG PRESS: MEDIA OPTIONS
    // ════════════════════════════════════════════════════════

    private void showMediaOptionsDialog(MessageModel msg, boolean isMine) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        View v = LayoutInflater.from(this)
                .inflate(R.layout.dialog_image_options, null);
        dialog.setContentView(v);
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(
                            android.graphics.Color.TRANSPARENT));

        v.findViewById(R.id.optionReply).setOnClickListener(x -> {
            dialog.dismiss();
            startReply(msg);
        });

        v.findViewById(R.id.optionCopy).setOnClickListener(x -> {
            dialog.dismiss();
            String url = msg.isImageMessage() ? msg.getImageUrl() : msg.getFileUrl();
            if (url != null) {
                ClipboardManager cb = (ClipboardManager)
                        getSystemService(Context.CLIPBOARD_SERVICE);
                cb.setPrimaryClip(ClipData.newPlainText("link", url));
                Toast.makeText(this, "Link copied!", Toast.LENGTH_SHORT).show();
            }
        });

        v.findViewById(R.id.optionForward).setOnClickListener(x -> {
            dialog.dismiss();
            openForwardScreen(msg);
        });

        View optionDelete = v.findViewById(R.id.optionDelete);
        if (optionDelete != null) {
            if (isMine) {
                optionDelete.setVisibility(View.VISIBLE);
                optionDelete.setOnClickListener(x -> {
                    dialog.dismiss();
                    confirmDelete(msg);
                });
            } else {
                optionDelete.setVisibility(View.GONE);
            }
        }

        dialog.show();
    }

    // ════════════════════════════════════════════════════════
    //  DELETE CONFIRM
    // ════════════════════════════════════════════════════════

    private void confirmDelete(MessageModel msg) {
        new IosDialog(this)
                .setTitle("Delete Message")
                .setMessage("Delete this message for you?")
                .setOkText("Delete")
                .setDestructive()
                .onOk(() -> {
                    FirestoreHelper.get().deleteMessageFor(
                            chatId, msg.getMessageId(), myUid);
                    Toast.makeText(this, "Message deleted.",
                            Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    // ════════════════════════════════════════════════════════
    //  FORWARD
    // ════════════════════════════════════════════════════════

    private void openForwardScreen(MessageModel msg) {
        Intent intent = new Intent(this, ForwardActivity.class);
        intent.putExtra("msgType",     msg.getType());
        intent.putExtra("msgText",     msg.getText());
        intent.putExtra("msgImageUrl", msg.getImageUrl());
        intent.putExtra("msgFileUrl",  msg.getFileUrl());
        intent.putExtra("msgFileName", msg.getFileName());
        intent.putExtra("msgFileSize", msg.getFileSize());
        intent.putExtra("msgFileType", msg.getFileType());
        startActivity(intent);
    }

    // ════════════════════════════════════════════════════════
    //  HEADER
    // ════════════════════════════════════════════════════════

    private void setupHeader() {
        tvName.setText(chatName != null ? chatName : "Chat");
        btnBack.setOnClickListener(v -> finish());
        fetchFreshChatData();

        imgProfile.setOnClickListener(v -> {
            Intent intent = new Intent(this, ChatProfileActivity.class);
            intent.putExtra("chatId",    chatId);
            intent.putExtra("chatName",  chatName);
            intent.putExtra("chatPhoto", chatPhoto);
            intent.putExtra("isGroup",   isGroup);
            if (!isGroup && otherUid != null)
                intent.putExtra("otherUid", otherUid);
            startActivity(intent);
        });
    }

    private void fetchFreshChatData() {
        if (isGroup) {
            FirebaseFirestore.getInstance()
                    .collection(FirestoreHelper.COL_CHATS)
                    .document(chatId)
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (!doc.exists()) return;

                        String freshName = doc.getString("groupName");
                        if (freshName != null && !freshName.isEmpty()) {
                            chatName = freshName;
                            tvName.setText(freshName);
                        }

                        String freshPhoto = doc.getString("groupPhoto");
                        if (freshPhoto != null && !freshPhoto.isEmpty()) {
                            chatPhoto = freshPhoto;
                            Glide.with(this)
                                    .load(freshPhoto)
                                    .transform(new CircleCrop())
                                    .placeholder(R.drawable.circle_grey_bg)
                                    .into(imgProfile);
                        } else {
                            imgProfile.setImageResource(R.drawable.circle_grey_bg);
                        }

                        java.util.List<String> p =
                                (java.util.List<String>) doc.get("participants");
                        if (p != null && tvProgramId != null) {
                            tvProgramId.setText(p.size() + " members");
                            tvProgramId.setVisibility(View.VISIBLE);
                        }

                        loadGroupMemberPhotos();
                        tvStatus.setText("Group Chat");
                        tvStatus.setTextColor(0xFF888888);
                        if (onlineDot != null) onlineDot.setVisibility(View.GONE);
                    });
        } else {
            if (otherUid == null)
                otherUid = getIntent().getStringExtra("otherUid");

            if (otherUid != null) {
                FirestoreHelper.get().getUser(otherUid,
                        new FirestoreHelper.OnUserFetched() {
                            @Override
                            public void onSuccess(UserModel user) {
                                String freshPhoto = user.getPhotoUrl();
                                if (freshPhoto != null && !freshPhoto.isEmpty()) {
                                    chatPhoto = freshPhoto;
                                    Glide.with(ChatActivity.this)
                                            .load(freshPhoto)
                                            .transform(new CircleCrop())
                                            .placeholder(R.drawable.circle_grey_bg)
                                            .into(imgProfile);
                                } else {
                                    imgProfile.setImageResource(
                                            R.drawable.circle_grey_bg);
                                }

                                if (tvProgramId != null) {
                                    String info = "";
                                    if (user.getCourse() != null
                                            && !user.getCourse().isEmpty())
                                        info += user.getCourse();
                                    if (user.getStudentId() != null
                                            && !user.getStudentId().isEmpty())
                                        info += (info.isEmpty() ? "" : " · ")
                                                + user.getStudentId();
                                    if (!info.isEmpty()) {
                                        tvProgramId.setText(info);
                                        tvProgramId.setVisibility(View.VISIBLE);
                                    }
                                }
                            }
                            @Override public void onFailure(String error) {}
                        });

                startPresenceListener(otherUid);
            }
        }
    }

    private void startPresenceListener(String uid) {
        if (presenceListener != null) presenceListener.remove();
        presenceListener = PresenceHelper.listenToPresence(uid,
                (isOnline, statusText) -> runOnUiThread(() -> {
                    tvStatus.setText(statusText);
                    if (onlineDot != null)
                        onlineDot.setVisibility(isOnline ? View.VISIBLE : View.GONE);
                    tvStatus.setTextColor(isOnline ? 0xFF4CAF50 : 0xFF888888);
                }));
    }

    // ════════════════════════════════════════════════════════
    //  INPUT BAR
    // ════════════════════════════════════════════════════════

    private void setupInputBar() {
        btnSendMessage.setOnClickListener(v -> sendTextMessage());
        btnSendFile.setOnClickListener(v -> showAttachmentDialog());
    }

    // ════════════════════════════════════════════════════════
    //  REAL TIME MESSAGES
    // ════════════════════════════════════════════════════════

    private void startListeningToMessages() {
        if (messageListener != null) {
            messageListener.remove();
            messageListener = null;
        }

        messageListener = FirestoreHelper.get()
                .listenToMessages(chatId)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;

                    List<MessageModel> msgs = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        MessageModel msg = doc.toObject(MessageModel.class);
                        if (msg != null) {
                            if (msg.getMessageId() == null
                                    || msg.getMessageId().isEmpty()) {
                                msg.setMessageId(doc.getId());
                            }
                            if (!msg.isDeletedFor(myUid)) {
                                msgs.add(msg);
                            }
                        }
                    }

                    messageAdapter.setMessages(msgs);

                    if (msgs.isEmpty()) {
                        emptyConversationLayout.setVisibility(View.VISIBLE);
                        recyclerMessages.setVisibility(View.GONE);
                    } else {
                        emptyConversationLayout.setVisibility(View.GONE);
                        recyclerMessages.setVisibility(View.VISIBLE);

                        // ✅ post() defers scroll until after RecyclerView layout pass
                        recyclerMessages.post(() -> {
                            if (highlightMessageId != null) {
                                int pos = messageAdapter
                                        .getPositionByMessageId(highlightMessageId);
                                if (pos != -1) {
                                    recyclerMessages.scrollToPosition(pos);
                                    messageAdapter.setHighlightedMessage(
                                            highlightMessageId);
                                    highlightMessageId = null;
                                }
                            } else {
                                recyclerMessages.scrollToPosition(msgs.size() - 1);
                            }
                        });
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

        final MessageModel replySnapshot = replyingTo;
        cancelReply();

        String replySenderLabel = (replySnapshot != null
                && replySnapshot.getSenderId().equals(myUid))
                ? "You" : chatName;

        getOtherParticipants(otherUids -> {
            if (otherUids.isEmpty()) {
                android.util.Log.w("ChatActivity",
                        "otherParticipants is empty! chatId=" + chatId);
            }
            FirestoreHelper.get().sendTextMessage(
                    chatId, myUid, text,
                    replySenderLabel, myUid,
                    replySnapshot, otherUids,
                    new FirestoreHelper.OnMessageSent() {
                        @Override public void onSent(String messageId) {
                            runOnUiThread(() -> lastSentMessageId = messageId);
                        }
                        @Override public void onFailure(String e) {
                            runOnUiThread(() ->
                                    Toast.makeText(ChatActivity.this,
                                            "Failed to send.",
                                            Toast.LENGTH_SHORT).show());
                        }
                    });
        });
    }

    // ════════════════════════════════════════════════════════
    //  SEND IMAGE
    // ════════════════════════════════════════════════════════

    private void uploadImageAndSend(Uri imageUri) {
        Toast.makeText(this, "Uploading image...", Toast.LENGTH_SHORT).show();
        final MessageModel replySnapshot = replyingTo;
        cancelReply();

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
                                        replySnapshot, chatName, myUid,
                                        new FirestoreHelper.OnMessageSent() {
                                            @Override public void onSent(String id) {
                                                runOnUiThread(() ->
                                                        lastSentMessageId = id);
                                            }
                                            @Override public void onFailure(String e) {
                                                runOnUiThread(() ->
                                                        Toast.makeText(ChatActivity.this,
                                                                "Failed to send image.",
                                                                Toast.LENGTH_SHORT).show());
                                            }
                                        }));
                    }

                    @Override
                    public void onError(String r, ErrorInfo e) {
                        runOnUiThread(() ->
                                Toast.makeText(ChatActivity.this,
                                        "Upload failed.",
                                        Toast.LENGTH_SHORT).show());
                    }
                })
                .dispatch();
    }

    // ════════════════════════════════════════════════════════
    //  SEND VIDEO
    // ════════════════════════════════════════════════════════

    private void handleVideoPicked(Uri uri) {
        String videoDuration = getVideoDuration(uri);
        Toast.makeText(this, "Uploading video...", Toast.LENGTH_SHORT).show();

        MediaManager.get()
                .upload(uri)
                .option("resource_type", "video")
                .option("upload_preset", "ucchat_profiles")
                .option("public_id", "videos/" + chatId + "/"
                        + System.currentTimeMillis())
                .callback(new UploadCallback() {
                    @Override public void onStart(String r) {}
                    @Override public void onReschedule(String r, ErrorInfo e) {}
                    @Override public void onProgress(String requestId,
                                                     long bytes, long totalBytes) {
                        int percent = (int) ((bytes * 100) / totalBytes);
                        runOnUiThread(() ->
                                Toast.makeText(ChatActivity.this,
                                        "Uploading... " + percent + "%",
                                        Toast.LENGTH_SHORT).show());
                    }

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        String videoUrl = (String) resultData.get("secure_url");
                        String thumbnailUrl = videoUrl
                                .replace("/upload/", "/upload/w_400,h_300,c_fill/")
                                .replace(".mp4", ".jpg")
                                .replace(".mov", ".jpg");

                        getOtherParticipants(otherUids ->
                                FirestoreHelper.get().sendVideoMessage(
                                        chatId, myUid, videoUrl,
                                        thumbnailUrl, videoDuration, otherUids,
                                        new FirestoreHelper.OnMessageSent() {
                                            @Override public void onSent(String id) {
                                                runOnUiThread(() ->
                                                        Toast.makeText(ChatActivity.this,
                                                                "Video sent! ✅",
                                                                Toast.LENGTH_SHORT).show());
                                            }
                                            @Override public void onFailure(String e) {
                                                runOnUiThread(() ->
                                                        Toast.makeText(ChatActivity.this,
                                                                "Failed to send video.",
                                                                Toast.LENGTH_SHORT).show());
                                            }
                                        }));
                    }

                    @Override public void onError(String r, ErrorInfo e) {
                        runOnUiThread(() ->
                                Toast.makeText(ChatActivity.this,
                                        "Upload failed.",
                                        Toast.LENGTH_SHORT).show());
                    }
                })
                .dispatch();
    }

    // ════════════════════════════════════════════════════════
    //  SEND FILE
    // ════════════════════════════════════════════════════════

    private void handleFilePicked(Uri uri) {
        String fileName = getFileName(uri);
        String fileSize = getFileSize(uri);
        String fileType = getFileExtension(fileName);
        Toast.makeText(this, "Uploading " + fileName + "...",
                Toast.LENGTH_SHORT).show();

        MediaManager.get()
                .upload(uri)
                .option("resource_type", "raw")
                .option("upload_preset", "ucchat_profiles")
                .option("public_id", "files/" + chatId + "/"
                        + System.currentTimeMillis() + "_" + fileName)
                .callback(new UploadCallback() {
                    @Override public void onStart(String r) {}
                    @Override public void onProgress(String r, long b, long t) {}
                    @Override public void onReschedule(String r, ErrorInfo e) {}

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        String fileUrl = (String) resultData.get("secure_url");
                        getOtherParticipants(otherUids ->
                                FirestoreHelper.get().sendFileMessage(
                                        chatId, myUid, fileUrl,
                                        fileName, fileSize, fileType, otherUids,
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

                    @Override public void onError(String r, ErrorInfo e) {
                        runOnUiThread(() ->
                                Toast.makeText(ChatActivity.this,
                                        "Upload failed: " + e.getDescription(),
                                        Toast.LENGTH_SHORT).show());
                    }
                })
                .dispatch();
    }

    // ════════════════════════════════════════════════════════
    //  ATTACHMENT DIALOG
    // ════════════════════════════════════════════════════════

    private void showAttachmentDialog() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_attachment, null);
        dialog.setContentView(dialogView);
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(
                            android.graphics.Color.TRANSPARENT));

        dialogView.findViewById(R.id.btnAttachImage).setOnClickListener(v -> {
            dialog.dismiss();
            imagePicker.launch("image/*");
        });
        dialogView.findViewById(R.id.btnAttachVideo).setOnClickListener(v -> {
            dialog.dismiss();
            videoPicker.launch("video/*");
        });
        dialogView.findViewById(R.id.btnAttachFile).setOnClickListener(v -> {
            dialog.dismiss();
            filePicker.launch(new String[]{
                    "application/pdf", "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-powerpoint",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "text/plain", "application/zip"
            });
        });
        dialogView.findViewById(R.id.btnAttachCancel)
                .setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    // ════════════════════════════════════════════════════════
    //  IMAGE VIEWER
    // ════════════════════════════════════════════════════════

    private void setupImageViewer() {
        viewImageLayout = findViewById(R.id.viewImageLayout);
        imgViewFull     = findViewById(R.id.imgViewFull);
        imgViewBackBtn  = findViewById(R.id.imgViewBackBtn);

        if (imgViewBackBtn != null)
            imgViewBackBtn.setOnClickListener(v -> closeImageViewer());

        View btnSave = findViewById(R.id.btnSaveDoc);
        if (btnSave != null)
            btnSave.setOnClickListener(v ->
                    saveImageToGallery(currentViewedImageUrl));

        View btnForwardImg = findViewById(R.id.btnForwardViewImage);
        if (btnForwardImg != null) {
            btnForwardImg.setOnClickListener(v -> {
                if (selectedMessage != null) {
                    openForwardScreen(selectedMessage);
                } else if (currentViewedImageUrl != null) {
                    MessageModel temp = new MessageModel();
                    temp.setType(MessageModel.TYPE_IMAGE);
                    temp.setImageUrl(currentViewedImageUrl);
                    openForwardScreen(temp);
                }
            });
        }
    }

    public void openImageViewer(MessageModel msg, String imageUrl) {
        if (viewImageLayout == null || imgViewFull == null) return;
        currentViewedImageUrl = imageUrl;
        selectedMessage       = msg;
        Glide.with(this).load(imageUrl)
                .placeholder(R.drawable.circle_grey_bg).into(imgViewFull);
        viewImageLayout.setVisibility(View.VISIBLE);
    }

    private void closeImageViewer() {
        if (viewImageLayout != null) viewImageLayout.setVisibility(View.GONE);
        currentViewedImageUrl = null;
    }

    // ════════════════════════════════════════════════════════
    //  SAVE IMAGE
    // ════════════════════════════════════════════════════════

    private void saveImageToGallery(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            Toast.makeText(this, "No image to save.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQ_WRITE_STORAGE);
                return;
            }
        }

        Toast.makeText(this, "Saving image...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                URL url = new URL(imageUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.connect();
                InputStream input = conn.getInputStream();
                String fileName = "UccChat_" + System.currentTimeMillis() + ".jpg";

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    android.content.ContentValues values =
                            new android.content.ContentValues();
                    values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,
                            fileName);
                    values.put(android.provider.MediaStore.Images.Media.MIME_TYPE,
                            "image/jpeg");
                    values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                            Environment.DIRECTORY_PICTURES + "/UccChat");
                    Uri uri = getContentResolver().insert(
                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            values);
                    if (uri != null) {
                        try (java.io.OutputStream os =
                                     getContentResolver().openOutputStream(uri)) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = input.read(buffer)) != -1)
                                if (os != null) os.write(buffer, 0, bytesRead);
                        }
                    }
                } else {
                    File dir = new File(
                            Environment.getExternalStoragePublicDirectory(
                                    Environment.DIRECTORY_PICTURES), "UccChat");
                    if (!dir.exists()) dir.mkdirs();
                    File file = new File(dir, fileName);
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = input.read(buffer)) != -1)
                            fos.write(buffer, 0, bytesRead);
                    }
                    sendBroadcast(new Intent(
                            Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                            Uri.fromFile(new File(
                                    Environment.getExternalStoragePublicDirectory(
                                            Environment.DIRECTORY_PICTURES),
                                    "UccChat/" + fileName))));
                }

                input.close();
                runOnUiThread(() ->
                        Toast.makeText(this, "Image saved to gallery! 📷",
                                Toast.LENGTH_SHORT).show());

            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Failed to save image.",
                                Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_WRITE_STORAGE
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            saveImageToGallery(currentViewedImageUrl);
        }
    }

    // ════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════

    private void getOtherParticipants(OnParticipantsFetched callback) {
        FirebaseFirestore.getInstance()
                .collection(FirestoreHelper.COL_CHATS)
                .document(chatId)
                .get()
                .addOnSuccessListener(doc -> {
                    List<String> others = new ArrayList<>();

                    if (doc.exists()) {
                        // ✅ Normal case — doc is in cache, get participants
                        List<String> participants =
                                (List<String>) doc.get("participants");
                        if (participants != null) {
                            for (String uid : participants) {
                                if (!uid.equals(myUid)) others.add(uid);
                            }
                        }
                    }

                    // ✅ Fallback — doc not in cache yet on first message
                    // of a brand new chat, use otherUid from Intent instead
                    if (others.isEmpty() && otherUid != null
                            && !otherUid.isEmpty()) {
                        others.add(otherUid);
                    }

                    // ✅ ALWAYS fire the callback — never silently drop
                    callback.onFetched(others);
                })
                .addOnFailureListener(e -> {
                    // ✅ Network error fallback
                    List<String> others = new ArrayList<>();
                    if (otherUid != null && !otherUid.isEmpty()) {
                        others.add(otherUid);
                    }
                    callback.onFetched(others);
                });
    }

    interface OnParticipantsFetched {
        void onFetched(List<String> otherUids);
    }

    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (android.database.Cursor cursor =
                         getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(
                            android.provider.OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) result = cursor.getString(idx);
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result != null ? result.lastIndexOf('/') : -1;
            if (cut != -1) result = result.substring(cut + 1);
        }
        return result != null ? result : "file";
    }

    private String getFileSize(Uri uri) {
        try (android.database.Cursor cursor =
                     getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(
                        android.provider.OpenableColumns.SIZE);
                if (idx >= 0) {
                    long bytes = cursor.getLong(idx);
                    if (bytes < 1024) return bytes + " B";
                    else if (bytes < 1024 * 1024)
                        return String.format(java.util.Locale.getDefault(),
                                "%.1f KB", bytes / 1024.0);
                    else
                        return String.format(java.util.Locale.getDefault(),
                                "%.1f MB", bytes / (1024.0 * 1024));
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return "";
    }

    private String getFileExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "";
    }

    private String getVideoDuration(Uri uri) {
        try {
            android.media.MediaMetadataRetriever retriever =
                    new android.media.MediaMetadataRetriever();
            retriever.setDataSource(this, uri);
            String durationMs = retriever.extractMetadata(
                    android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
            retriever.release();
            if (durationMs != null) {
                long totalSecs = Long.parseLong(durationMs) / 1000;
                long mins = totalSecs / 60;
                long secs = totalSecs % 60;
                return String.format(java.util.Locale.getDefault(),
                        "%d:%02d", mins, secs);
            }
        } catch (Exception e) { e.printStackTrace(); }
        return "";
    }
}