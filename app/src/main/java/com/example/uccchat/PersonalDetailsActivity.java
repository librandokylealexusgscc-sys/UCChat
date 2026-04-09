package com.example.uccchat;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

public class PersonalDetailsActivity extends AppCompatActivity {

    private LinearLayout btnTabChats, btnTabSearch, btnTabMenu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.personaldetails);

        // connect buttons from XML
        btnTabChats = findViewById(R.id.btnTabChats);
        btnTabSearch = findViewById(R.id.btnTabSearch);
        btnTabMenu = findViewById(R.id.btnTabMenu);

        // navigation actions
        btnTabChats.setOnClickListener(v ->
                startActivity(new Intent(this, ChatActivity.class)));


        btnTabSearch.setOnClickListener(v ->
                startActivity(new Intent(this, SearchActivity.class)));

        btnTabMenu.setOnClickListener(v ->
                startActivity(new Intent(this, MenuActivity.class)));
    }
}