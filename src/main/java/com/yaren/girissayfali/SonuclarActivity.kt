package com.yaren.girissayfali

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SonuclarActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sonuclar)

        recyclerView = findViewById(R.id.recyclerViewSonuclar)

        val preferences = getSharedPreferences("credentials", MODE_PRIVATE)
        val userId = preferences.getInt("userId", -1)

        val db = DatabaseHelper(this)
        val sonuclar = db.getResultsByUserId(userId)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = SonucAdapter(sonuclar)
    }
}
