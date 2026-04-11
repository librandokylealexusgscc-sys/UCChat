package com.example.uccchat;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_VIDEO_SENT     = 7;
    private static final int TYPE_VIDEO_RECEIVED = 8;

    // ── View types ────────────────────────────────────────────
    private static final int TYPE_TEXT_SENT      = 1;
    private static final int TYPE_TEXT_RECEIVED  = 2;
    private static final int TYPE_IMAGE_SENT     = 3;
    private static final int TYPE_IMAGE_RECEIVED = 4;
    private static final int TYPE_FILE_SENT      = 5;
    private static final int TYPE_FILE_RECEIVED  = 6;

    private final Context context;
    private final List<MessageModel> messages = new ArrayList<>();
    private final String myUid;
    private final String otherPhotoUrl;

    private OnMessageLongClickListener longClickListener;
    private OnImageClickListener imageClickListener;

    public interface OnMessageLongClickListener {
        void onLongClick(MessageModel message, View anchorView);
    }

    public interface OnImageClickListener {
        void onImageClick(String imageUrl);
    }

    public MessageAdapter(Context context, String otherPhotoUrl) {
        this.context       = context;
        this.otherPhotoUrl = otherPhotoUrl;
        this.myUid         = FirebaseAuth.getInstance()
                .getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : "";
    }

    public void setOnMessageLongClickListener(OnMessageLongClickListener l) {
        this.longClickListener = l;
    }
    // Add this field at the top of the class
    private String highlightedMessageId = null;

    // Add this method
    public void setHighlightedMessage(String messageId) {
        this.highlightedMessageId = messageId;
        notifyDataSetChanged();
    }

    // Add this method
    public int getPositionByMessageId(String messageId) {
        if (messageId == null || messages == null) return -1;
        for (int i = 0; i < messages.size(); i++) {
            if (messageId.equals(messages.get(i).getMessageId())) {
                return i;
            }
        }
        return -1;
    }

    public void setOnImageClickListener(OnImageClickListener l) {
        this.imageClickListener = l;
    }

    public void setMessages(List<MessageModel> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
        notifyDataSetChanged();
    }

    public List<MessageModel> getMessages() { return messages; }

    // ── View type logic ───────────────────────────────────────

    @Override
    public int getItemViewType(int position) {
        MessageModel msg = messages.get(position);
        boolean isMine   = msg.getSenderId().equals(myUid);

        if (msg.isImageMessage()) {
            return isMine ? TYPE_IMAGE_SENT : TYPE_IMAGE_RECEIVED;
        } else if (msg.isFileMessage()) {
            return isMine ? TYPE_FILE_SENT : TYPE_FILE_RECEIVED;
        } else if (msg.isVideoMessage()) {
            return isMine ? TYPE_VIDEO_SENT : TYPE_VIDEO_RECEIVED;
        } else {
            return isMine ? TYPE_TEXT_SENT : TYPE_TEXT_RECEIVED;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
                                                      int viewType) {
        LayoutInflater inf = LayoutInflater.from(context);
        switch (viewType) {
            case TYPE_TEXT_SENT:
                return new TextSentVH(inf.inflate(
                        R.layout.item_message_sent, parent, false));
            case TYPE_TEXT_RECEIVED:
                return new TextReceivedVH(inf.inflate(
                        R.layout.item_message_received, parent, false));
            case TYPE_IMAGE_SENT:
                return new ImageSentVH(inf.inflate(
                        R.layout.item_message_sent, parent, false));
            case TYPE_IMAGE_RECEIVED:
                return new ImageReceivedVH(inf.inflate(
                        R.layout.item_message_received, parent, false));
            case TYPE_VIDEO_SENT:
                return new VideoSentVH(inf.inflate(
                        R.layout.item_message_video_sent, parent, false));
            case TYPE_VIDEO_RECEIVED:
                return new VideoReceivedVH(inf.inflate(
                        R.layout.item_message_video_received, parent, false));
            case TYPE_FILE_SENT:
                return new FileSentVH(inf.inflate(
                        R.layout.item_message_file_sent, parent, false));
            case TYPE_FILE_RECEIVED:
            default:
                return new FileReceivedVH(inf.inflate(
                        R.layout.item_message_file_received, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        MessageModel msg = messages.get(position);

        boolean isHighlighted = msg.getMessageId() != null
                && msg.getMessageId().equals(highlightedMessageId);

        holder.itemView.setBackgroundColor(
                isHighlighted
                        ? 0x5566BB6A   // semi-transparent light green
                        : android.graphics.Color.TRANSPARENT
        );

        if      (holder instanceof TextSentVH)
            ((TextSentVH) holder).bind(msg);
        else if (holder instanceof TextReceivedVH)
            ((TextReceivedVH) holder).bind(msg);
        else if (holder instanceof ImageSentVH)
            ((ImageSentVH) holder).bind(msg);
        else if (holder instanceof ImageReceivedVH)
            ((ImageReceivedVH) holder).bind(msg);
        else if (holder instanceof FileSentVH)
            ((FileSentVH) holder).bind(msg);
        else if (holder instanceof FileReceivedVH)
            ((FileReceivedVH) holder).bind(msg);
        else if (holder instanceof VideoSentVH)
            ((VideoSentVH) holder).bind(msg);
        else if (holder instanceof VideoReceivedVH)
            ((VideoReceivedVH) holder).bind(msg);
    }

    @Override
    public int getItemCount() { return messages.size(); }

    // ════════════════════════════════════════════════════════
    //  TEXT SENT
    // ════════════════════════════════════════════════════════

    class TextSentVH extends RecyclerView.ViewHolder {
        LinearLayout bubbleContainer;
        TextView tvMessage, tvTimestamp, tvStatus;
        ImageView imgMessage;

        TextSentVH(@NonNull View v) {
            super(v);
            bubbleContainer = v.findViewById(R.id.bubbleContainer);
            tvMessage       = v.findViewById(R.id.tvMessage);
            tvTimestamp     = v.findViewById(R.id.tvTimestamp);
            tvStatus        = v.findViewById(R.id.tvStatus);
            imgMessage      = v.findViewById(R.id.imgMessage);
        }

        void bind(MessageModel msg) {
            imgMessage.setVisibility(View.GONE);
            tvMessage.setVisibility(View.VISIBLE);

            if (msg.isDeletedFor(myUid)) {
                tvMessage.setText("🚫 You deleted this message");
                tvMessage.setAlpha(0.5f);
                tvMessage.setMovementMethod(null);
            } else {
                tvMessage.setText(msg.getText());
                tvMessage.setAlpha(1f);
                // ✅ Makes links tappable
                tvMessage.setMovementMethod(
                        new LinkAndLongClickMovementMethod(bubbleContainer, msg));
                // ✅ Link color white for sent bubbles
                tvMessage.setLinkTextColor(0xFFCCFFCC);
            }

            if (msg.getTimestamp() != null)
                tvTimestamp.setText(formatTime(msg.getTimestamp().toDate()));
            if (tvStatus != null)
                tvStatus.setText(msg.isSeen() ? "Seen" : "Sent");

            bubbleContainer.setOnLongClickListener(v -> {
                if (longClickListener != null && !msg.isDeletedFor(myUid))
                    longClickListener.onLongClick(msg, v);
                return true;
            });
        }
    }

    // ════════════════════════════════════════════════════════
    //  TEXT RECEIVED
    // ════════════════════════════════════════════════════════

    class TextReceivedVH extends RecyclerView.ViewHolder {
        LinearLayout bubbleContainer;
        ImageView imgAvatar;
        TextView tvMessage, tvTimestamp;
        ImageView imgMessage;

        TextReceivedVH(@NonNull View v) {
            super(v);
            bubbleContainer = v.findViewById(R.id.bubbleContainer);
            imgAvatar       = v.findViewById(R.id.imgAvatar);
            tvMessage       = v.findViewById(R.id.tvMessage);
            tvTimestamp     = v.findViewById(R.id.tvTimestamp);
            imgMessage      = v.findViewById(R.id.imgMessage);
        }

        void bind(MessageModel msg) {
            imgMessage.setVisibility(View.GONE);
            tvMessage.setVisibility(View.VISIBLE);

            loadAvatar(imgAvatar);

            if (msg.isDeletedFor(myUid)) {
                tvMessage.setText("🚫 This message was deleted");
                tvMessage.setAlpha(0.5f);
                tvMessage.setMovementMethod(null);
            } else {
                tvMessage.setText(msg.getText());
                tvMessage.setAlpha(1f);
                // ✅ Makes links tappable
                tvMessage.setMovementMethod(
                        new LinkAndLongClickMovementMethod(bubbleContainer, msg));         // ✅ Link color green for received bubbles
                tvMessage.setLinkTextColor(0xFF4CAF50);
            }

            if (msg.getTimestamp() != null)
                tvTimestamp.setText(formatTime(msg.getTimestamp().toDate()));

            bubbleContainer.setOnLongClickListener(v -> {
                if (longClickListener != null && !msg.isDeletedFor(myUid))
                    longClickListener.onLongClick(msg, v);
                return true;
            });
        }
    }

    // ════════════════════════════════════════════════════════
    //  IMAGE SENT
    // ════════════════════════════════════════════════════════

    class ImageSentVH extends RecyclerView.ViewHolder {
        LinearLayout bubbleContainer;
        TextView tvMessage, tvTimestamp, tvStatus;
        ImageView imgMessage;

        ImageSentVH(@NonNull View v) {
            super(v);
            bubbleContainer = v.findViewById(R.id.bubbleContainer);
            tvMessage       = v.findViewById(R.id.tvMessage);
            tvTimestamp     = v.findViewById(R.id.tvTimestamp);
            tvStatus        = v.findViewById(R.id.tvStatus);
            imgMessage      = v.findViewById(R.id.imgMessage);
        }

        void bind(MessageModel msg) {
            tvMessage.setVisibility(View.GONE);
            imgMessage.setVisibility(View.VISIBLE);

            Glide.with(context)
                    .load(msg.getImageUrl())
                    .transform(new RoundedCorners(24))
                    .placeholder(R.drawable.circle_grey_bg)
                    .into(imgMessage);

            imgMessage.setOnClickListener(v -> {
                if (imageClickListener != null)
                    imageClickListener.onImageClick(msg.getImageUrl());
            });

            if (msg.getTimestamp() != null)
                tvTimestamp.setText(formatTime(
                        msg.getTimestamp().toDate()));
            if (tvStatus != null)
                tvStatus.setText(msg.isSeen() ? "Seen" : "Sent");

            bubbleContainer.setOnLongClickListener(v -> {
                if (longClickListener != null)
                    longClickListener.onLongClick(msg, v);
                return true;
            });
        }
    }

    // ════════════════════════════════════════════════════════
    //  IMAGE RECEIVED
    // ════════════════════════════════════════════════════════

    class ImageReceivedVH extends RecyclerView.ViewHolder {
        LinearLayout bubbleContainer;
        ImageView imgAvatar, imgMessage;
        TextView tvMessage, tvTimestamp;

        ImageReceivedVH(@NonNull View v) {
            super(v);
            bubbleContainer = v.findViewById(R.id.bubbleContainer);
            imgAvatar       = v.findViewById(R.id.imgAvatar);
            tvMessage       = v.findViewById(R.id.tvMessage);
            tvTimestamp     = v.findViewById(R.id.tvTimestamp);
            imgMessage      = v.findViewById(R.id.imgMessage);
        }

        void bind(MessageModel msg) {
            tvMessage.setVisibility(View.GONE);
            imgMessage.setVisibility(View.VISIBLE);

            loadAvatar(imgAvatar);

            Glide.with(context)
                    .load(msg.getImageUrl())
                    .transform(new RoundedCorners(24))
                    .placeholder(R.drawable.circle_grey_bg)
                    .into(imgMessage);

            imgMessage.setOnClickListener(v -> {
                if (imageClickListener != null)
                    imageClickListener.onImageClick(msg.getImageUrl());
            });

            if (msg.getTimestamp() != null)
                tvTimestamp.setText(formatTime(
                        msg.getTimestamp().toDate()));

            bubbleContainer.setOnLongClickListener(v -> {
                if (longClickListener != null)
                    longClickListener.onLongClick(msg, v);
                return true;
            });
        }
    }

    // ════════════════════════════════════════════════════════
    //  FILE SENT
    // ════════════════════════════════════════════════════════

    class FileSentVH extends RecyclerView.ViewHolder {
        LinearLayout bubbleContainer;
        TextView tvFileIcon, tvFileName, tvFileSize,
                tvTimestamp, tvStatus;

        FileSentVH(@NonNull View v) {
            super(v);
            bubbleContainer = v.findViewById(R.id.bubbleContainer);
            tvFileIcon      = v.findViewById(R.id.tvFileIcon);
            tvFileName      = v.findViewById(R.id.tvFileName);
            tvFileSize      = v.findViewById(R.id.tvFileSize);
            tvTimestamp     = v.findViewById(R.id.tvTimestamp);
            tvStatus        = v.findViewById(R.id.tvStatus);
        }

        void bind(MessageModel msg) {
            // Set icon based on file type
            tvFileIcon.setText(getFileIcon(msg.getFileType()));
            tvFileName.setText(msg.getFileName() != null
                    ? msg.getFileName() : "File");
            tvFileSize.setText(msg.getFileSize() != null
                    ? msg.getFileSize() : "");

            if (msg.getTimestamp() != null)
                tvTimestamp.setText(formatTime(
                        msg.getTimestamp().toDate()));
            if (tvStatus != null)
                tvStatus.setText(msg.isSeen() ? "Seen" : "Sent");

            // Tap to open file
            bubbleContainer.setOnClickListener(v ->
                    openFile(msg.getFileUrl(), msg.getFileName()));

            bubbleContainer.setOnLongClickListener(v -> {
                if (longClickListener != null)
                    longClickListener.onLongClick(msg, v);
                return true;
            });
        }
    }

    // ════════════════════════════════════════════════════════
    //  FILE RECEIVED
    // ════════════════════════════════════════════════════════

    class FileReceivedVH extends RecyclerView.ViewHolder {
        LinearLayout bubbleContainer;
        ImageView imgAvatar;
        TextView tvFileIcon, tvFileName, tvFileSize, tvTimestamp;

        FileReceivedVH(@NonNull View v) {
            super(v);
            bubbleContainer = v.findViewById(R.id.bubbleContainer);
            imgAvatar       = v.findViewById(R.id.imgAvatar);
            tvFileIcon      = v.findViewById(R.id.tvFileIcon);
            tvFileName      = v.findViewById(R.id.tvFileName);
            tvFileSize      = v.findViewById(R.id.tvFileSize);
            tvTimestamp     = v.findViewById(R.id.tvTimestamp);
        }

        void bind(MessageModel msg) {
            loadAvatar(imgAvatar);

            tvFileIcon.setText(getFileIcon(msg.getFileType()));
            tvFileName.setText(msg.getFileName() != null
                    ? msg.getFileName() : "File");
            tvFileSize.setText(msg.getFileSize() != null
                    ? msg.getFileSize() : "");

            if (msg.getTimestamp() != null)
                tvTimestamp.setText(formatTime(
                        msg.getTimestamp().toDate()));

            // Tap to open file
            bubbleContainer.setOnClickListener(v ->
                    openFile(msg.getFileUrl(), msg.getFileName()));

            bubbleContainer.setOnLongClickListener(v -> {
                if (longClickListener != null)
                    longClickListener.onLongClick(msg, v);
                return true;
            });
        }
    }

    // ════════════════════════════════════════════════════════
//  VIDEO SENT
// ════════════════════════════════════════════════════════

    class VideoSentVH extends RecyclerView.ViewHolder {
        android.widget.FrameLayout bubbleContainer;
        ImageView imgThumbnail;
        TextView tvDuration, tvTimestamp, tvStatus;

        VideoSentVH(@NonNull View v) {
            super(v);
            bubbleContainer = v.findViewById(R.id.bubbleContainer);
            imgThumbnail    = v.findViewById(R.id.imgThumbnail);
            tvDuration      = v.findViewById(R.id.tvDuration);
            tvTimestamp     = v.findViewById(R.id.tvTimestamp);
            tvStatus        = v.findViewById(R.id.tvStatus);
        }

        void bind(MessageModel msg) {
            // Load thumbnail if available
            if (msg.getThumbnailUrl() != null
                    && !msg.getThumbnailUrl().isEmpty()) {
                Glide.with(context)
                        .load(msg.getThumbnailUrl())
                        .centerCrop()
                        .placeholder(R.drawable.circle_grey_bg)
                        .into(imgThumbnail);
            } else if (msg.getFileUrl() != null) {
                // Use Cloudinary's auto-generated thumbnail
                String thumbUrl = msg.getFileUrl()
                        .replace("/upload/", "/upload/w_400,h_300,c_fill/")
                        .replace(".mp4", ".jpg")
                        .replace(".mov", ".jpg")
                        .replace(".avi", ".jpg");
                Glide.with(context)
                        .load(thumbUrl)
                        .centerCrop()
                        .placeholder(R.drawable.circle_grey_bg)
                        .into(imgThumbnail);
            }

            // Duration
            if (msg.getVideoDuration() != null
                    && !msg.getVideoDuration().isEmpty()) {
                tvDuration.setText(msg.getVideoDuration());
                tvDuration.setVisibility(View.VISIBLE);
            } else {
                tvDuration.setVisibility(View.GONE);
            }

            if (msg.getTimestamp() != null)
                tvTimestamp.setText(formatTime(
                        msg.getTimestamp().toDate()));
            if (tvStatus != null)
                tvStatus.setText(msg.isSeen() ? "Seen" : "Sent");

            // Tap to play
            bubbleContainer.setOnClickListener(v ->
                    playVideo(msg.getFileUrl()));

            // Long press
            bubbleContainer.setOnLongClickListener(v -> {
                if (longClickListener != null)
                    longClickListener.onLongClick(msg, v);
                return true;
            });
        }
    }

// ════════════════════════════════════════════════════════
//  VIDEO RECEIVED
// ════════════════════════════════════════════════════════

    class VideoReceivedVH extends RecyclerView.ViewHolder {
        android.widget.FrameLayout bubbleContainer;
        ImageView imgAvatar, imgThumbnail;
        TextView tvDuration, tvTimestamp;

        VideoReceivedVH(@NonNull View v) {
            super(v);
            bubbleContainer = v.findViewById(R.id.bubbleContainer);
            imgAvatar       = v.findViewById(R.id.imgAvatar);
            imgThumbnail    = v.findViewById(R.id.imgThumbnail);
            tvDuration      = v.findViewById(R.id.tvDuration);
            tvTimestamp     = v.findViewById(R.id.tvTimestamp);
        }

        void bind(MessageModel msg) {
            loadAvatar(imgAvatar);

            // Load thumbnail
            if (msg.getThumbnailUrl() != null
                    && !msg.getThumbnailUrl().isEmpty()) {
                Glide.with(context)
                        .load(msg.getThumbnailUrl())
                        .centerCrop()
                        .placeholder(R.drawable.circle_grey_bg)
                        .into(imgThumbnail);
            } else if (msg.getFileUrl() != null) {
                String thumbUrl = msg.getFileUrl()
                        .replace("/upload/", "/upload/w_400,h_300,c_fill/")
                        .replace(".mp4", ".jpg")
                        .replace(".mov", ".jpg")
                        .replace(".avi", ".jpg");
                Glide.with(context)
                        .load(thumbUrl)
                        .centerCrop()
                        .placeholder(R.drawable.circle_grey_bg)
                        .into(imgThumbnail);
            }

            // Duration
            if (msg.getVideoDuration() != null
                    && !msg.getVideoDuration().isEmpty()) {
                tvDuration.setText(msg.getVideoDuration());
                tvDuration.setVisibility(View.VISIBLE);
            } else {
                tvDuration.setVisibility(View.GONE);
            }

            if (msg.getTimestamp() != null)
                tvTimestamp.setText(formatTime(
                        msg.getTimestamp().toDate()));

            // Tap to play
            bubbleContainer.setOnClickListener(v ->
                    playVideo(msg.getFileUrl()));

            // Long press
            bubbleContainer.setOnLongClickListener(v -> {
                if (longClickListener != null)
                    longClickListener.onLongClick(msg, v);
                return true;
            });
        }
    }

    private void playVideo(String videoUrl) {
        if (videoUrl == null || videoUrl.isEmpty()) {
            Toast.makeText(context,
                    "Video not available.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(videoUrl), "video/*");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            // Fallback — open in browser
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse(videoUrl));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    // ════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════

    private void loadAvatar(ImageView imgAvatar) {
        if (otherPhotoUrl != null && !otherPhotoUrl.isEmpty()) {
            Glide.with(context)
                    .load(otherPhotoUrl)
                    .transform(new CircleCrop())
                    .placeholder(R.drawable.circle_grey_bg)
                    .into(imgAvatar);
        } else {
            imgAvatar.setImageResource(R.drawable.circle_grey_bg);
        }
    }

    /** Open file in external app (browser/PDF viewer/etc) */
    private void openFile(String fileUrl, String fileName) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            Toast.makeText(context,
                    "File not available.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(fileUrl));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context,
                    "No app found to open this file.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    /** Returns emoji icon based on file extension */
    private String getFileIcon(String fileType) {
        if (fileType == null) return "📄";
        switch (fileType.toLowerCase()) {
            case "pdf":  return "📕";
            case "doc":
            case "docx": return "📘";
            case "xls":
            case "xlsx": return "📗";
            case "ppt":
            case "pptx": return "📙";
            case "zip":
            case "rar":  return "🗜️";
            case "txt":  return "📝";
            case "mp3":
            case "wav":  return "🎵";
            case "mp4":
            case "mov":  return "🎬";
            default:     return "📄";
        }
    }

    private String formatTime(java.util.Date date) {
        return new SimpleDateFormat("h:mm a",
                Locale.getDefault()).format(date);
    }

    /**
     * Allows both link clicks AND long press to work together.
     */
    private class LinkAndLongClickMovementMethod
            extends android.text.method.LinkMovementMethod {

        private final View longPressTarget;
        private final MessageModel msg;

        LinkAndLongClickMovementMethod(View target, MessageModel msg) {
            this.longPressTarget = target;
            this.msg             = msg;
        }

        @Override
        public boolean onTouchEvent(android.widget.TextView widget,
                                    android.text.Spannable buffer,
                                    android.view.MotionEvent event) {
            // If no link at touch point → let long press handle it
            int action = event.getAction();
            if (action == android.view.MotionEvent.ACTION_UP
                    || action == android.view.MotionEvent.ACTION_DOWN) {

                int x = (int) event.getX() - widget.getTotalPaddingLeft()
                        + widget.getScrollX();
                int y = (int) event.getY() - widget.getTotalPaddingTop()
                        + widget.getScrollY();

                android.text.Layout layout = widget.getLayout();
                int line   = layout.getLineForVertical(y);
                int offset = layout.getOffsetForHorizontal(line, x);

                android.text.style.ClickableSpan[] links =
                        buffer.getSpans(offset, offset,
                                android.text.style.ClickableSpan.class);

                if (links.length == 0) {
                    // No link here — pass to default (enables long press)
                    return false;
                }
            }
            return super.onTouchEvent(widget, buffer, event);
        }
    }
}