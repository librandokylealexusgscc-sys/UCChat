package com.example.uccchat;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

public class SearchActivity extends AppCompatActivity {

    private LinearLayout btnTabChats, btnTabMenu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.search);

        // connect buttons from XML
        btnTabChats = findViewById(R.id.btnTabChats);
        btnTabMenu = findViewById(R.id.btnTabMenu);

        // navigation
        btnTabChats.setOnClickListener(v ->
                startActivity(new Intent(SearchActivity.this, ChatActivity.class)));



        btnTabMenu.setOnClickListener(v ->
                startActivity(new Intent(SearchActivity.this, MenuActivity.class)));
    }
}