package com.example.uccchat;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class UCChatMessagingService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage message) {
        String chatId    = message.getData().get("chatId");
        String title     = message.getData().get("title");
        String body      = message.getData().get("body");
        String chatPhoto = message.getData().get("chatPhoto");
        String isGroup   = message.getData().get("isGroup");

        // ── Mute check ──────────────────────────────────────────
        String uid = com.google.firebase.auth.FirebaseAuth.getInstance()
                .getCurrentUser() != null
                ? com.google.firebase.auth.FirebaseAuth.getInstance()
                .getCurrentUser().getUid()
                : null;

        if (uid == null || chatId == null) return;

        // Read mute status then decide whether to show notification
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    Object data = doc.get("mutedChats");
                    if (data instanceof java.util.Map) {
                        java.util.Map<String, Boolean> mutedChats =
                                (java.util.Map<String, Boolean>) data;
                        if (Boolean.TRUE.equals(mutedChats.get(chatId))) {
                            return; // Chat is muted — suppress notification
                        }
                    }
                    // Not muted — show it
                    showNotification(chatId, title, body, chatPhoto,
                            Boolean.parseBoolean(isGroup));
                });
    }

    private void showNotification(String chatId, String title,
                                  String body, String chatPhoto,
                                  boolean isGroup) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("chatId",    chatId);
        intent.putExtra("chatName",  title);
        intent.putExtra("chatPhoto", chatPhoto);
        intent.putExtra("isGroup",   isGroup);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pi = PendingIntent.getActivity(
                this, chatId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, "uccchat_messages")
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setContentIntent(pi);

        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(chatId.hashCode(), builder.build());
    }

    @Override
    public void onNewToken(String token) {
        // Save token to Firestore
        String uid = com.google.firebase.auth.FirebaseAuth.getInstance()
                .getCurrentUser().getUid();

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .update("fcmToken", token);
    }
}