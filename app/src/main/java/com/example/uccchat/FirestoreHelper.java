package com.example.uccchat;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

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

    public void getOrCreateChat(UserModel currentUser,
                                UserModel otherUser,
                                OnChatReady callback) {
        String myUid    = currentUser.getUid();
        String otherUid = otherUser.getUid();

        db.collection(COL_CHATS)
                .whereArrayContains("participants", myUid)
                .get()
                .addOnSuccessListener(querySnapshot -> {
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
        photos.put(myUid,    currentUser.getPhotoUrl() != null ? currentUser.getPhotoUrl() : "");
        photos.put(otherUid, otherUser.getPhotoUrl()   != null ? otherUser.getPhotoUrl()   : "");

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
                                boolean manuallyNamed,
                                String currentUserUid,
                                OnChatReady callback) {

        Map<String, Long> unread = new HashMap<>();
        for (String uid : participantUids) unread.put(uid, 0L);

        Map<String, Object> chat = new HashMap<>();
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
        chat.put("createdBy",         currentUserUid);
        chat.put("manuallyNamed",     manuallyNamed); // ✅ NEW

        db.collection(COL_CHATS)
                .add(chat)
                .addOnSuccessListener(ref -> callback.onReady(ref.getId()))
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public Query listenToChats(String uid) {
        return db.collection(COL_CHATS)
                .whereArrayContains("participants", uid);
    }

    // ════════════════════════════════════════════════════════
    //  MESSAGE OPERATIONS
    // ════════════════════════════════════════════════════════

    public void sendTextMessage(String chatId,
                                String senderId,
                                String text,
                                String chatName,
                                String myUid,
                                MessageModel replySnapshot,
                                List<String> otherParticipants,
                                OnMessageSent callback) {
        sendTextMessage(chatId, senderId, text, chatName, myUid,
                replySnapshot, otherParticipants, false, callback);
    }

    // Overload that accepts forwarded flag
    public void sendTextMessage(String chatId,
                                String senderId,
                                String text,
                                String chatName,
                                String myUid,
                                MessageModel replySnapshot,
                                List<String> otherParticipants,
                                boolean forwarded,
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
        message.put("deleted",    false);
        message.put("forwarded",  forwarded);

        if (replySnapshot != null) {
            message.put("replyToId",     replySnapshot.getMessageId());
            message.put("replyToText",   replySnapshot.getPreviewText());
            message.put("replyToType",   replySnapshot.getType());
            message.put("replyToSender", replySnapshot.getSenderId().equals(myUid)
                    ? "You" : chatName);
        }

        Map<String, Object> chatUpdates = new HashMap<>();
        chatUpdates.put("lastMessage",     forwarded ? "➡ " + text : text);
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

    public void sendImageMessage(String chatId,
                                 String senderId,
                                 String imageUrl,
                                 List<String> otherParticipants,
                                 MessageModel replySnapshot,
                                 String chatName,
                                 String myUid,
                                 OnMessageSent callback) {
        sendImageMessage(chatId, senderId, imageUrl, otherParticipants,
                replySnapshot, chatName, myUid, false, callback);
    }

    // Overload that accepts forwarded flag
    public void sendImageMessage(String chatId,
                                 String senderId,
                                 String imageUrl,
                                 List<String> otherParticipants,
                                 MessageModel replySnapshot,
                                 String chatName,
                                 String myUid,
                                 boolean forwarded,
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
        message.put("deleted",    false);
        message.put("forwarded",  forwarded);

        if (replySnapshot != null) {
            message.put("replyToId",     replySnapshot.getMessageId());
            message.put("replyToText",   replySnapshot.getPreviewText());
            message.put("replyToType",   replySnapshot.getType());
            message.put("replyToSender", replySnapshot.getSenderId().equals(myUid)
                    ? "You" : chatName);
        }

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

    public void sendFileMessage(String chatId,
                                String senderId,
                                String fileUrl,
                                String fileName,
                                String fileSize,
                                String fileType,
                                List<String> otherParticipants,
                                OnMessageSent callback) {
        sendFileMessage(chatId, senderId, fileUrl, fileName, fileSize,
                fileType, otherParticipants, false, callback);
    }

    // Overload that accepts forwarded flag
    public void sendFileMessage(String chatId,
                                String senderId,
                                String fileUrl,
                                String fileName,
                                String fileSize,
                                String fileType,
                                List<String> otherParticipants,
                                boolean forwarded,
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
        message.put("deleted",    false);
        message.put("forwarded",  forwarded);

        Map<String, Object> chatUpdates = new HashMap<>();
        chatUpdates.put("lastMessage",     "📎 " + fileName);
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
        sendVideoMessage(chatId, senderId, videoUrl, thumbnailUrl,
                videoDuration, otherParticipants, false, callback);
    }

    // Overload that accepts forwarded flag
    public void sendVideoMessage(String chatId,
                                 String senderId,
                                 String videoUrl,
                                 String thumbnailUrl,
                                 String videoDuration,
                                 List<String> otherParticipants,
                                 boolean forwarded,
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
        message.put("deleted",       false);
        message.put("forwarded",     forwarded);

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

    public Query listenToMessages(String chatId) {
        return db.collection(COL_CHATS)
                .document(chatId)
                .collection(COL_MESSAGES)
                .orderBy("timestamp", Query.Direction.ASCENDING);
    }

    public void markChatAsRead(String chatId, String uid) {
        db.collection(COL_CHATS)
                .document(chatId)
                .update("unreadCount." + uid, 0);
    }

    // ── KEY CHANGE: instead of hiding the message, mark deleted=true ──
    // This makes BOTH sides see "🚫 Message was deleted" in place of the bubble
    public void deleteMessageFor(String chatId,
                                 String messageId,
                                 String uid) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("deletedFor", FieldValue.arrayUnion(uid));
        updates.put("deleted", true);

        db.collection(COL_CHATS)
                .document(chatId)
                .collection(COL_MESSAGES)
                .document(messageId)
                .update(updates);
    }

    public void deleteMessagePermanently(String chatId,
                                         String messageId,
                                         Runnable onSuccess,
                                         Consumer<String> onFailure) {
        db.collection(COL_CHATS)
                .document(chatId)
                .collection(COL_MESSAGES)
                .document(messageId)
                .delete()
                .addOnSuccessListener(a -> { if (onSuccess != null) onSuccess.run(); })
                .addOnFailureListener(e -> { if (onFailure != null) onFailure.accept(e.getMessage()); });
    }

    // ════════════════════════════════════════════════════════
    //  BLOCK / UNBLOCK
    // ════════════════════════════════════════════════════════

    public void blockUser(String myUid, String blockedUid, OnActionComplete callback) {
        db.collection(COL_USERS).document(myUid)
                .update("blockedUsers", FieldValue.arrayUnion(blockedUid))
                .addOnSuccessListener(u -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public void unblockUser(String myUid, String blockedUid, OnActionComplete callback) {
        db.collection(COL_USERS).document(myUid)
                .update("blockedUsers", FieldValue.arrayRemove(blockedUid))
                .addOnSuccessListener(u -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public void isUserBlocked(String myUid, String otherUid, OnBlockChecked callback) {
        db.collection(COL_USERS).document(myUid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) { callback.onResult(false); return; }
                    List<String> blocked = (List<String>) doc.get("blockedUsers");
                    callback.onResult(blocked != null && blocked.contains(otherUid));
                })
                .addOnFailureListener(e -> callback.onResult(false));
    }

    // ════════════════════════════════════════════════════════
    //  MUTE / UNMUTE
    // ════════════════════════════════════════════════════════

    public void muteChat(String myUid, String chatId, OnActionComplete callback) {
        db.collection(COL_USERS).document(myUid)
                .update("mutedChats", FieldValue.arrayUnion(chatId))
                .addOnSuccessListener(u -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public void unmuteChat(String myUid, String chatId, OnActionComplete callback) {
        db.collection(COL_USERS).document(myUid)
                .update("mutedChats", FieldValue.arrayRemove(chatId))
                .addOnSuccessListener(u -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public void isChatMuted(String myUid, String chatId, OnMuteChecked callback) {
        db.collection(COL_USERS).document(myUid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) { callback.onResult(false); return; }
                    List<String> muted = (List<String>) doc.get("mutedChats");
                    callback.onResult(muted != null && muted.contains(chatId));
                })
                .addOnFailureListener(e -> callback.onResult(false));
    }

    // ════════════════════════════════════════════════════════
    //  DELETE CONVERSATION
    // ════════════════════════════════════════════════════════

    public void deleteConversation(String chatId,
                                   String myUid,
                                   List<String> participants,
                                   OnActionComplete callback) {
        db.collection(COL_CHATS).document(chatId)
                .update("deletedFor", FieldValue.arrayUnion(myUid))
                .addOnSuccessListener(u -> {
                    db.collection(COL_CHATS).document(chatId)
                            .get()
                            .addOnSuccessListener(doc -> {
                                if (!doc.exists()) { callback.onSuccess(); return; }
                                List<String> deletedFor = (List<String>) doc.get("deletedFor");
                                if (deletedFor != null && deletedFor.containsAll(participants)) {
                                    hardDeleteChat(chatId, callback);
                                } else {
                                    callback.onSuccess();
                                }
                            });
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    private void hardDeleteChat(String chatId, OnActionComplete callback) {
        db.collection(COL_CHATS).document(chatId)
                .collection(COL_MESSAGES).get()
                .addOnSuccessListener(snap -> {
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        doc.getReference().delete();
                    }
                    db.collection(COL_CHATS).document(chatId)
                            .delete()
                            .addOnSuccessListener(u -> callback.onSuccess())
                            .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
                });
    }

    // ════════════════════════════════════════════════════════
    //  GROUP PHOTO UPDATE
    // ════════════════════════════════════════════════════════

    public void updateGroupPhoto(String chatId, String photoUrl,
                                 OnActionComplete callback) {
        db.collection(COL_CHATS).document(chatId)
                .update("groupPhoto", photoUrl)  // ✅ must be "groupPhoto"
                .addOnSuccessListener(u -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    // ════════════════════════════════════════════════════════
    //  ADD MEMBER TO GROUP
    // ════════════════════════════════════════════════════════

    public void addMemberToGroup(String chatId,
                                 UserModel newMember,
                                 String adderFullName,
                                 OnActionComplete callback) {

        db.collection(COL_CHATS).document(chatId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) { callback.onFailure("Chat not found"); return; }

                    Boolean manuallyNamed = doc.getBoolean("manuallyNamed");

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("participants",
                            FieldValue.arrayUnion(newMember.getUid()));
                    updates.put("participantNames." + newMember.getUid(),
                            newMember.getFirstName() + " " + newMember.getLastName());
                    updates.put("participantPhotos." + newMember.getUid(),
                            newMember.getPhotoUrl() != null ? newMember.getPhotoUrl() : "");
                    updates.put("unreadCount." + newMember.getUid(), 0L);

                    // ✅ If group has NO custom name yet (auto-generated),
                    // add new member's first name to the existing name
                    if (!Boolean.TRUE.equals(manuallyNamed)) {
                        String currentName = doc.getString("groupName");
                        String newGroupName = (currentName != null && !currentName.isEmpty())
                                ? currentName + ", " + newMember.getFirstName()
                                : newMember.getFirstName();
                        updates.put("groupName", newGroupName);
                    }
                    // ✅ If group already has a custom/manually set name → don't touch it

                    // ✅ Once a member is added, lock the name permanently
                    updates.put("manuallyNamed", true);

                    db.collection(COL_CHATS).document(chatId)
                            .update(updates)
                            .addOnSuccessListener(u -> {
                                String systemText = adderFullName + " added "
                                        + newMember.getFirstName() + " "
                                        + newMember.getLastName() + " to the group";
                                sendSystemMessage(chatId, systemText);
                                callback.onSuccess();
                            })
                            .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }
    public void kickMemberFromGroup(String chatId,
                                    String kickerFullName,
                                    UserModel member,
                                    OnActionComplete callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("participants",
                FieldValue.arrayRemove(member.getUid()));
        updates.put("unreadCount." + member.getUid(),
                FieldValue.delete());
        updates.put("participantNames." + member.getUid(),
                FieldValue.delete());
        updates.put("participantPhotos." + member.getUid(),
                FieldValue.delete());

        db.collection(COL_CHATS).document(chatId)
                .update(updates)
                .addOnSuccessListener(u -> {
                    String systemText = kickerFullName + " kicked "
                            + member.getFirstName() + " "
                            + member.getLastName() + " from the group";
                    sendSystemMessage(chatId, systemText);
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }
    // ════════════════════════════════════════════════════════
    //  CALLBACKS
    // ════════════════════════════════════════════════════════
    public void sendSystemMessage(String chatId, String text) {
        DocumentReference chatRef = db.collection(COL_CHATS).document(chatId);
        DocumentReference msgRef  = chatRef.collection(COL_MESSAGES).document();
        Timestamp now = Timestamp.now();

        Map<String, Object> message = new HashMap<>();
        message.put("messageId",  msgRef.getId());
        message.put("senderId",   "system");
        message.put("text",       text);
        message.put("timestamp",  now);
        message.put("type",       MessageModel.TYPE_SYSTEM);
        message.put("seen",       true);
        message.put("seenBy",     new ArrayList<>());
        message.put("deletedFor", new ArrayList<>());
        message.put("deleted",    false);

        Map<String, Object> chatUpdates = new HashMap<>();
        chatUpdates.put("lastMessage",     text);
        chatUpdates.put("lastMessageTime", now);
        chatUpdates.put("lastSenderId",    "system");

        WriteBatch batch = db.batch();
        batch.set(msgRef, message);
        batch.update(chatRef, chatUpdates);
        batch.commit();
    }
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
}