package com.example.uccchat;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.util.Linkify;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
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

    // ── View types ────────────────────────────────────────────────
    private static final int TYPE_TEXT_SENT      = 1;
    private static final int TYPE_TEXT_RECEIVED  = 2;
    private static final int TYPE_IMAGE_SENT     = 3;
    private static final int TYPE_IMAGE_RECEIVED = 4;
    private static final int TYPE_FILE_SENT      = 5;
    private static final int TYPE_FILE_RECEIVED  = 6;
    private static final int TYPE_VIDEO_SENT     = 7;
    private static final int TYPE_VIDEO_RECEIVED = 8;
    private static final int TYPE_DATE_HEADER = 99;
    private final Context context;
    private final List<MessageModel> messages = new ArrayList<>();
    private final String myUid;
    private final String otherPhotoUrl;

    private String highlightedMessageId = null;

    // ── Listeners ─────────────────────────────────────────────────
    private OnMessageLongClickListener longClickListener;
    private OnImageClickListener       imageClickListener;
    private OnImageLongClickListener   imageLongClickListener;

    public interface OnMessageLongClickListener {
        void onLongClick(MessageModel message, View anchorView);
    }

    public interface OnImageClickListener {
        void onImageClick(MessageModel message, String imageUrl);
    }

    public interface OnImageLongClickListener {
        /**
         * @param isMine  true if the message belongs to the current user —
         *                use this in your Activity to decide whether to show "Delete".
         */
        void onImageLongClick(MessageModel message, View anchorView, boolean isMine);
    }

    public MessageAdapter(Context context, String otherPhotoUrl) {
        this.context       = context;
        this.otherPhotoUrl = otherPhotoUrl;
        this.myUid         = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
    }

    public void setOnMessageLongClickListener(OnMessageLongClickListener l)   { this.longClickListener = l; }
    public void setOnImageClickListener(OnImageClickListener l)               { this.imageClickListener = l; }
    public void setOnImageLongClickListener(OnImageLongClickListener l)       { this.imageLongClickListener = l; }

    // Cache of uid → photoUrl for group chats
    private final java.util.Map<String, String> memberPhotos
            = new java.util.HashMap<>();

    private boolean isGroupChat = false;

    public void setGroupChat(boolean isGroup) {
        this.isGroupChat = isGroup;
    }

    public void setMemberPhotos(java.util.Map<String, String> photos) {
        memberPhotos.clear();
        if (photos != null) memberPhotos.putAll(photos);
    }

    public void setHighlightedMessage(String messageId) {
        this.highlightedMessageId = messageId;
        notifyDataSetChanged();
    }

    public int getPositionByMessageId(String messageId) {
        if (messageId == null) return -1;
        for (int i = 0; i < messages.size(); i++) {
            if (messageId.equals(messages.get(i).getMessageId())) return i;
        }
        return -1;
    }

    public void setMessages(List<MessageModel> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
        notifyDataSetChanged();
    }

    public List<MessageModel> getMessages() { return messages; }

    // ── View type logic ───────────────────────────────────────────
    @Override
    public int getItemViewType(int position) {
        // ✅ Back to original — no more TYPE_DATE_HEADER hijacking
        MessageModel msg = messages.get(position);
        boolean isMine = msg.getSenderId() != null
                && msg.getSenderId().equals(myUid);
        String type = msg.getType() != null
                ? msg.getType() : MessageModel.TYPE_TEXT;
        switch (type) {
            case MessageModel.TYPE_IMAGE:
                return isMine ? TYPE_IMAGE_SENT : TYPE_IMAGE_RECEIVED;
            case MessageModel.TYPE_VIDEO:
                return isMine ? TYPE_VIDEO_SENT : TYPE_VIDEO_RECEIVED;
            case MessageModel.TYPE_FILE:
                return isMine ? TYPE_FILE_SENT : TYPE_FILE_RECEIVED;
            default:
                return isMine ? TYPE_TEXT_SENT : TYPE_TEXT_RECEIVED;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(context);
        switch (viewType) {
            case TYPE_TEXT_SENT:      return new TextSentVH(inf.inflate(R.layout.item_message_sent, parent, false));
            case TYPE_TEXT_RECEIVED:  return new TextReceivedVH(inf.inflate(R.layout.item_message_received, parent, false));
            case TYPE_IMAGE_SENT:     return new ImageSentVH(inf.inflate(R.layout.item_message_sent, parent, false));
            case TYPE_IMAGE_RECEIVED: return new ImageReceivedVH(inf.inflate(R.layout.item_message_received, parent, false));
            case TYPE_VIDEO_SENT:     return new VideoSentVH(inf.inflate(R.layout.item_message_video_sent, parent, false));
            case TYPE_VIDEO_RECEIVED: return new VideoReceivedVH(inf.inflate(R.layout.item_message_video_received, parent, false));
            case TYPE_FILE_SENT:      return new FileSentVH(inf.inflate(R.layout.item_message_file_sent, parent, false));
            case TYPE_FILE_RECEIVED:
            default:                  return new FileReceivedVH(inf.inflate(R.layout.item_message_file_received, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        MessageModel msg = messages.get(position);

        // ✅ Date separator — works for ALL view types
        View dateSeparator = holder.itemView.findViewById(R.id.tvDateSeparator);
        if (dateSeparator != null) {
            boolean showDate = position == 0 ||
                    !isSameDay(messages.get(position - 1).getTimestamp(),
                            msg.getTimestamp());
            dateSeparator.setVisibility(showDate ? View.VISIBLE : View.GONE);
            if (showDate) {
                ((TextView) dateSeparator).setText(
                        formatDateHeader(msg.getTimestamp()));
            }
        }

        if      (holder instanceof TextSentVH)      ((TextSentVH) holder).bind(msg);
        else if (holder instanceof TextReceivedVH)  ((TextReceivedVH) holder).bind(msg);
        else if (holder instanceof ImageSentVH)     ((ImageSentVH) holder).bind(msg);
        else if (holder instanceof ImageReceivedVH) ((ImageReceivedVH) holder).bind(msg);
        else if (holder instanceof FileSentVH)      ((FileSentVH) holder).bind(msg);
        else if (holder instanceof FileReceivedVH)  ((FileReceivedVH) holder).bind(msg);
        else if (holder instanceof VideoSentVH)     ((VideoSentVH) holder).bind(msg);
        else if (holder instanceof VideoReceivedVH) ((VideoReceivedVH) holder).bind(msg);
    }

    @Override
    public int getItemCount() { return messages.size(); }

    //DATE PUSH NOTIFICATION

    private String formatDateHeader(com.google.firebase.Timestamp timestamp) {
        if (timestamp == null) return "";
        java.util.Date date = timestamp.toDate();
        java.util.Calendar msgCal = java.util.Calendar.getInstance();
        msgCal.setTime(date);
        java.util.Calendar today = java.util.Calendar.getInstance();
        java.util.Calendar yesterday = java.util.Calendar.getInstance();
        yesterday.add(java.util.Calendar.DAY_OF_YEAR, -1);

        if (msgCal.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR)
                && msgCal.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR)) {
            return "Today";
        } else if (msgCal.get(java.util.Calendar.YEAR) == yesterday.get(java.util.Calendar.YEAR)
                && msgCal.get(java.util.Calendar.DAY_OF_YEAR) == yesterday.get(java.util.Calendar.DAY_OF_YEAR)) {
            return "Yesterday";
        } else {
            return new java.text.SimpleDateFormat("MMMM d, yyyy",
                    java.util.Locale.getDefault()).format(date);
        }
    }

    private boolean isSameDay(com.google.firebase.Timestamp t1, com.google.firebase.Timestamp t2) {
        if (t1 == null || t2 == null) return false;
        java.util.Calendar c1 = java.util.Calendar.getInstance();
        java.util.Calendar c2 = java.util.Calendar.getInstance();
        c1.setTime(t1.toDate());
        c2.setTime(t2.toDate());
        return c1.get(java.util.Calendar.YEAR) == c2.get(java.util.Calendar.YEAR)
                && c1.get(java.util.Calendar.DAY_OF_YEAR) == c2.get(java.util.Calendar.DAY_OF_YEAR);
    }

    // ════════════════════════════════════════════════════════════
    //  HELPER — forwarded label
    // ════════════════════════════════════════════════════════════
    private void bindForwardedLabel(View itemView, MessageModel msg) {
        TextView tvForwarded = itemView.findViewById(R.id.tvForwardedLabel);
        if (tvForwarded == null) return;
        tvForwarded.setVisibility(msg.isForwarded() ? View.VISIBLE : View.GONE);
    }

    // ════════════════════════════════════════════════════════════
    //  HELPER — reply preview bar
    // ════════════════════════════════════════════════════════════
    private void bindReplyPreview(View itemView, MessageModel msg) {
        LinearLayout replyBar      = itemView.findViewById(R.id.replyPreviewBar);
        TextView     tvReplySender = itemView.findViewById(R.id.tvReplySender);
        TextView     tvReplyText   = itemView.findViewById(R.id.tvReplyText);

        if (replyBar == null) return;

        if (msg.isDeleted() || !msg.hasReply()) {
            replyBar.setVisibility(View.GONE);
            return;
        }

        replyBar.setVisibility(View.VISIBLE);
        if (tvReplySender != null) {
            tvReplySender.setText(msg.getReplyToSender() != null
                    ? msg.getReplyToSender() : "Message");
        }
        if (tvReplyText != null) {
            tvReplyText.setText(msg.getReplyToText() != null
                    ? msg.getReplyToText() : "");
        }
    }

    // ════════════════════════════════════════════════════════════
    //  HELPER — GestureDetector for long press + URL tap
    // ════════════════════════════════════════════════════════════
    private void attachGestures(View bubble, TextView tvMessage, MessageModel msg, boolean isText) {
        GestureDetector gestureDetector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public void onLongPress(MotionEvent e) {
                        if (msg.isDeleted()) return;
                        if (longClickListener != null)
                            longClickListener.onLongClick(msg, bubble);
                    }

                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent e) {
                        if (tvMessage != null && isText && msg.getText() != null) {
                            android.text.Layout layout = tvMessage.getLayout();
                            if (layout != null) {
                                int x = (int)(e.getX() - tvMessage.getTotalPaddingLeft() + tvMessage.getScrollX());
                                int y = (int)(e.getY() - tvMessage.getTotalPaddingTop() + tvMessage.getScrollY());
                                int line = layout.getLineForVertical(y);
                                int offset = layout.getOffsetForHorizontal(line, x);
                                android.text.Spannable buffer = (android.text.Spannable) tvMessage.getText();
                                android.text.style.URLSpan[] spans =
                                        buffer.getSpans(offset, offset, android.text.style.URLSpan.class);
                                if (spans.length > 0) {
                                    Intent intent = new Intent(Intent.ACTION_VIEW,
                                            Uri.parse(spans[0].getURL()));
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    context.startActivity(intent);
                                    return true;
                                }
                            }
                        }
                        return false;
                    }

                    @Override
                    public boolean onDown(MotionEvent e) { return true; }
                });

        View.OnTouchListener touchListener = (v, event) -> {
            gestureDetector.onTouchEvent(event);
            return true;
        };

        bubble.setOnTouchListener(touchListener);
        if (tvMessage != null) tvMessage.setOnTouchListener(touchListener);

        bubble.setOnLongClickListener(v -> {
            if (!msg.isDeleted() && longClickListener != null)
                longClickListener.onLongClick(msg, v);
            return true;
        });
    }

    // ════════════════════════════════════════════════════════════
    //  HELPER — fire image long-click with isMine flag
    // ════════════════════════════════════════════════════════════
    private void fireImageLongClick(MessageModel msg, View anchor) {
        if (imageLongClickListener != null) {
            boolean isMine = msg.getSenderId().equals(myUid);
            imageLongClickListener.onImageLongClick(msg, anchor, isMine);
        }
    }

    // ════════════════════════════════════════════════════════════
    //  TEXT SENT
    // ════════════════════════════════════════════════════════════
    class TextSentVH extends RecyclerView.ViewHolder {
        LinearLayout bubbleContainer;
        TextView tvForwardedLabel, tvMessage, tvTimestamp, tvStatus;
        ImageView imgMessage;

        TextSentVH(@NonNull View v) {
            super(v);
            bubbleContainer  = v.findViewById(R.id.bubbleContainer);
            tvForwardedLabel = v.findViewById(R.id.tvForwardedLabel);
            tvMessage        = v.findViewById(R.id.tvMessage);
            tvTimestamp      = v.findViewById(R.id.tvTimestamp);
            tvStatus         = v.findViewById(R.id.tvStatus);
            imgMessage       = v.findViewById(R.id.imgMessage);
        }

        void bind(MessageModel msg) {
            imgMessage.setVisibility(View.GONE);
            tvMessage.setVisibility(View.VISIBLE);
            bindForwardedLabel(itemView, msg);
            bindReplyPreview(itemView, msg);

            if (msg.isDeleted()) {
                tvMessage.setText("🚫 You deleted this message");
                tvMessage.setAlpha(0.5f);
                tvMessage.setTypeface(null, android.graphics.Typeface.ITALIC);
                tvMessage.setAutoLinkMask(0);
                tvMessage.setMovementMethod(null);
                tvMessage.setOnTouchListener(null);
                bubbleContainer.setOnTouchListener(null);
                bubbleContainer.setOnLongClickListener(null);
            } else {
                tvMessage.setText(msg.getText());
                tvMessage.setAlpha(1f);
                tvMessage.setTypeface(null, android.graphics.Typeface.NORMAL);
                Linkify.addLinks(tvMessage, Linkify.WEB_URLS);
                tvMessage.setLinkTextColor(0xFFCCFFCC);
                attachGestures(bubbleContainer, tvMessage, msg, true);
            }

            if (msg.getTimestamp() != null)
                tvTimestamp.setText(formatTime(msg.getTimestamp().toDate()));
            if (tvStatus != null)
                tvStatus.setText(msg.isSeen() ? "Seen" : "✓ Sent");
        }
    }

    // ════════════════════════════════════════════════════════════
    //  TEXT RECEIVED
    // ════════════════════════════════════════════════════════════
    class TextReceivedVH extends RecyclerView.ViewHolder {
        LinearLayout bubbleContainer;
        ImageView imgAvatar, imgMessage;
        TextView tvForwardedLabel, tvMessage, tvTimestamp;

        TextReceivedVH(@NonNull View v) {
            super(v);
            bubbleContainer  = v.findViewById(R.id.bubbleContainer);
            imgAvatar        = v.findViewById(R.id.imgAvatar);
            tvForwardedLabel = v.findViewById(R.id.tvForwardedLabel);
            tvMessage        = v.findViewById(R.id.tvMessage);
            tvTimestamp      = v.findViewById(R.id.tvTimestamp);
            imgMessage       = v.findViewById(R.id.imgMessage);
        }

        void bind(MessageModel msg) {
            imgMessage.setVisibility(View.GONE);
            tvMessage.setVisibility(View.VISIBLE);
            loadAvatar(imgAvatar, msg.getSenderId()); // ✅ pass senderId
            bindForwardedLabel(itemView, msg);
            bindReplyPreview(itemView, msg);

            if (msg.isDeleted()) {
                tvMessage.setText("🚫 This message was deleted");
                tvMessage.setAlpha(0.5f);
                tvMessage.setTypeface(null, android.graphics.Typeface.ITALIC);
                tvMessage.setAutoLinkMask(0);
                tvMessage.setMovementMethod(null);
                tvMessage.setOnTouchListener(null);
                bubbleContainer.setOnTouchListener(null);
                bubbleContainer.setOnLongClickListener(null);
            } else {
                tvMessage.setText(msg.getText());
                tvMessage.setAlpha(1f);
                tvMessage.setTypeface(null, android.graphics.Typeface.NORMAL);
                Linkify.addLinks(tvMessage, Linkify.WEB_URLS);
                tvMessage.setLinkTextColor(0xFF4CAF50);
                attachGestures(bubbleContainer, tvMessage, msg, true);
            }

            if (msg.getTimestamp() != null)
                tvTimestamp.setText(formatTime(msg.getTimestamp().toDate()));
        }
    }

    // ════════════════════════════════════════════════════════════
    //  IMAGE SENT
    // ════════════════════════════════════════════════════════════
    class ImageSentVH extends RecyclerView.ViewHolder {
        LinearLayout bubbleContainer;
        TextView tvForwardedLabel, tvMessage, tvTimestamp, tvStatus;
        ImageView imgMessage;

        ImageSentVH(@NonNull View v) {
            super(v);
            bubbleContainer  = v.findViewById(R.id.bubbleContainer);
            tvForwardedLabel = v.findViewById(R.id.tvForwardedLabel);
            tvMessage        = v.findViewById(R.id.tvMessage);
            tvTimestamp      = v.findViewById(R.id.tvTimestamp);
            tvStatus         = v.findViewById(R.id.tvStatus);
            imgMessage       = v.findViewById(R.id.imgMessage);
        }

        void bind(MessageModel msg) {
            bindForwardedLabel(itemView, msg);
            bindReplyPreview(itemView, msg);

            if (msg.isDeleted()) {
                imgMessage.setVisibility(View.GONE);
                tvMessage.setVisibility(View.VISIBLE);
                tvMessage.setText("🚫 You deleted this message");
                tvMessage.setAlpha(0.5f);
                tvMessage.setTypeface(null, android.graphics.Typeface.ITALIC);
                bubbleContainer.setOnClickListener(null);
                bubbleContainer.setOnLongClickListener(null);
            } else {
                tvMessage.setVisibility(View.GONE);
                imgMessage.setVisibility(View.VISIBLE);

                Glide.with(context).load(msg.getImageUrl())
                        .transform(new RoundedCorners(24))
                        .placeholder(R.drawable.circle_grey_bg)
                        .into(imgMessage);

                imgMessage.setOnClickListener(v -> {
                    if (imageClickListener != null)
                        imageClickListener.onImageClick(msg, msg.getImageUrl());
                });
                imgMessage.setOnLongClickListener(v -> {
                    fireImageLongClick(msg, v);
                    return true;
                });
                bubbleContainer.setOnLongClickListener(v -> {
                    fireImageLongClick(msg, v);
                    return true;
                });
            }

            if (msg.getTimestamp() != null)
                tvTimestamp.setText(formatTime(msg.getTimestamp().toDate()));
            if (tvStatus != null)
                tvStatus.setText(msg.isSeen() ? "Seen" : "✓ Sent");
        }
    }

    // ════════════════════════════════════════════════════════════
    //  IMAGE RECEIVED
    // ════════════════════════════════════════════════════════════
    class ImageReceivedVH extends RecyclerView.ViewHolder {
        LinearLayout bubbleContainer;
        ImageView imgAvatar, imgMessage;
        TextView tvForwardedLabel, tvMessage, tvTimestamp;

        ImageReceivedVH(@NonNull View v) {
            super(v);
            bubbleContainer  = v.findViewById(R.id.bubbleContainer);
            imgAvatar        = v.findViewById(R.id.imgAvatar);
            tvForwardedLabel = v.findViewById(R.id.tvForwardedLabel);
            tvMessage        = v.findViewById(R.id.tvMessage);
            tvTimestamp      = v.findViewById(R.id.tvTimestamp);
            imgMessage       = v.findViewById(R.id.imgMessage);
        }

        void bind(MessageModel msg) {
            loadAvatar(imgAvatar, msg.getSenderId()); // ✅ pass senderId
            bindForwardedLabel(itemView, msg);
            bindReplyPreview(itemView, msg);

            if (msg.isDeleted()) {
                imgMessage.setVisibility(View.GONE);
                tvMessage.setVisibility(View.VISIBLE);
                tvMessage.setText("🚫 This message was deleted");
                tvMessage.setAlpha(0.5f);
                tvMessage.setTypeface(null, android.graphics.Typeface.ITALIC);
                bubbleContainer.setOnClickListener(null);
                bubbleContainer.setOnLongClickListener(null);
            } else {
                tvMessage.setVisibility(View.GONE);
                imgMessage.setVisibility(View.VISIBLE);

                Glide.with(context).load(msg.getImageUrl())
                        .transform(new RoundedCorners(24))
                        .placeholder(R.drawable.circle_grey_bg)
                        .into(imgMessage);

                imgMessage.setOnClickListener(v -> {
                    if (imageClickListener != null)
                        imageClickListener.onImageClick(msg, msg.getImageUrl());
                });
                imgMessage.setOnLongClickListener(v -> {
                    fireImageLongClick(msg, v);
                    return true;
                });
                bubbleContainer.setOnLongClickListener(v -> {
                    fireImageLongClick(msg, v);
                    return true;
                });
            }

            if (msg.getTimestamp() != null)
                tvTimestamp.setText(formatTime(msg.getTimestamp().toDate()));
        }
    }

    // ════════════════════════════════════════════════════════════
    //  FILE SENT
    // ════════════════════════════════════════════════════════════
    class FileSentVH extends RecyclerView.ViewHolder {
        LinearLayout bubbleContainer;
        TextView tvForwardedLabel, tvFileIcon, tvFileName, tvFileSize, tvTimestamp, tvStatus;

        FileSentVH(@NonNull View v) {
            super(v);
            bubbleContainer  = v.findViewById(R.id.bubbleContainer);
            tvForwardedLabel = v.findViewById(R.id.tvForwardedLabel);
            tvFileIcon       = v.findViewById(R.id.tvFileIcon);
            tvFileName       = v.findViewById(R.id.tvFileName);
            tvFileSize       = v.findViewById(R.id.tvFileSize);
            tvTimestamp      = v.findViewById(R.id.tvTimestamp);
            tvStatus         = v.findViewById(R.id.tvStatus);
        }

        void bind(MessageModel msg) {
            bindForwardedLabel(itemView, msg);
            bindReplyPreview(itemView, msg);

            if (msg.isDeleted()) {
                tvFileIcon.setText("🚫");
                tvFileName.setText("You deleted this message");
                tvFileName.setAlpha(0.5f);
                tvFileName.setTypeface(null, android.graphics.Typeface.ITALIC);
                tvFileSize.setVisibility(View.GONE);
                bubbleContainer.setOnClickListener(null);
                bubbleContainer.setOnLongClickListener(null);
            } else {
                tvFileIcon.setText(getFileIcon(msg.getFileType()));
                tvFileName.setText(msg.getFileName() != null ? msg.getFileName() : "File");
                tvFileName.setAlpha(1f);
                tvFileName.setTypeface(null, android.graphics.Typeface.NORMAL);
                tvFileSize.setVisibility(View.VISIBLE);
                tvFileSize.setText(msg.getFileSize() != null ? msg.getFileSize() : "");
                bubbleContainer.setOnClickListener(v -> openFile(msg.getFileUrl(), msg.getFileName()));
                bubbleContainer.setOnLongClickListener(v -> {
                    fireImageLongClick(msg, v);
                    return true;
                });
            }

            if (msg.getTimestamp() != null)
                tvTimestamp.setText(formatTime(msg.getTimestamp().toDate()));
            if (tvStatus != null)
                tvStatus.setText(msg.isSeen() ? "Seen" : "✓ Sent");
        }
    }

    // ════════════════════════════════════════════════════════════
    //  FILE RECEIVED
    // ════════════════════════════════════════════════════════════
    class FileReceivedVH extends RecyclerView.ViewHolder {
        LinearLayout bubbleContainer;
        ImageView imgAvatar;
        TextView tvForwardedLabel, tvFileIcon, tvFileName, tvFileSize, tvTimestamp;

        FileReceivedVH(@NonNull View v) {
            super(v);
            bubbleContainer  = v.findViewById(R.id.bubbleContainer);
            imgAvatar        = v.findViewById(R.id.imgAvatar);
            tvForwardedLabel = v.findViewById(R.id.tvForwardedLabel);
            tvFileIcon       = v.findViewById(R.id.tvFileIcon);
            tvFileName       = v.findViewById(R.id.tvFileName);
            tvFileSize       = v.findViewById(R.id.tvFileSize);
            tvTimestamp      = v.findViewById(R.id.tvTimestamp);
        }

        void bind(MessageModel msg) {
            loadAvatar(imgAvatar, msg.getSenderId()); // ✅ pass senderId
            bindForwardedLabel(itemView, msg);
            bindReplyPreview(itemView, msg);

            if (msg.isDeleted()) {
                tvFileIcon.setText("🚫");
                tvFileName.setText("This message was deleted");
                tvFileName.setAlpha(0.5f);
                tvFileName.setTypeface(null, android.graphics.Typeface.ITALIC);
                tvFileSize.setVisibility(View.GONE);
                bubbleContainer.setOnClickListener(null);
                bubbleContainer.setOnLongClickListener(null);
            } else {
                tvFileIcon.setText(getFileIcon(msg.getFileType()));
                tvFileName.setText(msg.getFileName() != null ? msg.getFileName() : "File");
                tvFileName.setAlpha(1f);
                tvFileName.setTypeface(null, android.graphics.Typeface.NORMAL);
                tvFileSize.setVisibility(View.VISIBLE);
                tvFileSize.setText(msg.getFileSize() != null ? msg.getFileSize() : "");
                bubbleContainer.setOnClickListener(v -> openFile(msg.getFileUrl(), msg.getFileName()));
                bubbleContainer.setOnLongClickListener(v -> {
                    fireImageLongClick(msg, v);
                    return true;
                });
            }

            if (msg.getTimestamp() != null)
                tvTimestamp.setText(formatTime(msg.getTimestamp().toDate()));
        }
    }

    // ════════════════════════════════════════════════════════════
    //  VIDEO SENT
    // ════════════════════════════════════════════════════════════
    class VideoSentVH extends RecyclerView.ViewHolder {
        android.widget.FrameLayout bubbleContainer;
        ImageView imgThumbnail;
        TextView tvForwardedLabel, tvDuration, tvTimestamp, tvStatus;

        VideoSentVH(@NonNull View v) {
            super(v);
            bubbleContainer  = v.findViewById(R.id.bubbleContainer);
            tvForwardedLabel = v.findViewById(R.id.tvForwardedLabel);
            imgThumbnail     = v.findViewById(R.id.imgThumbnail);
            tvDuration       = v.findViewById(R.id.tvDuration);
            tvTimestamp      = v.findViewById(R.id.tvTimestamp);
            tvStatus         = v.findViewById(R.id.tvStatus);
        }

        void bind(MessageModel msg) {
            bindForwardedLabel(itemView, msg);

            if (msg.isDeleted()) {
                imgThumbnail.setVisibility(View.GONE);
                tvDuration.setVisibility(View.GONE);
                if (tvStatus != null) {
                    tvStatus.setText("🚫 You deleted this message");
                    tvStatus.setAlpha(0.5f);
                    tvStatus.setTypeface(null, android.graphics.Typeface.ITALIC);
                }
                bubbleContainer.setOnClickListener(null);
                bubbleContainer.setOnLongClickListener(null);
            } else {
                imgThumbnail.setVisibility(View.VISIBLE);
                loadThumbnail(imgThumbnail, msg);

                if (msg.getVideoDuration() != null && !msg.getVideoDuration().isEmpty()) {
                    tvDuration.setText(msg.getVideoDuration());
                    tvDuration.setVisibility(View.VISIBLE);
                } else {
                    tvDuration.setVisibility(View.GONE);
                }
                if (tvStatus != null) {
                    tvStatus.setText(msg.isSeen() ? "Seen" : "✓ Sent");
                    tvStatus.setAlpha(1f);
                    tvStatus.setTypeface(null, android.graphics.Typeface.NORMAL);
                }
                bubbleContainer.setOnClickListener(v -> playVideo(msg.getFileUrl()));
                bubbleContainer.setOnLongClickListener(v -> {
                    fireImageLongClick(msg, v);
                    return true;
                });
            }

            if (msg.getTimestamp() != null)
                tvTimestamp.setText(formatTime(msg.getTimestamp().toDate()));
        }
    }

    // ════════════════════════════════════════════════════════════
    //  VIDEO RECEIVED
    // ════════════════════════════════════════════════════════════
    class VideoReceivedVH extends RecyclerView.ViewHolder {
        android.widget.FrameLayout bubbleContainer;
        ImageView imgAvatar, imgThumbnail;
        TextView tvForwardedLabel, tvDuration, tvTimestamp;

        VideoReceivedVH(@NonNull View v) {
            super(v);
            bubbleContainer  = v.findViewById(R.id.bubbleContainer);
            imgAvatar        = v.findViewById(R.id.imgAvatar);
            tvForwardedLabel = v.findViewById(R.id.tvForwardedLabel);
            imgThumbnail     = v.findViewById(R.id.imgThumbnail);
            tvDuration       = v.findViewById(R.id.tvDuration);
            tvTimestamp      = v.findViewById(R.id.tvTimestamp);
        }

        void bind(MessageModel msg) {
            loadAvatar(imgAvatar, msg.getSenderId()); // ✅ pass senderId
            bindForwardedLabel(itemView, msg);

            if (msg.isDeleted()) {
                imgThumbnail.setVisibility(View.GONE);
                tvDuration.setVisibility(View.GONE);
                tvTimestamp.setText("🚫 This message was deleted");
                tvTimestamp.setAlpha(0.5f);
                tvTimestamp.setTypeface(null, android.graphics.Typeface.ITALIC);
                bubbleContainer.setOnClickListener(null);
                bubbleContainer.setOnLongClickListener(null);
            } else {
                imgThumbnail.setVisibility(View.VISIBLE);
                loadThumbnail(imgThumbnail, msg);

                if (msg.getVideoDuration() != null && !msg.getVideoDuration().isEmpty()) {
                    tvDuration.setText(msg.getVideoDuration());
                    tvDuration.setVisibility(View.VISIBLE);
                } else {
                    tvDuration.setVisibility(View.GONE);
                }
                tvTimestamp.setAlpha(1f);
                tvTimestamp.setTypeface(null, android.graphics.Typeface.NORMAL);
                if (msg.getTimestamp() != null)
                    tvTimestamp.setText(formatTime(msg.getTimestamp().toDate()));

                bubbleContainer.setOnClickListener(v -> playVideo(msg.getFileUrl()));
                bubbleContainer.setOnLongClickListener(v -> {
                    fireImageLongClick(msg, v);
                    return true;
                });
            }
        }
    }

    // ════════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════════

    private void loadAvatar(ImageView imgAvatar, String senderId) {
        String photoUrl = null;

        // For group chats use sender-specific photo
        if (isGroupChat && senderId != null) {
            photoUrl = memberPhotos.get(senderId);
        } else {
            photoUrl = otherPhotoUrl;
        }

        if (photoUrl != null && !photoUrl.isEmpty()) {
            Glide.with(context)
                    .load(photoUrl)
                    .transform(new CircleCrop())
                    .placeholder(R.drawable.circle_grey_bg)
                    .into(imgAvatar);
        } else {
            imgAvatar.setImageResource(R.drawable.circle_grey_bg);
        }
    }

    private void loadThumbnail(ImageView imgThumbnail, MessageModel msg) {
        if (msg.getThumbnailUrl() != null && !msg.getThumbnailUrl().isEmpty()) {
            Glide.with(context).load(msg.getThumbnailUrl())
                    .centerCrop().placeholder(R.drawable.circle_grey_bg).into(imgThumbnail);
        } else if (msg.getFileUrl() != null) {
            String thumbUrl = msg.getFileUrl()
                    .replace("/upload/", "/upload/w_400,h_300,c_fill/")
                    .replace(".mp4", ".jpg").replace(".mov", ".jpg").replace(".avi", ".jpg");
            Glide.with(context).load(thumbUrl)
                    .centerCrop().placeholder(R.drawable.circle_grey_bg).into(imgThumbnail);
        }
    }

    private void openFile(String fileUrl, String fileName) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            Toast.makeText(context, "File not available.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(fileUrl));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "No app found to open this file.", Toast.LENGTH_SHORT).show();
        }
    }

    private void playVideo(String videoUrl) {
        if (videoUrl == null || videoUrl.isEmpty()) {
            Toast.makeText(context, "Video not available.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(videoUrl), "video/*");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    private String getFileIcon(String fileType) {
        if (fileType == null) return "📄";
        switch (fileType.toLowerCase()) {
            case "pdf":  return "📕";
            case "doc":  case "docx": return "📘";
            case "xls":  case "xlsx": return "📗";
            case "ppt":  case "pptx": return "📙";
            case "zip":  case "rar":  return "🗜️";
            case "txt":  return "📝";
            case "mp3":  case "wav":  return "🎵";
            case "mp4":  case "mov":  return "🎬";
            default:     return "📄";
        }
    }

    private String formatTime(java.util.Date date) {
        return new SimpleDateFormat("h:mm a", Locale.getDefault()).format(date);
    }
}