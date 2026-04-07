package com.example.uccchat;

import com.google.firebase.Timestamp;
import java.util.List;

public class MessageModel {

    // Message types
    public static final String TYPE_TEXT  = "text";
    public static final String TYPE_IMAGE = "image";

    private String messageId;
    private String senderId;
    private String text;
    private Timestamp timestamp;
    private String type;          // "text" or "image"
    private String imageUrl;      // Cloudinary URL, null if type is "text"
    private boolean seen;
    private List<String> seenBy;      // list of uids who have seen it
    private List<String> deletedFor;  // list of uids who deleted this message

    // Required empty constructor for Firestore
    public MessageModel() {}

    // Constructor for text messages
    public MessageModel(String messageId, String senderId,
                        String text, Timestamp timestamp) {
        this.messageId  = messageId;
        this.senderId   = senderId;
        this.text       = text;
        this.timestamp  = timestamp;
        this.type       = TYPE_TEXT;
        this.imageUrl   = null;
        this.seen       = false;
    }

    // Constructor for image messages
    public MessageModel(String messageId, String senderId,
                        String imageUrl, Timestamp timestamp,
                        boolean isImage) {
        this.messageId  = messageId;
        this.senderId   = senderId;
        this.text       = "";
        this.timestamp  = timestamp;
        this.type       = TYPE_IMAGE;
        this.imageUrl   = imageUrl;
        this.seen       = false;
    }



    // Getters
    public String getMessageId()        { return messageId; }
    public String getSenderId()         { return senderId; }
    public String getText()             { return text; }
    public Timestamp getTimestamp()     { return timestamp; }
    public String getType()             { return type; }
    public String getImageUrl()         { return imageUrl; }
    public boolean isSeen()             { return seen; }
    public List<String> getSeenBy()     { return seenBy; }
    public List<String> getDeletedFor() { return deletedFor; }

    // Setters
    public void setMessageId(String messageId)         { this.messageId = messageId; }
    public void setSenderId(String senderId)           { this.senderId = senderId; }
    public void setText(String text)                   { this.text = text; }
    public void setTimestamp(Timestamp timestamp)      { this.timestamp = timestamp; }
    public void setType(String type)                   { this.type = type; }
    public void setImageUrl(String imageUrl)           { this.imageUrl = imageUrl; }
    public void setSeen(boolean seen)                  { this.seen = seen; }
    public void setSeenBy(List<String> seenBy)         { this.seenBy = seenBy; }
    public void setDeletedFor(List<String> deletedFor) { this.deletedFor = deletedFor; }

    // ─── Helper Methods ───────────────────────────────────────

    /** Check if this is a text message */
    public boolean isTextMessage() { return TYPE_TEXT.equals(type); }

    /** Check if this is an image message */
    public boolean isImageMessage() { return TYPE_IMAGE.equals(type); }

    /** Check if a specific user has seen this message */
    public boolean isSeenBy(String uid) {
        return seenBy != null && seenBy.contains(uid);
    }

    /** Check if a specific user deleted this message */
    public boolean isDeletedFor(String uid) {
        return deletedFor != null && deletedFor.contains(uid);
    }

    /**
     * Returns display text for chat list preview.
     * e.g. "📷 Photo" for images, actual text for text messages.
     */
    public static final String TYPE_FILE = "file";

    private String fileName;   // "report.pdf"
    private String fileSize;   // "2.3 MB"
    private String fileType;   // "pdf", "docx", etc.

    // Getters
    public String getFileName() { return fileName; }
    public String getFileSize() { return fileSize; }
    public String getFileType() { return fileType; }

    // Setters
    public void setFileName(String fileName) { this.fileName = fileName; }
    public void setFileSize(String fileSize) { this.fileSize = fileSize; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    // Helper
    public boolean isFileMessage() { return TYPE_FILE.equals(type); }

    public static final String TYPE_VIDEO = "video";

    // Add helper
    public boolean isVideoMessage() { return TYPE_VIDEO.equals(type); }

    // Update getPreviewText()
    public String getPreviewText() {
        if (TYPE_IMAGE.equals(type)) return "📷 Photo";
        if (TYPE_FILE.equals(type))  return "📎 " + (fileName != null ? fileName : "File");
        if (TYPE_VIDEO.equals(type)) return "🎥 Video";
        return text != null ? text : "";
    }

    private String thumbnailUrl;
    private String videoDuration;

    public String getThumbnailUrl()  { return thumbnailUrl; }
    public String getVideoDuration() { return videoDuration; }
    public void setThumbnailUrl(String t)  { this.thumbnailUrl = t; }
    public void setVideoDuration(String d) { this.videoDuration = d; }

    private String fileUrl;

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
}