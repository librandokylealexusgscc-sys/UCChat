package com.example.uccchat;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ForwardActivity extends AppCompatActivity {

    private String myUid;

    // Message being forwarded
    private String msgType, msgText, msgImageUrl,
            msgFileUrl, msgFileName, msgFileSize, msgFileType;

    // Views
    private EditText     etWriteMessage;
    private EditText     etSearch;
    private RecyclerView recyclerChats;
    private LinearLayout undoBanner;
    private TextView     tvUndoText;

    // Data
    private final List<ChatModel> allChats      = new ArrayList<>();
    private final List<ChatModel> filteredChats = new ArrayList<>();

    // Undo state
    private String lastForwardedMessageId = null;
    private String lastForwardedChatId    = null;
    private final Handler  undoHandler        = new Handler(Looper.getMainLooper());
    private       Runnable undoDismissRunnable;

    // ════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.forwardmessage);

        myUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (myUid == null) { finish(); return; }

        // Intent extras
        msgType     = getIntent().getStringExtra("msgType");
        msgText     = getIntent().getStringExtra("msgText");
        msgImageUrl = getIntent().getStringExtra("msgImageUrl");
        msgFileUrl  = getIntent().getStringExtra("msgFileUrl");
        msgFileName = getIntent().getStringExtra("msgFileName");
        msgFileSize = getIntent().getStringExtra("msgFileSize");
        msgFileType = getIntent().getStringExtra("msgFileType");

        // Bind views
        etWriteMessage = findViewById(R.id.ForwardMessage);
        etSearch       = findViewById(R.id.SearchStudent);
        recyclerChats  = findViewById(R.id.recyclerForwardChats);
        undoBanner     = findViewById(R.id.undoBanner);
        tvUndoText     = findViewById(R.id.tvUndoText);

        // Back
        findViewById(R.id.backBtn).setOnClickListener(v -> finish());

        // Undo button
        TextView btnUndo = findViewById(R.id.btnUndo);
        if (btnUndo != null) btnUndo.setOnClickListener(v -> handleUndo());

        // RecyclerView
        recyclerChats.setLayoutManager(new LinearLayoutManager(this));
        recyclerChats.setAdapter(new ForwardChatAdapter());

        setupSearch();
        loadChats();
    }

    // ════════════════════════════════════════════════════════
    //  HELPER — resolve a display name from ChatModel
    //  Uses groupName for groups, or the other participant's
    //  name from participantNames for 1-on-1 chats.
    // ════════════════════════════════════════════════════════

    private String resolveChatName(ChatModel chat) {
        // Group chats → use groupName
        if (chat.isGroup()) {
            String gn = chat.getGroupName();
            return (gn != null && !gn.isEmpty()) ? gn : "Group";
        }

        // 1-on-1 → find the OTHER participant's name
        Map<String, String> names = chat.getParticipantNames();
        if (names != null) {
            for (Map.Entry<String, String> entry : names.entrySet()) {
                if (!entry.getKey().equals(myUid)) {
                    return entry.getValue() != null ? entry.getValue() : "Chat";
                }
            }
        }
        return "Chat";
    }

    private String resolveChatPhoto(ChatModel chat) {
        if (chat.isGroup()) {
            return chat.getGroupPhoto();
        }
        Map<String, String> photos = chat.getParticipantPhotos();
        if (photos != null) {
            for (Map.Entry<String, String> entry : photos.entrySet()) {
                if (!entry.getKey().equals(myUid)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    // ════════════════════════════════════════════════════════
    //  SEARCH
    // ════════════════════════════════════════════════════════

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                filterChats(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void filterChats(String query) {
        filteredChats.clear();
        if (query.isEmpty()) {
            filteredChats.addAll(allChats);
        } else {
            String lower = query.toLowerCase();
            for (ChatModel chat : allChats) {
                String name = resolveChatName(chat);
                if (name.toLowerCase().contains(lower)) {
                    filteredChats.add(chat);
                }
            }
        }
        recyclerChats.getAdapter().notifyDataSetChanged();
    }

    // ════════════════════════════════════════════════════════
    //  LOAD CHATS FROM FIRESTORE
    // ════════════════════════════════════════════════════════

    private void loadChats() {
        FirebaseFirestore.getInstance()
                .collection(FirestoreHelper.COL_CHATS)
                .whereArrayContains("participants", myUid)
                .get()
                .addOnSuccessListener(snapshots -> {
                    allChats.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        ChatModel chat = doc.toObject(ChatModel.class);
                        if (chat != null) {
                            chat.setChatId(doc.getId());
                            allChats.add(chat);
                        }
                    }
                    filteredChats.clear();
                    filteredChats.addAll(allChats);
                    recyclerChats.getAdapter().notifyDataSetChanged();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load chats.", Toast.LENGTH_SHORT).show());
    }

    // ════════════════════════════════════════════════════════
    //  FORWARD
    // ════════════════════════════════════════════════════════

    private void forwardTo(ChatModel chat) {
        String toChatId   = chat.getChatId();
        String displayName = resolveChatName(chat);
        String extraText  = etWriteMessage != null
                ? etWriteMessage.getText().toString().trim() : "";

        FirebaseFirestore.getInstance()
                .collection(FirestoreHelper.COL_CHATS)
                .document(toChatId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;

                    List<String> parts  = (List<String>) doc.get("participants");
                    List<String> others = new ArrayList<>();
                    if (parts != null) {
                        for (String uid : parts) {
                            if (!uid.equals(myUid)) others.add(uid);
                        }
                    }

                    // Optional extra text typed by the user
                    if (!extraText.isEmpty()) {
                        FirestoreHelper.get().sendTextMessage(
                                toChatId, myUid, extraText,
                                displayName, myUid,   // ← chatName + myUid for reply metadata
                                null,                  // ← no reply context
                                others,
                                new FirestoreHelper.OnMessageSent() {
                                    @Override public void onSent(String id) {}
                                    @Override public void onFailure(String e) {}
                                });
                    }

                    sendForwardedMessage(toChatId, others, displayName);
                });
    }

    private void sendForwardedMessage(String toChatId,
                                      List<String> others,
                                      String displayName) {
        switch (msgType != null ? msgType : "") {

            case MessageModel.TYPE_TEXT:
                FirestoreHelper.get().sendTextMessage(
                        toChatId, myUid,
                        msgText != null ? msgText : "",
                        displayName, myUid,   // ← chatName + myUid
                        null,                  // ← no reply context for forwarded msg
                        others,
                        new FirestoreHelper.OnMessageSent() {
                            @Override public void onSent(String id) {
                                lastForwardedMessageId = id;
                                lastForwardedChatId    = toChatId;
                                runOnUiThread(() -> showUndoBanner(displayName));
                            }
                            @Override public void onFailure(String e) { failed(); }
                        });
                break;

            case MessageModel.TYPE_IMAGE:
                FirestoreHelper.get().sendImageMessage(
                        toChatId, myUid,
                        msgImageUrl,
                        others,
                        null,          // ← no reply context
                        displayName,   // ← chatName
                        myUid,         // ← myUid
                        new FirestoreHelper.OnMessageSent() {
                            @Override public void onSent(String id) {
                                lastForwardedMessageId = id;
                                lastForwardedChatId    = toChatId;
                                runOnUiThread(() -> showUndoBanner(displayName));
                            }
                            @Override public void onFailure(String e) { failed(); }
                        });
                break;

            case MessageModel.TYPE_FILE:
            case MessageModel.TYPE_VIDEO:
                FirestoreHelper.get().sendFileMessage(
                        toChatId, myUid,
                        msgFileUrl, msgFileName, msgFileSize, msgFileType,
                        others,
                        new FirestoreHelper.OnMessageSent() {
                            @Override public void onSent(String id) {
                                lastForwardedMessageId = id;
                                lastForwardedChatId    = toChatId;
                                runOnUiThread(() -> showUndoBanner(displayName));
                            }
                            @Override public void onFailure(String e) { failed(); }
                        });
                break;

            default:
                Toast.makeText(this, "Unknown message type.", Toast.LENGTH_SHORT).show();
        }
    }

    private void failed() {
        runOnUiThread(() ->
                Toast.makeText(this, "Failed to forward message.", Toast.LENGTH_SHORT).show());
    }

    // ════════════════════════════════════════════════════════
    //  UNDO BANNER
    // ════════════════════════════════════════════════════════

    private void showUndoBanner(String chatName) {
        if (undoBanner == null) return;
        if (tvUndoText != null) tvUndoText.setText("Forwarded to " + chatName);
        undoBanner.setVisibility(View.VISIBLE);

        if (undoDismissRunnable != null) undoHandler.removeCallbacks(undoDismissRunnable);
        undoDismissRunnable = this::hideUndoBanner;
        undoHandler.postDelayed(undoDismissRunnable, 4000);
    }

    private void hideUndoBanner() {
        if (undoBanner != null) undoBanner.setVisibility(View.GONE);
    }

    private void handleUndo() {
        if (lastForwardedMessageId != null && lastForwardedChatId != null) {
            String chatId = lastForwardedChatId;
            String msgId  = lastForwardedMessageId;
            lastForwardedMessageId = null;
            lastForwardedChatId    = null;

            FirestoreHelper.get().deleteMessagePermanently(
                    chatId, msgId,
                    () -> runOnUiThread(() -> {
                        Toast.makeText(this, "Forward undone.", Toast.LENGTH_SHORT).show();
                        hideUndoBanner();
                    }),
                    err -> runOnUiThread(() ->
                            Toast.makeText(this, "Could not undo.", Toast.LENGTH_SHORT).show())
            );
        } else {
            hideUndoBanner();
        }
    }

    // ════════════════════════════════════════════════════════
    //  INNER ADAPTER
    // ════════════════════════════════════════════════════════

    private class ForwardChatAdapter
            extends RecyclerView.Adapter<ForwardChatAdapter.VH> {

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(ForwardActivity.this)
                    .inflate(R.layout.item_chat, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            ChatModel chat        = filteredChats.get(position);
            String    displayName = resolveChatName(chat);
            String    photoUrl    = resolveChatPhoto(chat);

            holder.tvName.setText(displayName);
            holder.tvPreview.setText("Tap to forward here");

            if (photoUrl != null && !photoUrl.isEmpty()) {
                Glide.with(ForwardActivity.this)
                        .load(photoUrl)
                        .transform(new CircleCrop())
                        .placeholder(R.drawable.circle_grey_bg)
                        .into(holder.imgPhoto);
            } else {
                holder.imgPhoto.setImageResource(R.drawable.circle_grey_bg);
            }

            holder.itemView.setOnClickListener(v ->
                    new IosDialog(ForwardActivity.this)
                            .setTitle("Forward Message")
                            .setMessage("Forward to " + displayName + "?")
                            .setOkText("Forward")
                            .onOk(() -> forwardTo(chat))
                            .show());
        }

        @Override public int getItemCount() { return filteredChats.size(); }

        class VH extends RecyclerView.ViewHolder {
            ImageView imgPhoto;
            TextView  tvName, tvPreview;
            VH(View v) {
                super(v);
                imgPhoto  = v.findViewById(R.id.imgAvatar);      // ← was imgChatPhoto
                tvName    = v.findViewById(R.id.tvName);          // ← was tvChatName
                tvPreview = v.findViewById(R.id.tvLastMessage);   // ← this one was correct
            }

        }
    }
}