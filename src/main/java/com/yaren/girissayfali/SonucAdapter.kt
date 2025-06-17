package com.yaren.girissayfali

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat

class SonucAdapter(private val sonucList: List<Sonuc>) :
    RecyclerView.Adapter<SonucAdapter.SonucViewHolder>() {

    class SonucViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textTarih: TextView = itemView.findViewById(R.id.textTarih)
        val textSonuclar: TextView = itemView.findViewById(R.id.textSonuclar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SonucViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sonuc, parent, false)
        return SonucViewHolder(view)
    }

    override fun onBindViewHolder(holder: SonucViewHolder, position: Int) {
        val item = sonucList[position]
        val formatter = SimpleDateFormat("dd.MM.yyyy")
        holder.textTarih.text = formatter.format(item.tarih)

        // Tüm bilgileri birleştirerek göster
        holder.textSonuclar.text = """
        Sınav Türü: ${item.sinav}
        Kitapçık Türü: ${item.kitapcik}
        Doğru: ${item.dogru}, Yanlış: ${item.yanlis}, Boş: ${item.bos}
    """.trimIndent()
    }


    override fun getItemCount(): Int = sonucList.size
}
