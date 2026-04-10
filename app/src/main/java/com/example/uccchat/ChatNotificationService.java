package com.example.uccchat;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ChatNotificationService
 * ────────────────────────
 * A started (non-foreground) service that keeps a Firestore snapshot
 * listener alive so the user gets in-app / heads-up notifications
 * whenever a new message arrives in any of their chats, regardless of
 * which screen they are on.
 *
 * Lifecycle:
 *   • Start in ChatHomeActivity.onStart()
 *   • Stop in ChatHomeActivity.onDestroy()  (or let Android manage it)
 *
 * This service is intentionally lightweight — it does NOT run as a
 * foreground service (no persistent notification bar icon) because it
 * only needs to run while the app process is alive.
 */
public class ChatNotificationService extends Service {

    private static final String TAG = "ChatNotifService";

    private String myUid;

    // One listener per chat — keyed by chatId
    private final Map<String, ListenerRegistration> messageListeners = new HashMap<>();
    // The top-level listener on the chats collection
    private ListenerRegistration chatListListener;

    // Track which chat is currently open so we suppress its notifications
    public static String activeChatId = null;

    // ── Lifecycle ──────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        myUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        NotificationHelper.createChannel(this);

        if (myUid != null) {
            startListening();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // START_STICKY: Android will restart the service if killed
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        removeAllListeners();
    }

    // ── Listening Logic ────────────────────────────────────────

    private void startListening() {
        // Listen to all chats the user is part of
        chatListListener = FirebaseFirestore.getInstance()
                .collection(FirestoreHelper.COL_CHATS)
                .whereArrayContains("participants", myUid)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;

                    // Collect current chat IDs
                    List<String> currentChatIds = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        currentChatIds.add(doc.getId());
                    }

                    // Register message listener for any newly discovered chat
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        String chatId = doc.getId();
                        if (!messageListeners.containsKey(chatId)) {
                            ChatModel chat = doc.toObject(ChatModel.class);
                            if (chat != null) {
                                chat.setChatId(chatId);
                                registerMessageListener(chat);
                            }
                        }
                    }

                    // Remove listeners for chats that no longer include the user
                    List<String> toRemove = new ArrayList<>();
                    for (String chatId : messageListeners.keySet()) {
                        if (!currentChatIds.contains(chatId)) {
                            toRemove.add(chatId);
                        }
                    }
                    for (String chatId : toRemove) {
                        ListenerRegistration reg = messageListeners.remove(chatId);
                        if (reg != null) reg.remove();
                    }
                });
    }

    /**
     * Attaches a real-time listener to a chat's messages subcollection.
     * Only notifies on ADDED documents whose timestamp is recent (i.e. new)
     * and whose sender is NOT the current user.
     */
    private void registerMessageListener(ChatModel chat) {
        String chatId = chat.getChatId();

        ListenerRegistration reg = FirebaseFirestore.getInstance()
                .collection(FirestoreHelper.COL_CHATS)
                .document(chatId)
                .collection(FirestoreHelper.COL_MESSAGES)
                // Only fetch the last message first so we don't fire on initial load
                .orderBy("timestamp",
                        com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;

                    for (DocumentChange change : snapshots.getDocumentChanges()) {

                        // Only care about brand-new messages, not initial fetch
                        if (change.getType() != DocumentChange.Type.ADDED) continue;

                        // Skip if this is the very first snapshot load
                        // (getDocumentChanges on the first call marks everything as ADDED)
                        // We guard against this by checking metadata.isFromCache / hasPendingWrites.
                        // A simpler guard: only fire if the snapshot is NOT from cache.
                        if (snapshots.getMetadata().isFromCache()) continue;

                        DocumentSnapshot msgDoc = change.getDocument();
                        MessageModel msg = msgDoc.toObject(MessageModel.class);
                        if (msg == null) continue;

                        // Don't notify for own messages
                        if (myUid.equals(msg.getSenderId())) continue;

                        // Don't notify if user is currently in this chat
                        if (chatId.equals(activeChatId)) continue;

                        // Don't notify for deleted messages
                        if (msg.isDeletedFor(myUid)) continue;

                        // Build notification text
                        String senderName = getSenderName(chat, msg.getSenderId());
                        String preview    = buildPreview(msg);
                        String title      = chat.isGroup()
                                ? chat.getGroupName() + " • " + senderName
                                : senderName;

                        NotificationHelper.showMessageNotification(
                                ChatNotificationService.this,
                                chatId,
                                title,
                                chat.getDisplayPhoto(myUid),
                                preview,
                                chat.isGroup(),
                                msg.getSenderId()
                        );
                    }
                });

        messageListeners.put(chatId, reg);
    }

    // ── Helpers ────────────────────────────────────────────────

    private String getSenderName(ChatModel chat, String senderId) {
        if (chat.getParticipantNames() != null) {
            String name = chat.getParticipantNames().get(senderId);
            if (name != null && !name.isEmpty()) return name;
        }
        return "Someone";
    }

    private String buildPreview(MessageModel msg) {
        if (msg == null) return "New message";
        String type = msg.getType();
        if (type == null || MessageModel.TYPE_TEXT.equals(type)) {
            String text = msg.getText();
            return (text != null && !text.isEmpty()) ? text : "New message";
        }
        switch (type) {
            case MessageModel.TYPE_IMAGE: return "📷 Photo";
            case MessageModel.TYPE_VIDEO: return "🎥 Video";
            case MessageModel.TYPE_FILE:  return "📎 " + (msg.getFileName() != null
                    ? msg.getFileName() : "File");
            default: return "New message";
        }
    }

    private void removeAllListeners() {
        if (chatListListener != null) {
            chatListListener.remove();
            chatListListener = null;
        }
        for (ListenerRegistration reg : messageListeners.values()) {
            if (reg != null) reg.remove();
        }
        messageListeners.clear();
    }
}