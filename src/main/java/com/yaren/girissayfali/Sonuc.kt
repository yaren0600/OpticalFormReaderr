package com.yaren.girissayfali

import java.util.Date

data class Sonuc(
    val tarih: Date,
    val kitapcik: String = "",
    val sinav: String = "",
    val dogru: Int,
    val yanlis: Int,
    val bos: Int

)