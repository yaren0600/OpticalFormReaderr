package com.yaren.girissayfali

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yaren.girissayfali.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding
    lateinit var preferences : SharedPreferences

    lateinit var username : EditText
    lateinit var password : EditText
    lateinit var loginButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        val context = this
        val db = DatabaseHelper(context)
        //    ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
        //      val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        //    v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
        //  insets

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.loginButton.setOnClickListener{

            var girisKullanici = binding.girisKullaniciad.text.toString()
            var girisSifre = binding.girisSifre.text.toString()

            val userId = db.getUserIdByCredentials(girisKullanici, girisSifre)
            if(userId == -1){
                Toast.makeText(this, "Kullanıcı adı ve ya şifre hatalı!!!", Toast.LENGTH_LONG).show()
                return@setOnClickListener

            }


            val preferences = getSharedPreferences("credentials", MODE_PRIVATE)
            val editor = preferences.edit()
            editor.putInt("userId", userId)
            editor.apply()

            val intent = Intent(applicationContext, MainHosgeldiniz::class.java)
            startActivity(intent)

        }

        binding.kayitButton.setOnClickListener{
            intent = Intent(applicationContext,MainKayitOl::class.java)
            startActivity(intent)
        }
    }
}