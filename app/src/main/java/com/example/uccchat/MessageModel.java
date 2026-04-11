package com.example.uccchat;

import com.google.firebase.Timestamp;
import java.util.List;

public class MessageModel {

    // Message types
    public static final String TYPE_TEXT  = "text";
    public static final String TYPE_IMAGE = "image";
    public static final String TYPE_FILE  = "file";
    public static final String TYPE_VIDEO = "video";

    private String messageId;
    private String senderId;
    private String text;
    private Timestamp timestamp;
    private String type;
    private String imageUrl;
    private boolean seen;
    private List<String> seenBy;
    private List<String> deletedFor;

    private String fileName;
    private String fileSize;
    private String fileType;
    private String fileUrl;

    private String thumbnailUrl;
    private String videoDuration;

    // Required empty constructor for Firestore
    public MessageModel() {}

    // Constructor for text messages
    public MessageModel(String messageId, String senderId,
                        String text, Timestamp timestamp) {
        this.messageId = messageId;
        this.senderId  = senderId;
        this.text      = text;
        this.timestamp = timestamp;
        this.type      = TYPE_TEXT;
        this.imageUrl  = null;
        this.seen      = false;
    }

    // Constructor for image messages
    public MessageModel(String messageId, String senderId,
                        String imageUrl, Timestamp timestamp,
                        boolean isImage) {
        this.messageId = messageId;
        this.senderId  = senderId;
        this.text      = "";
        this.timestamp = timestamp;
        this.type      = TYPE_IMAGE;
        this.imageUrl  = imageUrl;
        this.seen      = false;
    }

    // ─── Getters ──────────────────────────────────────────────
    public String getMessageId()        { return messageId; }
    public String getSenderId()         { return senderId; }
    public String getText()             { return text; }
    public Timestamp getTimestamp()     { return timestamp; }
    public String getType()             { return type; }
    public String getImageUrl()         { return imageUrl; }
    public boolean isSeen()             { return seen; }
    public List<String> getSeenBy()     { return seenBy; }
    public List<String> getDeletedFor() { return deletedFor; }
    public String getFileName()         { return fileName; }
    public String getFileSize()         { return fileSize; }
    public String getFileType()         { return fileType; }
    public String getFileUrl()          { return fileUrl; }
    public String getThumbnailUrl()     { return thumbnailUrl; }
    public String getVideoDuration()    { return videoDuration; }

    // ─── Setters ──────────────────────────────────────────────
    public void setMessageId(String messageId)         { this.messageId = messageId; }
    public void setSenderId(String senderId)           { this.senderId = senderId; }
    public void setText(String text)                   { this.text = text; }
    public void setTimestamp(Timestamp timestamp)      { this.timestamp = timestamp; }
    public void setType(String type)                   { this.type = type; }
    public void setImageUrl(String imageUrl)           { this.imageUrl = imageUrl; }
    public void setSeen(boolean seen)                  { this.seen = seen; }
    public void setSeenBy(List<String> seenBy)         { this.seenBy = seenBy; }
    public void setDeletedFor(List<String> deletedFor) { this.deletedFor = deletedFor; }
    public void setFileName(String fileName)           { this.fileName = fileName; }
    public void setFileSize(String fileSize)           { this.fileSize = fileSize; }
    public void setFileType(String fileType)           { this.fileType = fileType; }
    public void setFileUrl(String fileUrl)             { this.fileUrl = fileUrl; }
    public void setThumbnailUrl(String t)              { this.thumbnailUrl = t; }
    public void setVideoDuration(String d)             { this.videoDuration = d; }

    // ─── Helpers ──────────────────────────────────────────────
    public boolean isTextMessage()  { return TYPE_TEXT.equals(type); }
    public boolean isImageMessage() { return TYPE_IMAGE.equals(type); }
    public boolean isFileMessage()  { return TYPE_FILE.equals(type); }
    public boolean isVideoMessage() { return TYPE_VIDEO.equals(type); }

    public boolean isSeenBy(String uid) {
        return seenBy != null && seenBy.contains(uid);
    }

    public boolean isDeletedFor(String uid) {
        return deletedFor != null && deletedFor.contains(uid);
    }

    public String getPreviewText() {
        if (TYPE_IMAGE.equals(type)) return "📷 Photo";
        if (TYPE_FILE.equals(type))  return "📎 " + (fileName != null ? fileName : "File");
        if (TYPE_VIDEO.equals(type)) return "🎥 Video";
        return text != null ? text : "";
    }
}