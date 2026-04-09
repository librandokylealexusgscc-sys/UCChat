package com.example.uccchat;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChatHomeActivity extends AppCompatActivity {

    private static final int TAB_ALL    = 0;
    private static final int TAB_GROUP  = 1;
    private static final int TAB_UNREAD = 2;
    private int currentTab = TAB_ALL;

    private View viewAll, viewGroup, viewUnread;

    private TextView btnAllChat_All, btnGroup_All, btnUnread_All;
    private LinearLayout emptyStateLayout_All;
    private RecyclerView recyclerAll;
    private ChatListAdapter adapterAll;

    private TextView btnAllChat_Group, btnGroup_Group, btnUnread_Group;
    private LinearLayout emptyGroupStateLayout;
    private RecyclerView recyclerGroup;
    private ChatListAdapter adapterGroup;

    private TextView btnAllChat_Unread, btnGroup_Unread, btnUnread_Unread;
    private LinearLayout emptyUnreadStateLayout;
    private RecyclerView recyclerUnread;
    private ChatListAdapter adapterUnread;

    private String myUid;
    private ListenerRegistration chatListener;
    private final List<ChatModel> allChats = new ArrayList<>();
    private boolean isNavigating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_home);

        myUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        if (myUid == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        if (UserSession.firstName == null) {
            loadUserSession();
        }

        setupViews();
        setupAdapters();
        setupTabClicks();
        setupBottomNav();
        setupAddButton();
        startListeningToChats();

        showTab(TAB_ALL);
    }

    @Override
    protected void onResume() {
        super.onResume();
        isNavigating = false;
    }

    private void setupViews() {
        viewAll    = getLayoutInflater().inflate(R.layout.chat, null);
        viewGroup  = getLayoutInflater().inflate(R.layout.groupbtn, null);
        viewUnread = getLayoutInflater().inflate(R.layout.unreadbtn, null);

        btnAllChat_All   = viewAll.findViewById(R.id.btnAllChat);
        btnGroup_All     = viewAll.findViewById(R.id.btnGroup);
        btnUnread_All    = viewAll.findViewById(R.id.btnUnread);
        emptyStateLayout_All = viewAll.findViewById(R.id.emptyStateLayout);
        recyclerAll      = viewAll.findViewById(R.id.recyclerViewChats);

        btnAllChat_Group  = viewGroup.findViewById(R.id.btnAllChat);
        btnGroup_Group    = viewGroup.findViewById(R.id.btnGroup);
        btnUnread_Group   = viewGroup.findViewById(R.id.btnUnread);
        emptyGroupStateLayout = viewGroup.findViewById(R.id.emptyGroupStateLayout);
        recyclerGroup     = viewGroup.findViewById(R.id.recyclerViewChats);

        btnAllChat_Unread  = viewUnread.findViewById(R.id.btnAllChat);
        btnGroup_Unread    = viewUnread.findViewById(R.id.btnGroup);
        btnUnread_Unread   = viewUnread.findViewById(R.id.btnUnread);
        emptyUnreadStateLayout = viewUnread.findViewById(R.id.emptyUnreadStateLayout);
        recyclerUnread     = viewUnread.findViewById(R.id.recyclerViewChats);
    }

    private void setupAdapters() {
        adapterAll = new ChatListAdapter(this);
        recyclerAll.setLayoutManager(new LinearLayoutManager(this));
        recyclerAll.setAdapter(adapterAll);
        adapterAll.setOnChatClickListener(this::openChat);

        adapterGroup = new ChatListAdapter(this);
        recyclerGroup.setLayoutManager(new LinearLayoutManager(this));
        recyclerGroup.setAdapter(adapterGroup);
        adapterGroup.setOnChatClickListener(this::openChat);

        adapterUnread = new ChatListAdapter(this);
        recyclerUnread.setLayoutManager(new LinearLayoutManager(this));
        recyclerUnread.setAdapter(adapterUnread);
        adapterUnread.setOnChatClickListener(this::openChat);
    }

    private void setupTabClicks() {
        btnAllChat_All.setOnClickListener(v -> showTab(TAB_ALL));
        btnGroup_All.setOnClickListener(v -> showTab(TAB_GROUP));
        btnUnread_All.setOnClickListener(v -> showTab(TAB_UNREAD));

        btnAllChat_Group.setOnClickListener(v -> showTab(TAB_ALL));
        btnGroup_Group.setOnClickListener(v -> showTab(TAB_GROUP));
        btnUnread_Group.setOnClickListener(v -> showTab(TAB_UNREAD));

        btnAllChat_Unread.setOnClickListener(v -> showTab(TAB_ALL));
        btnGroup_Unread.setOnClickListener(v -> showTab(TAB_GROUP));
        btnUnread_Unread.setOnClickListener(v -> showTab(TAB_UNREAD));
    }

    private void setupBottomNav() {
        View[] tabViews = {viewAll, viewGroup, viewUnread};
        for (View tabView : tabViews) {
            View btnSearch = tabView.findViewById(R.id.btnTabSearch);
            View btnMenu   = tabView.findViewById(R.id.btnTabMenu);

            if (btnSearch != null) {
                btnSearch.setOnClickListener(v -> navigateTo(SearchActivity.class));
            }

            if (btnMenu != null) {
                btnMenu.setOnClickListener(v -> navigateTo(MenuActivity.class));
            }
        }

        View btnStartNewChat = viewAll.findViewById(R.id.btnStartNewChat);
        if (btnStartNewChat != null) {
            btnStartNewChat.setOnClickListener(v ->
                    startActivity(new Intent(this, NewChatActivity.class)));
        }
    }

    private void setupAddButton() {
        ImageButton btnAddAll = viewAll.findViewById(R.id.btnAdd);
        if (btnAddAll != null) {
            btnAddAll.setOnClickListener(v ->
                    startActivity(new Intent(this, NewChatActivity.class)));
        }

        ImageButton btnAddGroup = viewGroup.findViewById(R.id.btnAdd);
        if (btnAddGroup != null) {
            btnAddGroup.setOnClickListener(v ->
                    startActivity(new Intent(this, NewGroupChatActivity.class)));
        }

        ImageButton btnAddUnread = viewUnread.findViewById(R.id.btnAdd);
        if (btnAddUnread != null) {
            btnAddUnread.setOnClickListener(v ->
                    startActivity(new Intent(this, NewChatActivity.class)));
        }
    }

    private void navigateTo(Class<?> destination) {
        if (isNavigating) return;
        if (this.getClass().equals(destination)) return;
        isNavigating = true;

        Intent intent = new Intent(this, destination);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    private void showTab(int tab) {
        currentTab = tab;

        android.widget.FrameLayout container = findViewById(R.id.tabContainer);
        container.removeAllViews();

        switch (tab) {
            case TAB_ALL:
                container.addView(viewAll);
                break;
            case TAB_GROUP:
                container.addView(viewGroup);
                break;
            case TAB_UNREAD:
                container.addView(viewUnread);
                break;
        }

        updateDisplayedList();
    }

    private void startListeningToChats() {
        chatListener = FirestoreHelper.get()
                .listenToChats(myUid)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;

                    allChats.clear();

                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        ChatModel chat = doc.toObject(ChatModel.class);
                        if (chat != null) {
                            chat.setChatId(doc.getId());
                            allChats.add(chat);
                        }
                    }

                    Collections.sort(allChats, (a, b) -> {
                        if (a.getLastMessageTime() == null) return 1;
                        if (b.getLastMessageTime() == null) return -1;
                        return b.getLastMessageTime()
                                .compareTo(a.getLastMessageTime());
                    });

                    updateDisplayedList();
                });
    }

    private void updateDisplayedList() {
        List<ChatModel> filtered = new ArrayList<>();

        switch (currentTab) {
            case TAB_ALL:
                filtered.addAll(allChats);
                adapterAll.setChats(filtered);
                toggleEmptyState(emptyStateLayout_All, recyclerAll, filtered.isEmpty());
                break;

            case TAB_GROUP:
                for (ChatModel c : allChats) {
                    if (c.isGroup()) filtered.add(c);
                }
                adapterGroup.setChats(filtered);
                toggleEmptyState(emptyGroupStateLayout, recyclerGroup, filtered.isEmpty());
                break;

            case TAB_UNREAD:
                for (ChatModel c : allChats) {
                    if (c.getUnreadCountFor(myUid) > 0) filtered.add(c);
                }
                adapterUnread.setChats(filtered);
                toggleEmptyState(emptyUnreadStateLayout, recyclerUnread, filtered.isEmpty());
                break;
        }
    }

    private void toggleEmptyState(View emptyView, View recyclerView, boolean isEmpty) {
        if (emptyView != null) {
            emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }
        if (recyclerView != null) {
            recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }
    }

    private void openChat(ChatModel chat) {
        FirestoreHelper.get().markChatAsRead(chat.getChatId(), myUid);

        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("chatId",    chat.getChatId());
        intent.putExtra("chatName",  chat.getDisplayName(myUid));
        intent.putExtra("chatPhoto", chat.getDisplayPhoto(myUid));
        intent.putExtra("isGroup",   chat.isGroup());
        if (!chat.isGroup()) {
            intent.putExtra("otherUid", chat.getOtherUid(myUid));
        }
        startActivity(intent);
    }

    private void loadUserSession() {
        FirestoreHelper.get().getUser(myUid, new FirestoreHelper.OnUserFetched() {
            @Override
            public void onSuccess(UserModel user) {
                UserSession.firstName = user.getFirstName();
                UserSession.lastName  = user.getLastName();
                UserSession.username  = user.getUsername();
                UserSession.email     = user.getEmail();
                UserSession.phone     = user.getPhone();
                UserSession.studentId = user.getStudentId();
                UserSession.photoUrl  = user.getPhotoUrl();
                UserSession.firebaseUid = myUid;
            }
            @Override
            public void onFailure(String error) {
                Toast.makeText(ChatHomeActivity.this,
                        "Failed to load profile.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (chatListener != null) chatListener.remove();
    }
}