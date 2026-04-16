package com.example.uccchat;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatListAdapter extends RecyclerView.Adapter<ChatListAdapter.ChatViewHolder> {

    private final Context context;
    private final List<ChatModel> chatList;
    private final String myUid;
    private OnChatClickListener listener;

    public interface OnChatClickListener {
        void onChatClick(ChatModel chat);
    }

    public ChatListAdapter(Context context) {
        this.context  = context;
        this.chatList = new ArrayList<>();
        this.myUid    = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
    }

    public void setOnChatClickListener(OnChatClickListener listener) {
        this.listener = listener;
    }

    /** Replace the full list and refresh */
    public void setChats(List<ChatModel> newList) {
        chatList.clear();
        chatList.addAll(newList);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_chat, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        ChatModel chat = chatList.get(position);
        holder.bind(chat);
    }

    @Override
    public int getItemCount() { return chatList.size(); }

    // ─── ViewHolder ───────────────────────────────────────────

    class ChatViewHolder extends RecyclerView.ViewHolder {

        ImageView imgAvatar;
        TextView tvName, tvLastMessage, tvTimestamp, tvUnreadBadge;

        ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            imgAvatar = itemView.findViewById(R.id.imgAvatar);
            tvName = itemView.findViewById(R.id.tvName);
            tvLastMessage = itemView.findViewById(R.id.tvLastMessage);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvUnreadBadge = itemView.findViewById(R.id.tvUnreadBadge);
        }

        void bind(ChatModel chat) {

            // ── Name ──────────────────────────────────────────
            tvName.setText(chat.getDisplayName(myUid));

            // ── Last Message ──────────────────────────────────
            String preview = chat.getLastMessage();
            if (preview == null || preview.isEmpty()) {
                preview = "Say hello! 👋";
            }
            tvLastMessage.setText(preview);

            // ── Timestamp ─────────────────────────────────────
            if (chat.getLastMessageTime() != null) {
                tvTimestamp.setText(
                        formatTimestamp(chat.getLastMessageTime().toDate()));
            }

            // ── Unread Badge ──────────────────────────────────
            long unread = chat.getUnreadCountFor(myUid);
            if (unread > 0) {
                // ✅ Show badge
                tvUnreadBadge.setVisibility(View.VISIBLE);
                tvUnreadBadge.setText(unread > 99 ? "99+" : String.valueOf(unread));

                // ✅ Bold name
                tvName.setTypeface(null, android.graphics.Typeface.BOLD);

                // ✅ Bold last message + use color resource not hardcoded
                tvLastMessage.setTypeface(null, android.graphics.Typeface.BOLD);
                tvLastMessage.setTextColor(
                        context.getResources().getColor(R.color.text_primary,
                                context.getTheme()));

                // ✅ Bold timestamp too
                tvTimestamp.setTypeface(null, android.graphics.Typeface.BOLD);
                tvTimestamp.setTextColor(
                        context.getResources().getColor(R.color.text_primary,
                                context.getTheme()));

            } else {
                // ✅ No unread — reset everything
                tvUnreadBadge.setVisibility(View.GONE);

                tvName.setTypeface(null, android.graphics.Typeface.NORMAL);

                tvLastMessage.setTypeface(null, android.graphics.Typeface.NORMAL);
                tvLastMessage.setTextColor(
                        context.getResources().getColor(R.color.text_secondary,
                                context.getTheme()));

                tvTimestamp.setTypeface(null, android.graphics.Typeface.NORMAL);
                tvTimestamp.setTextColor(
                        context.getResources().getColor(R.color.text_secondary,
                                context.getTheme()));
            }

            // ── Avatar ────────────────────────────────────────
            String photoUrl = chat.getDisplayPhoto(myUid);
            if (photoUrl != null && !photoUrl.isEmpty()) {
                Glide.with(context)
                        .load(photoUrl)
                        .transform(new CircleCrop())
                        .placeholder(R.drawable.circle_grey_bg)
                        .into(imgAvatar);
            } else {
                imgAvatar.setImageResource(R.drawable.circle_grey_bg);
            }

            // ── Click ─────────────────────────────────────────
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onChatClick(chat);
            });
        }
    }

    // ─── Timestamp Formatter ──────────────────────────────────

    private String formatTimestamp(Date date) {
        if (date == null) return "";

        Calendar msgCal  = Calendar.getInstance();
        msgCal.setTime(date);

        Calendar todayCal = Calendar.getInstance();

        boolean isToday =
                msgCal.get(Calendar.YEAR)         == todayCal.get(Calendar.YEAR) &&
                        msgCal.get(Calendar.DAY_OF_YEAR)  == todayCal.get(Calendar.DAY_OF_YEAR);

        boolean isYesterday =
                msgCal.get(Calendar.YEAR)         == todayCal.get(Calendar.YEAR) &&
                        msgCal.get(Calendar.DAY_OF_YEAR)  == todayCal.get(Calendar.DAY_OF_YEAR) - 1;

        if (isToday) {
            // Show time e.g. "5:00 pm"
            return new SimpleDateFormat("h:mm a", Locale.getDefault()).format(date);
        } else if (isYesterday) {
            return "Yesterday";
        } else {
            // Show date e.g. "Apr 3"
            return new SimpleDateFormat("MMM d", Locale.getDefault()).format(date);
        }
    }
}