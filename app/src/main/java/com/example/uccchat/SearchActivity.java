package com.example.uccchat;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class SearchActivity extends AppCompatActivity {

    private LinearLayout btnTabChats, btnTabMenu, btnTabSearch;
    private LinearLayout emptySearchLayout, notFoundLayout;
    private LinearLayout searchResultsContainer;
    private ScrollView searchResultsScrollView;
    private EditText edittxtSearchStudent;
    private Spinner spinnerCourse;

    private FirebaseFirestore db;
    private boolean isNavigating = false;

    private final String[] COURSES = {
            "All",
            "Information Technology",
            "Computer Science",
            "Psychology",
            "Education",
            "Nursing",
            "Accountancy",
            "Business Administration",
            "Civil Engineering",
            "Architecture",
            "Criminology",
            "Social Work",
            "Tourism Management"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.search);

        btnTabChats  = findViewById(R.id.btnTabChats);
        btnTabMenu   = findViewById(R.id.btnTabMenu);
        btnTabSearch = findViewById(R.id.btnTabSearch);

        edittxtSearchStudent    = findViewById(R.id.edittxtSearchStudent);
        spinnerCourse           = findViewById(R.id.spinnerCourse);
        emptySearchLayout       = findViewById(R.id.emptySearchLayout);
        notFoundLayout          = findViewById(R.id.notFoundLayout);
        searchResultsScrollView = findViewById(R.id.searchResultsScrollView);
        searchResultsContainer  = findViewById(R.id.searchResultsContainer);

        db = FirebaseFirestore.getInstance();

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, COURSES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCourse.setAdapter(adapter);

        btnTabChats.setOnClickListener(v -> navigateTo(ChatHomeActivity.class));
        btnTabMenu.setOnClickListener(v  -> navigateTo(MenuActivity.class));
        btnTabSearch.setOnClickListener(v -> handleSearch());

        edittxtSearchStudent.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                handleSearch();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void handleSearch() {
        String query          = edittxtSearchStudent.getText().toString().trim();
        String selectedCourse = spinnerCourse.getSelectedItem().toString();

        if (query.isEmpty()) {
            emptySearchLayout.setVisibility(View.VISIBLE);
            notFoundLayout.setVisibility(View.GONE);
            searchResultsScrollView.setVisibility(View.GONE);
            return;
        }

        searchResultsContainer.removeAllViews();
        String currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // ✅ Run both searches in parallel, then combine results
        final boolean[] usersDone  = {false};
        final boolean[] groupsDone = {false};
        final boolean[] foundAny   = {false};

        Runnable checkBothDone = () -> {
            if (usersDone[0] && groupsDone[0]) {
                runOnUiThread(() -> {
                    if (foundAny[0]) {
                        emptySearchLayout.setVisibility(View.GONE);
                        notFoundLayout.setVisibility(View.GONE);
                        searchResultsScrollView.setVisibility(View.VISIBLE);
                    } else {
                        emptySearchLayout.setVisibility(View.GONE);
                        notFoundLayout.setVisibility(View.VISIBLE);
                        searchResultsScrollView.setVisibility(View.GONE);
                    }
                });
            }
        };

        // ══════════════════════════════════════════
        // SEARCH 1: Users (existing logic)
        // ══════════════════════════════════════════
        db.collection("users")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {

                        // ✅ Skip yourself in results
                        if (doc.getId().equals(currentUid)) continue;

                        String firstName    = doc.getString("firstName");
                        String lastName     = doc.getString("lastName");
                        String course       = doc.getString("course");
                        String profileImage = doc.getString("photoUrl");
                        String studentId    = doc.getString("studentId");

                        if (firstName == null || lastName == null) continue;

                        String fullName = firstName + " " + lastName;

                        boolean matchesName      = fullName.toLowerCase().contains(query.toLowerCase());
                        boolean matchesStudentId = studentId != null && studentId.toLowerCase().contains(query.toLowerCase());
                        boolean matchesCourse    = selectedCourse.equals("All") || selectedCourse.equals(course);

                        if ((matchesName || matchesStudentId) && matchesCourse) {
                            foundAny[0] = true;

                            View itemView = LayoutInflater.from(this)
                                    .inflate(R.layout.item_search_result, searchResultsContainer, false);

                            TextView tvName     = itemView.findViewById(R.id.studentName);
                            ImageView ivProfile = itemView.findViewById(R.id.ImgProfile);

                            tvName.setText(fullName);

                            if (profileImage != null && !profileImage.isEmpty()) {
                                Glide.with(this)
                                        .load(profileImage)
                                        .placeholder(R.drawable.circle_grey_bg)
                                        .circleCrop()
                                        .into(ivProfile);
                            } else {
                                ivProfile.setImageResource(R.drawable.circle_grey_bg);
                            }

                            String otherUid = doc.getId();

                            itemView.setOnClickListener(v -> {
                                FirestoreHelper.get().getUser(currentUid, new FirestoreHelper.OnUserFetched() {
                                    @Override
                                    public void onSuccess(UserModel currentUser) {
                                        FirestoreHelper.get().getUser(otherUid, new FirestoreHelper.OnUserFetched() {
                                            @Override
                                            public void onSuccess(UserModel otherUser) {
                                                FirestoreHelper.get().getOrCreateChat(currentUser, otherUser,
                                                        new FirestoreHelper.OnChatReady() {
                                                            @Override
                                                            public void onReady(String chatId) {
                                                                Intent intent = new Intent(SearchActivity.this, ChatActivity.class);
                                                                intent.putExtra("chatId",    chatId);
                                                                intent.putExtra("chatName",  fullName);
                                                                intent.putExtra("chatPhoto", profileImage);
                                                                intent.putExtra("otherUid",  otherUid);
                                                                intent.putExtra("isGroup",   false);
                                                                startActivity(intent);

                                                                edittxtSearchStudent.setText("");
                                                                emptySearchLayout.setVisibility(View.VISIBLE);
                                                                searchResultsScrollView.setVisibility(View.GONE);
                                                                notFoundLayout.setVisibility(View.GONE);
                                                            }
                                                            @Override
                                                            public void onFailure(String error) {
                                                                Toast.makeText(SearchActivity.this,
                                                                        "Could not open chat.", Toast.LENGTH_SHORT).show();
                                                            }
                                                        });
                                            }
                                            @Override
                                            public void onFailure(String error) {
                                                Toast.makeText(SearchActivity.this,
                                                        "Could not find user.", Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                    }
                                    @Override
                                    public void onFailure(String error) {
                                        Toast.makeText(SearchActivity.this,
                                                "Could not load your profile.", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            });

                            searchResultsContainer.addView(itemView);
                        }
                    }
                    usersDone[0] = true;
                    checkBothDone.run();
                })
                .addOnFailureListener(e -> {
                    usersDone[0] = true;
                    checkBothDone.run();
                });

        // ══════════════════════════════════════════
        // SEARCH 2: Groups the current user is in
        // ══════════════════════════════════════════
        db.collection("chats")
                .whereEqualTo("isGroup", true)
                .whereArrayContains("participants", currentUid)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {

                        String groupName  = doc.getString("groupName");
                        String groupPhoto = doc.getString("groupPhoto");
                        String chatId     = doc.getId();

                        if (groupName == null) continue;

                        // ✅ Only show groups matching the search query
                        if (!groupName.toLowerCase().contains(query.toLowerCase())) continue;

                        // ✅ Course filter doesn't apply to groups
                        if (!selectedCourse.equals("All")) continue;

                        foundAny[0] = true;

                        View itemView = LayoutInflater.from(this)
                                .inflate(R.layout.item_search_result, searchResultsContainer, false);

                        TextView tvName     = itemView.findViewById(R.id.studentName);
                        ImageView ivProfile = itemView.findViewById(R.id.ImgProfile);

                        // ✅ Show group name with a label
                        tvName.setText("👥 " + groupName);

                        if (groupPhoto != null && !groupPhoto.isEmpty()) {
                            Glide.with(this)
                                    .load(groupPhoto)
                                    .placeholder(R.drawable.circle_grey_bg)
                                    .circleCrop()
                                    .into(ivProfile);
                        } else {
                            ivProfile.setImageResource(R.drawable.circle_grey_bg);
                        }

                        // ✅ Open group chat directly — chatId already exists
                        itemView.setOnClickListener(v -> {
                            Intent intent = new Intent(SearchActivity.this, ChatActivity.class);
                            intent.putExtra("chatId",    chatId);
                            intent.putExtra("chatName",  groupName);
                            intent.putExtra("chatPhoto", groupPhoto);
                            intent.putExtra("isGroup",   true);
                            startActivity(intent);

                            edittxtSearchStudent.setText("");
                            emptySearchLayout.setVisibility(View.VISIBLE);
                            searchResultsScrollView.setVisibility(View.GONE);
                            notFoundLayout.setVisibility(View.GONE);
                        });

                        searchResultsContainer.addView(itemView);
                    }
                    groupsDone[0] = true;
                    checkBothDone.run();
                })
                .addOnFailureListener(e -> {
                    groupsDone[0] = true;
                    checkBothDone.run();
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        isNavigating = false;
    }

    private void navigateTo(Class<?> destination) {
        if (isNavigating) return;
        isNavigating = true;
        Intent intent = new Intent(this, destination);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }
}