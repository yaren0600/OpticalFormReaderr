package com.yaren.girissayfali

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yaren.girissayfali.databinding.ActivitiyMainKayitOlBinding

class MainKayitOl : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitiyMainKayitOlBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val context = this
        val db = DatabaseHelper(context)

        binding.btnKaydet.setOnClickListener {
            var kullaniciBilgisi = binding.kayitKullaniciadi.text.toString()
            var kullaniciSifre = binding.kayitSifre.text.toString()

            if(kullaniciBilgisi.isNotEmpty() && kullaniciSifre.isNotEmpty()){
                var user = User(kullaniciBilgisi, kullaniciSifre)
                val inserted = db.insertUser(user)
                if (inserted) {
                    Toast.makeText(context, "Kayıt işlemi başarılı!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Kayıt oluşturulamadı!", Toast.LENGTH_LONG).show()
                }
            }else{
                Toast.makeText(applicationContext,"Lütfen boş alanları doldurun!!!", Toast.LENGTH_LONG).show()

            }
            binding.kayitKullaniciadi.text.clear()
            binding.kayitSifre.text.clear()


        }


        binding.btnGiriseDon.setOnClickListener({
            intent = Intent(applicationContext,MainActivity::class.java)
            startActivity(intent)
        })

    }
}