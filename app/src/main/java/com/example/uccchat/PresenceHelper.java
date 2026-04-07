package com.example.uccchat;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Date;
import java.util.concurrent.TimeUnit;

public class PresenceHelper {

    public interface OnPresenceUpdate {
        void onUpdate(boolean isOnline, String statusText);
    }

    /**
     * Listens to another user's online status in real time.
     * Returns a ListenerRegistration — call .remove() in onDestroy().
     */
    public static ListenerRegistration listenToPresence(
            String uid,
            OnPresenceUpdate callback) {

        return FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .addSnapshotListener((doc, error) -> {
                    if (error != null || doc == null || !doc.exists()) return;

                    Boolean isOnline = doc.getBoolean("isOnline");
                    Date lastSeen    = doc.getDate("lastSeen");

                    if (Boolean.TRUE.equals(isOnline)) {
                        callback.onUpdate(true, "Online");
                    } else {
                        String timeAgo = formatLastSeen(lastSeen);
                        callback.onUpdate(false, timeAgo);
                    }
                });
    }

    /**
     * Formats lastSeen into a human-readable string.
     * e.g. "Last seen 5 minutes ago" / "Last seen 2 hours ago"
     */
    public static String formatLastSeen(Date lastSeen) {
        if (lastSeen == null) return "Offline";

        long diffMs      = new Date().getTime() - lastSeen.getTime();
        long diffSecs    = TimeUnit.MILLISECONDS.toSeconds(diffMs);
        long diffMins    = TimeUnit.MILLISECONDS.toMinutes(diffMs);
        long diffHours   = TimeUnit.MILLISECONDS.toHours(diffMs);
        long diffDays    = TimeUnit.MILLISECONDS.toDays(diffMs);

        if (diffSecs < 60) {
            return "Last seen just now";
        } else if (diffMins < 60) {
            return "Last seen " + diffMins
                    + (diffMins == 1 ? " minute ago" : " minutes ago");
        } else if (diffHours < 24) {
            return "Last seen " + diffHours
                    + (diffHours == 1 ? " hour ago" : " hours ago");
        } else if (diffDays == 1) {
            return "Last seen yesterday";
        } else {
            return "Last seen " + diffDays + " days ago";
        }
    }
}