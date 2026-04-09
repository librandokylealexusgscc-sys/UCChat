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

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class SearchActivity extends AppCompatActivity {
    private LinearLayout chatItem;
    private TextView studentName;
    private ImageView imgProfile;
    private LinearLayout btnTabChats, btnTabMenu, btnTabSearch;
    private LinearLayout emptySearchLayout, notFoundLayout;
    private LinearLayout searchResultsContainer; // ✅ FIX
    private ScrollView searchResultsScrollView;
    private EditText edittxtSearchStudent;
    private Spinner spinnerCourse;

    private FirebaseFirestore db; // ✅ FIX

    private boolean isNavigating = false;

    private final String[] COURSES = {
            "All",
            "Information Technology",
            "Computer Science",
            "Psychology",
            "Education"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.search);

        // NAV
        btnTabChats = findViewById(R.id.btnTabChats);
        btnTabMenu  = findViewById(R.id.btnTabMenu);
        btnTabSearch = findViewById(R.id.btnTabSearch);

        // VIEWS
        edittxtSearchStudent = findViewById(R.id.edittxtSearchStudent);
        spinnerCourse = findViewById(R.id.spinnerCourse);

        emptySearchLayout = findViewById(R.id.emptySearchLayout);
        notFoundLayout = findViewById(R.id.notFoundLayout);
        searchResultsScrollView = findViewById(R.id.searchResultsScrollView);
        searchResultsContainer = findViewById(R.id.searchResultsContainer); // ✅ FIX

        // FIRESTORE
        db = FirebaseFirestore.getInstance(); // ✅ FIX

        // Spinner setup
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                COURSES
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCourse.setAdapter(adapter);

        // Bottom nav
        btnTabChats.setOnClickListener(v -> navigateTo(ChatHomeActivity.class));
        btnTabMenu.setOnClickListener(v  -> navigateTo(MenuActivity.class));

        // SEARCH CLICK
        btnTabSearch.setOnClickListener(v -> handleSearch());

        // LIVE SEARCH
        edittxtSearchStudent.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                handleSearch();
            }

            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void handleSearch() {
        String query = edittxtSearchStudent.getText().toString().trim();
        String selectedCourse = spinnerCourse.getSelectedItem().toString();

        if (query.isEmpty()) {
            emptySearchLayout.setVisibility(View.VISIBLE);
            notFoundLayout.setVisibility(View.GONE);
            searchResultsScrollView.setVisibility(View.GONE);
            return;
        }

        searchResultsContainer.removeAllViews();

        db.collection("users")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {

                    boolean found = false;

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {

                        String firstName = doc.getString("firstName");
                        String lastName  = doc.getString("lastName");
                        String course    = doc.getString("course");

                        if (firstName == null || lastName == null) continue;

                        String fullName = firstName + " " + lastName;

                        boolean matchesName = fullName.toLowerCase().contains(query.toLowerCase());
                        boolean matchesCourse = selectedCourse.equals("All") || selectedCourse.equals(course);

                        if (matchesName && matchesCourse) {
                            found = true;

                            chatItem.setVisibility(View.VISIBLE); // 👈 SHOW RESULT
                            studentName.setText(fullName);

                            chatItem.setOnClickListener(v -> {
                                Intent intent = new Intent(SearchActivity.this, ChatActivity.class);

                                intent.putExtra("studentName", fullName);
                                intent.putExtra("studentId", doc.getString("studentId"));
                                intent.putExtra("email", doc.getString("email"));

                                startActivity(intent);

                                // CLEAR SEARCH
                                edittxtSearchStudent.setText("");

                                emptySearchLayout.setVisibility(View.VISIBLE);
                                searchResultsScrollView.setVisibility(View.GONE);
                                notFoundLayout.setVisibility(View.GONE);
                            });
                        }
                    }

                    if (found) {
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