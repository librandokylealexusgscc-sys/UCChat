package com.example.uccchat;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirestoreHelper {

    private static FirestoreHelper instance;
    private final FirebaseFirestore db;

    public static final String COL_USERS    = "users";
    public static final String COL_CHATS    = "chats";
    public static final String COL_MESSAGES = "messages";

    private FirestoreHelper() {
        db = FirebaseFirestore.getInstance();
    }

    public static FirestoreHelper get() {
        if (instance == null) instance = new FirestoreHelper();
        return instance;
    }

    // ════════════════════════════════════════════════════════
    //  USER OPERATIONS
    // ════════════════════════════════════════════════════════

    public void getUser(String uid, OnUserFetched callback) {
        db.collection(COL_USERS)
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        UserModel user = doc.toObject(UserModel.class);
                        if (user != null) user.setUid(doc.getId());
                        callback.onSuccess(user);
                    } else {
                        callback.onFailure("User not found");
                    }
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    // ════════════════════════════════════════════════════════
    //  CHAT OPERATIONS
    // ════════════════════════════════════════════════════════

    /**
     * FREE TIER OPTIMIZATION:
     * Instead of querying all chats and filtering in Firestore,
     * we fetch once and filter in memory.
     * This avoids needing a composite index and saves reads.
     */
    public void getOrCreateChat(UserModel currentUser,
                                UserModel otherUser,
                                OnChatReady callback) {

        String myUid    = currentUser.getUid();
        String otherUid = otherUser.getUid();

        db.collection(COL_CHATS)
                .whereArrayContains("participants", myUid)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    // getOrCreateChat method
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        ChatModel chat = doc.toObject(ChatModel.class);
                        if (chat != null
                                && !chat.isGroup()
                                && chat.getParticipants() != null
                                && chat.getParticipants().contains(otherUid)) {
                            callback.onReady(doc.getId());
                            return;
                        }
                    }
                    createNewChat(currentUser, otherUser, callback);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    private void createNewChat(UserModel currentUser,
                               UserModel otherUser,
                               OnChatReady callback) {

        String myUid    = currentUser.getUid();
        String otherUid = otherUser.getUid();

        Map<String, String> names = new HashMap<>();
        names.put(myUid,    currentUser.getFirstName());
        names.put(otherUid, otherUser.getFirstName());

        Map<String, String> photos = new HashMap<>();
        photos.put(myUid,    currentUser.getPhotoUrl() != null
                ? currentUser.getPhotoUrl() : "");
        photos.put(otherUid, otherUser.getPhotoUrl() != null
                ? otherUser.getPhotoUrl() : "");

        // FREE TIER: unreadCount stored as simple int per user
        // avoids complex queries to count unread messages
        Map<String, Long> unread = new HashMap<>();
        unread.put(myUid,    0L);
        unread.put(otherUid, 0L);

        Map<String, Object> chat = new HashMap<>();
        chat.put("participants",      Arrays.asList(myUid, otherUid));
        chat.put("participantNames",  names);
        chat.put("participantPhotos", photos);
        chat.put("lastMessage",       "");
        chat.put("lastMessageTime",   Timestamp.now());
        chat.put("lastSenderId",      myUid);
        chat.put("isGroup",           false);
        chat.put("groupName",         null);
        chat.put("groupPhoto",        null);
        chat.put("unreadCount",       unread);

        db.collection(COL_CHATS)
                .add(chat)
                .addOnSuccessListener(ref -> callback.onReady(ref.getId()))
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public void createGroupChat(List<String> participantUids,
                                Map<String, String> participantNames,
                                Map<String, String> participantPhotos,
                                String groupName,
                                String currentUserUid,
                                OnChatReady callback) {

        Map<String, Long> unread = new HashMap<>();
        for (String uid : participantUids) unread.put(uid, 0L);

        Map<String, Object> chat = new HashMap<>();
        chat.put("createdBy", currentUserUid); // ✅ Add this line
        chat.put("participants",      participantUids);
        chat.put("participantNames",  participantNames);
        chat.put("participantPhotos", participantPhotos);
        chat.put("lastMessage",       "");
        chat.put("lastMessageTime",   Timestamp.now());
        chat.put("lastSenderId",      currentUserUid);
        chat.put("isGroup",           true);
        chat.put("groupName",         groupName);
        chat.put("groupPhoto",        null);
        chat.put("unreadCount",       unread);

        db.collection(COL_CHATS)
                .add(chat)
                .addOnSuccessListener(ref -> callback.onReady(ref.getId()))
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /**
     * FREE TIER OPTIMIZATION:
     * Simple query — only needs 1 field index (auto-created by Firestore).
     * No composite index needed = no console setup required.
     */
    public Query listenToChats(String uid) {
        return db.collection(COL_CHATS)
                .whereArrayContains("participants", uid);
        // ⚠️ We sort by lastMessageTime in Java (ChatHomeActivity)
        // instead of Firestore — avoids composite index requirement
    }

    // ════════════════════════════════════════════════════════
    //  MESSAGE OPERATIONS
    // ════════════════════════════════════════════════════════

    /**
     * FREE TIER OPTIMIZATION:
     * Uses a WriteBatch — saves the message AND updates the chat
     * in a single network round trip = 2 writes instead of 2
     * separate network calls that could fail independently.
     */
    public void sendTextMessage(String chatId,
                                String senderId,
                                String text,
                                List<String> otherParticipants,
                                OnMessageSent callback) {

        DocumentReference chatRef = db.collection(COL_CHATS).document(chatId);
        DocumentReference msgRef  = chatRef.collection(COL_MESSAGES).document();

        Timestamp now = Timestamp.now();

        Map<String, Object> message = new HashMap<>();
        message.put("messageId",  msgRef.getId());
        message.put("senderId",   senderId);
        message.put("text",       text);
        message.put("timestamp",  now);
        message.put("type",       MessageModel.TYPE_TEXT);
        message.put("imageUrl",   null);
        message.put("seen",       false);
        message.put("seenBy",     Arrays.asList(senderId));
        message.put("deletedFor", Arrays.asList());

        // Unread increments for other participants
        Map<String, Object> chatUpdates = new HashMap<>();
        chatUpdates.put("lastMessage",     text);
        chatUpdates.put("lastMessageTime", now);
        chatUpdates.put("lastSenderId",    senderId);
        for (String uid : otherParticipants) {
            chatUpdates.put("unreadCount." + uid, FieldValue.increment(1));
        }

        // Batch write — atomic, 1 round trip
        WriteBatch batch = db.batch();
        batch.set(msgRef, message);
        batch.update(chatRef, chatUpdates);
        batch.commit()
                .addOnSuccessListener(u -> callback.onSent(msgRef.getId()))
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public void sendImageMessage(String chatId,
                                 String senderId,
                                 String imageUrl,
                                 List<String> otherParticipants,
                                 OnMessageSent callback) {

        DocumentReference chatRef = db.collection(COL_CHATS).document(chatId);
        DocumentReference msgRef  = chatRef.collection(COL_MESSAGES).document();

        Timestamp now = Timestamp.now();

        Map<String, Object> message = new HashMap<>();
        message.put("messageId",  msgRef.getId());
        message.put("senderId",   senderId);
        message.put("text",       "");
        message.put("timestamp",  now);
        message.put("type",       MessageModel.TYPE_IMAGE);
        message.put("imageUrl",   imageUrl);
        message.put("seen",       false);
        message.put("seenBy",     Arrays.asList(senderId));
        message.put("deletedFor", Arrays.asList());

        Map<String, Object> chatUpdates = new HashMap<>();
        chatUpdates.put("lastMessage",     "📷 Photo");
        chatUpdates.put("lastMessageTime", now);
        chatUpdates.put("lastSenderId",    senderId);
        for (String uid : otherParticipants) {
            chatUpdates.put("unreadCount." + uid, FieldValue.increment(1));
        }

        WriteBatch batch = db.batch();
        batch.set(msgRef, message);
        batch.update(chatRef, chatUpdates);
        batch.commit()
                .addOnSuccessListener(u -> callback.onSent(msgRef.getId()))
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public Query listenToMessages(String chatId) {
        return db.collection(COL_CHATS)
                .document(chatId)
                .collection(COL_MESSAGES)
                .orderBy("timestamp", Query.Direction.ASCENDING);
        // ✅ Single field index — auto-created, no setup needed
    }

    /**
     * FREE TIER OPTIMIZATION:
     * Only updates the chat document's unreadCount.
     * Does NOT query all messages to mark them seen —
     * that would waste 50-100+ reads per chat open.
     * "seen" status is only tracked on the chat level (unreadCount).
     */
    public void markChatAsRead(String chatId, String uid) {
        db.collection(COL_CHATS)
                .document(chatId)
                .update("unreadCount." + uid, 0);
        // ✅ Just 1 write — no reads wasted
    }

    public void deleteMessageFor(String chatId,
                                 String messageId,
                                 String uid) {
        db.collection(COL_CHATS)
                .document(chatId)
                .collection(COL_MESSAGES)
                .document(messageId)
                .update("deletedFor", FieldValue.arrayUnion(uid));
    }

    // ════════════════════════════════════════════════════════
    //  CALLBACKS
    // ════════════════════════════════════════════════════════

    public interface OnUserFetched {
        void onSuccess(UserModel user);
        void onFailure(String error);
    }

    public interface OnChatReady {
        void onReady(String chatId);
        void onFailure(String error);
    }

    public interface OnMessageSent {
        void onSent(String messageId);
        void onFailure(String error);
    }

    // ════════════════════════════════════════════════════════
//  BLOCK / UNBLOCK
// ════════════════════════════════════════════════════════

    public void blockUser(String myUid, String blockedUid,
                          OnActionComplete callback) {
        db.collection(COL_USERS).document(myUid)
                .update("blockedUsers", FieldValue.arrayUnion(blockedUid))
                .addOnSuccessListener(u -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public void unblockUser(String myUid, String blockedUid,
                            OnActionComplete callback) {
        db.collection(COL_USERS).document(myUid)
                .update("blockedUsers", FieldValue.arrayRemove(blockedUid))
                .addOnSuccessListener(u -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public void isUserBlocked(String myUid, String otherUid,
                              OnBlockChecked callback) {
        db.collection(COL_USERS).document(myUid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) { callback.onResult(false); return; }
                    java.util.List<String> blocked =
                            (java.util.List<String>) doc.get("blockedUsers");
                    callback.onResult(blocked != null
                            && blocked.contains(otherUid));
                })
                .addOnFailureListener(e -> callback.onResult(false));
    }

// ════════════════════════════════════════════════════════
//  MUTE / UNMUTE
// ════════════════════════════════════════════════════════

    public void muteChat(String myUid, String chatId,
                         OnActionComplete callback) {
        db.collection(COL_USERS).document(myUid)
                .update("mutedChats", FieldValue.arrayUnion(chatId))
                .addOnSuccessListener(u -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public void unmuteChat(String myUid, String chatId,
                           OnActionComplete callback) {
        db.collection(COL_USERS).document(myUid)
                .update("mutedChats", FieldValue.arrayRemove(chatId))
                .addOnSuccessListener(u -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public void isChatMuted(String myUid, String chatId,
                            OnMuteChecked callback) {
        db.collection(COL_USERS).document(myUid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) { callback.onResult(false); return; }
                    java.util.List<String> muted =
                            (java.util.List<String>) doc.get("mutedChats");
                    callback.onResult(muted != null
                            && muted.contains(chatId));
                })
                .addOnFailureListener(e -> callback.onResult(false));
    }

// ════════════════════════════════════════════════════════
//  DELETE CONVERSATION
// ════════════════════════════════════════════════════════

    /**
     * Soft delete — adds myUid to deletedFor on the chat.
     * Both users deleting = full delete.
     */
    public void deleteConversation(String chatId, String myUid,
                                   java.util.List<String> participants,
                                   OnActionComplete callback) {
        // Add myUid to deletedFor array on chat doc
        db.collection(COL_CHATS).document(chatId)
                .update("deletedFor", FieldValue.arrayUnion(myUid))
                .addOnSuccessListener(u -> {
                    // Check if ALL participants deleted
                    // If so, hard delete the chat
                    db.collection(COL_CHATS).document(chatId)
                            .get()
                            .addOnSuccessListener(doc -> {
                                if (!doc.exists()) {
                                    callback.onSuccess();
                                    return;
                                }
                                java.util.List<String> deletedFor =
                                        (java.util.List<String>) doc.get("deletedFor");
                                if (deletedFor != null
                                        && deletedFor.containsAll(participants)) {
                                    // Everyone deleted — hard delete
                                    hardDeleteChat(chatId, callback);
                                } else {
                                    callback.onSuccess();
                                }
                            });
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    private void hardDeleteChat(String chatId, OnActionComplete callback) {
        // Delete messages subcollection first
        db.collection(COL_CHATS).document(chatId)
                .collection(COL_MESSAGES).get()
                .addOnSuccessListener(snap -> {
                    for (com.google.firebase.firestore.DocumentSnapshot doc
                            : snap.getDocuments()) {
                        doc.getReference().delete();
                    }
                    // Delete chat document
                    db.collection(COL_CHATS).document(chatId)
                            .delete()
                            .addOnSuccessListener(u -> callback.onSuccess())
                            .addOnFailureListener(e ->
                                    callback.onFailure(e.getMessage()));
                });
    }

// ════════════════════════════════════════════════════════
//  GROUP PHOTO UPDATE
// ════════════════════════════════════════════════════════

    public void updateGroupPhoto(String chatId, String photoUrl,
                                 OnActionComplete callback) {
        db.collection(COL_CHATS).document(chatId)
                .update("groupPhoto", photoUrl)
                .addOnSuccessListener(u -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

// ════════════════════════════════════════════════════════
//  ADD MEMBER TO GROUP
// ════════════════════════════════════════════════════════

    public void addMemberToGroup(String chatId,
                                 UserModel newMember,
                                 OnActionComplete callback) {
        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("participants",
                FieldValue.arrayUnion(newMember.getUid()));
        updates.put("participantNames." + newMember.getUid(),
                newMember.getFirstName() + " " + newMember.getLastName());
        updates.put("participantPhotos." + newMember.getUid(),
                newMember.getPhotoUrl() != null ? newMember.getPhotoUrl() : "");
        updates.put("unreadCount." + newMember.getUid(), 0L);

        db.collection(COL_CHATS).document(chatId)
                .update(updates)
                .addOnSuccessListener(u -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

// ════════════════════════════════════════════════════════
//  CALLBACKS
// ════════════════════════════════════════════════════════

    public interface OnActionComplete {
        void onSuccess();
        void onFailure(String error);
    }

    public interface OnBlockChecked {
        void onResult(boolean isBlocked);
    }

    public interface OnMuteChecked {
        void onResult(boolean isMuted);
    }

    public void sendFileMessage(String chatId,
                                String senderId,
                                String fileUrl,
                                String fileName,
                                String fileSize,
                                String fileType,
                                List<String> otherParticipants,
                                OnMessageSent callback) {

        DocumentReference chatRef = db.collection(COL_CHATS).document(chatId);
        DocumentReference msgRef  = chatRef.collection(COL_MESSAGES).document();

        Timestamp now = Timestamp.now();

        Map<String, Object> message = new HashMap<>();
        message.put("messageId",  msgRef.getId());
        message.put("senderId",   senderId);
        message.put("text",       "");
        message.put("timestamp",  now);
        message.put("type",       MessageModel.TYPE_FILE);
        message.put("imageUrl",   null);
        message.put("fileUrl",    fileUrl);
        message.put("fileName",   fileName);
        message.put("fileSize",   fileSize);
        message.put("fileType",   fileType);
        message.put("seen",       false);
        message.put("seenBy",     Arrays.asList(senderId));
        message.put("deletedFor", Arrays.asList());

        String preview = "📎 " + fileName;

        Map<String, Object> chatUpdates = new HashMap<>();
        chatUpdates.put("lastMessage",     preview);
        chatUpdates.put("lastMessageTime", now);
        chatUpdates.put("lastSenderId",    senderId);
        for (String uid : otherParticipants) {
            chatUpdates.put("unreadCount." + uid, FieldValue.increment(1));
        }

        WriteBatch batch = db.batch();
        batch.set(msgRef, message);
        batch.update(chatRef, chatUpdates);
        batch.commit()
                .addOnSuccessListener(u -> callback.onSent(msgRef.getId()))
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public void sendVideoMessage(String chatId,
                                 String senderId,
                                 String videoUrl,
                                 String thumbnailUrl,
                                 String videoDuration,
                                 List<String> otherParticipants,
                                 OnMessageSent callback) {

        DocumentReference chatRef = db.collection(COL_CHATS).document(chatId);
        DocumentReference msgRef  = chatRef.collection(COL_MESSAGES).document();

        Timestamp now = Timestamp.now();

        Map<String, Object> message = new HashMap<>();
        message.put("messageId",     msgRef.getId());
        message.put("senderId",      senderId);
        message.put("text",          "");
        message.put("timestamp",     now);
        message.put("type",          MessageModel.TYPE_VIDEO);
        message.put("imageUrl",      null);
        message.put("fileUrl",       videoUrl);
        message.put("thumbnailUrl",  thumbnailUrl);
        message.put("videoDuration", videoDuration);
        message.put("seen",          false);
        message.put("seenBy",        Arrays.asList(senderId));
        message.put("deletedFor",    Arrays.asList());

        Map<String, Object> chatUpdates = new HashMap<>();
        chatUpdates.put("lastMessage",     "🎥 Video");
        chatUpdates.put("lastMessageTime", now);
        chatUpdates.put("lastSenderId",    senderId);
        for (String uid : otherParticipants) {
            chatUpdates.put("unreadCount." + uid, FieldValue.increment(1));
        }

        WriteBatch batch = db.batch();
        batch.set(msgRef, message);
        batch.update(chatRef, chatUpdates);
        batch.commit()
                .addOnSuccessListener(u -> callback.onSent(msgRef.getId()))
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

}