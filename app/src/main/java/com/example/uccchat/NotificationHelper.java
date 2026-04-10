package com.example.uccchat;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class NotificationHelper {

    private static final String CHANNEL_ID   = "uccchat_messages";
    private static final String CHANNEL_NAME = "Messages";
    private static final int    NOTIF_ID     = 1001;

    /**
     * Call once at app startup (e.g. in Application class or MainActivity.onCreate).
     * Creates the notification channel required on Android 8+.
     */
    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH   // heads-up popup
            );
            channel.setDescription("New message notifications");
            channel.enableVibration(true);
            channel.enableLights(true);

            NotificationManager manager =
                    context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Posts a heads-up notification.
     *
     * @param context     any context
     * @param chatId      Firestore chat document ID — passed to ChatActivity
     * @param chatName    display name to show as notification title
     * @param chatPhoto   photo URL (not used yet — can add later with Glide)
     * @param message     message preview text
     * @param isGroup     whether this is a group chat
     * @param otherUid    UID of the sender (null for groups)
     */
    public static void showMessageNotification(Context context,
                                               String chatId,
                                               String chatName,
                                               String chatPhoto,
                                               String message,
                                               boolean isGroup,
                                               String otherUid) {

        // Tap notification → open ChatActivity directly in that conversation
        Intent intent = new Intent(context, ChatActivity.class);
        intent.putExtra("chatId",    chatId);
        intent.putExtra("chatName",  chatName);
        intent.putExtra("chatPhoto", chatPhoto);
        intent.putExtra("isGroup",   isGroup);
        if (!isGroup && otherUid != null) {
            intent.putExtra("otherUid", otherUid);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                chatId.hashCode(),      // unique per chat so each chat stacks separately
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification) // ← create this drawable (see notes)
                        .setContentTitle(chatName)
                        .setContentText(message)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent);

        NotificationManagerCompat manager = NotificationManagerCompat.from(context);

        // Use chatId.hashCode() so each conversation gets its own notification slot
        // (new messages in the same chat update rather than stack duplicates)
        manager.notify(chatId.hashCode(), builder.build());
    }

    /** Cancel the notification for a specific chat (call when user opens the chat). */
    public static void cancelNotification(Context context, String chatId) {
        NotificationManagerCompat.from(context).cancel(chatId.hashCode());
    }
}