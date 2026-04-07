package com.example.uccchat;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.PropertyName;
import java.util.List;
import java.util.Map;

public class ChatModel {

    private String chatId;
    private List<String> participants;
    private Map<String, String> participantNames;
    private Map<String, String> participantPhotos;
    private String lastMessage;
    private Timestamp lastMessageTime;
    private String lastSenderId;

    // ✅ Fix: use @PropertyName to force Firestore to use "isGroup"
    @PropertyName("isGroup")
    private boolean isGroup;

    private String groupName;
    private String groupPhoto;
    private Map<String, Long> unreadCount;

    public ChatModel() {}

    public ChatModel(String chatId,
                     List<String> participants,
                     Map<String, String> participantNames,
                     Map<String, String> participantPhotos,
                     String lastMessage,
                     Timestamp lastMessageTime,
                     String lastSenderId,
                     boolean isGroup,
                     String groupName,
                     String groupPhoto,
                     Map<String, Long> unreadCount) {
        this.chatId            = chatId;
        this.participants      = participants;
        this.participantNames  = participantNames;
        this.participantPhotos = participantPhotos;
        this.lastMessage       = lastMessage;
        this.lastMessageTime   = lastMessageTime;
        this.lastSenderId      = lastSenderId;
        this.isGroup           = isGroup;
        this.groupName         = groupName;
        this.groupPhoto        = groupPhoto;
        this.unreadCount       = unreadCount;
    }

    // Getters
    public String getChatId()                         { return chatId; }
    public List<String> getParticipants()             { return participants; }
    public Map<String, String> getParticipantNames()  { return participantNames; }
    public Map<String, String> getParticipantPhotos() { return participantPhotos; }
    public String getLastMessage()                    { return lastMessage; }
    public Timestamp getLastMessageTime()             { return lastMessageTime; }
    public String getLastSenderId()                   { return lastSenderId; }
    public String getGroupName()                      { return groupName; }
    public String getGroupPhoto()                     { return groupPhoto; }
    public Map<String, Long> getUnreadCount()         { return unreadCount; }

    // ✅ Fix: annotate both getter and setter
    @PropertyName("isGroup")
    public boolean isGroup() { return isGroup; }

    // Setters
    public void setChatId(String chatId)                       { this.chatId = chatId; }
    public void setParticipants(List<String> p)                { this.participants = p; }
    public void setParticipantNames(Map<String, String> n)     { this.participantNames = n; }
    public void setParticipantPhotos(Map<String, String> p)    { this.participantPhotos = p; }
    public void setLastMessage(String m)                       { this.lastMessage = m; }
    public void setLastMessageTime(Timestamp t)                { this.lastMessageTime = t; }
    public void setLastSenderId(String id)                     { this.lastSenderId = id; }
    public void setGroupName(String groupName)                 { this.groupName = groupName; }
    public void setGroupPhoto(String groupPhoto)               { this.groupPhoto = groupPhoto; }
    public void setUnreadCount(Map<String, Long> u)            { this.unreadCount = u; }

    @PropertyName("isGroup")
    public void setGroup(boolean group) { this.isGroup = group; }

    // ── Helpers ───────────────────────────────────────────────

    public String getOtherUid(String myUid) {
        if (participants == null) return null;
        for (String uid : participants) {
            if (!uid.equals(myUid)) return uid;
        }
        return null;
    }

    public String getDisplayName(String myUid) {
        if (isGroup) return groupName != null ? groupName : "Group Chat";
        String otherUid = getOtherUid(myUid);
        if (otherUid != null && participantNames != null) {
            return participantNames.get(otherUid);
        }
        return "Unknown";
    }

    public String getDisplayPhoto(String myUid) {
        if (isGroup) return groupPhoto;
        String otherUid = getOtherUid(myUid);
        if (otherUid != null && participantPhotos != null) {
            return participantPhotos.get(otherUid);
        }
        return null;
    }

    public long getUnreadCountFor(String uid) {
        if (unreadCount == null) return 0;
        Long count = unreadCount.get(uid);
        return count != null ? count : 0;
    }
}