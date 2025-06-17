package com.yaren.girissayfali

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

val database_name = "Veritabani"


class DatabaseHelper(var context: Context) :
    SQLiteOpenHelper(context, database_name, null, 6) {

    override fun onCreate(db: SQLiteDatabase?) {
        // Kullanıcılar tablosu
        val createUserTable = """
            CREATE TABLE IF NOT EXISTS Users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username VARCHAR(256),
                password VARCHAR(256)
            )
        """.trimIndent()
        db?.execSQL(createUserTable)

        // Sonuçlar tablosu
        val createResultTable = """
            CREATE TABLE IF NOT EXISTS Results (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                userId INTEGER,
                kitapcik TEXT, 
                sinav TEXT,
                date INTEGER, 
                correct INTEGER,
                wrong INTEGER,
                empty INTEGER,
                FOREIGN KEY(userid) REFERENCES Users(id)
            )
        """.trimIndent()
        db?.execSQL(createResultTable)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS Users")
        db?.execSQL("DROP TABLE IF EXISTS Results")
        onCreate(db)
    }

    fun insertUser(user: User): Boolean {
        val db = this.writableDatabase
        val cv = ContentValues()
        cv.put("username", user.username)
        cv.put("password", user.password)
        val rowId = db.insert("Users", null, cv)
        db.close()
        return rowId != (-1).toLong()
    }

    fun insertResult(sonuc: Sonuc, userId: Int):Boolean{
        val db = this.writableDatabase
        val cv = ContentValues()
        // Türkiye saatine göre şu anki zamanı al
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        dateFormat.timeZone = TimeZone.getTimeZone("Europe/Istanbul")
        val currentDate = dateFormat.format(Date())

        cv.put("date", sonuc.tarih.time)
        cv.put("kitapcik",sonuc.kitapcik)
        cv.put("sinav", sonuc.sinav)
        cv.put("correct", sonuc.dogru)
        cv.put("wrong", sonuc.yanlis)
        cv.put("empty", sonuc.bos)
        cv.put("userId", userId)

        val rowId = db.insert("Results", null, cv)
        db.close()
        return rowId != (-1).toLong()
    }

    @SuppressLint("Range")
    fun getUsers(): MutableList<User> {
        val users: MutableList<User> = ArrayList()
        val db = this.readableDatabase
        val query = "SELECT * FROM Users"
        val cursor = db.rawQuery(query, null)
        if (cursor.moveToFirst()) {
            do {
                val user = User()
                user.id = cursor.getInt(cursor.getColumnIndex("id"))
                user.username = cursor.getString(cursor.getColumnIndex("username"))
                user.password = cursor.getString(cursor.getColumnIndex("password"))
                users.add(user)
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return users
    }

    @SuppressLint("Range")
    fun getUserIdByCredentials(username : String, password : String): Int{
        val db = this.readableDatabase
        val query = "SELECT id FROM Users WHERE username = ? AND password = ?"
        val cursor = db.rawQuery(query, arrayOf(username, password) )
        var userId = -1 // val sabit var değişken
        if(cursor.moveToFirst()){
            userId = cursor.getInt(cursor.getColumnIndex("id"))

        }

        cursor.close()
        db.close()
        return userId
    }

    @SuppressLint("Range")
    fun getUserById(userId : Int): User?{
        val db = this.readableDatabase
        val query = "SELECT * FROM Users WHERE id = "+ userId
        val cursor = db.rawQuery(query, null )
        var user: User? = null //  ? null olabilir demek
        if(cursor.moveToFirst()){
            val username = cursor.getString(cursor.getColumnIndex("username"))
            val password = cursor.getString(cursor.getColumnIndex("password"))
            user = User(userId, username, password)

        }

        cursor.close()
        db.close()
        return user
    }

fun getResultsByUserId(userId: Int): List<Sonuc> {
    val db = this.readableDatabase
    val list = mutableListOf<Sonuc>()
    val query = "SELECT date, correct, wrong, empty, kitapcik, sinav FROM Results WHERE userId = ? ORDER BY date DESC"
    val cursor = db.rawQuery(query, arrayOf(userId.toString()))

    if (cursor.moveToFirst()) {
        do {
            val timestamp = cursor.getLong(0)
            val tarih = Date(timestamp)       // Unix zaman damgasını Date'e çevir

            val dogru = cursor.getInt(1)
            val yanlis = cursor.getInt(2)
            val bos = cursor.getInt(3)
            val kitapcik = cursor.getString(4)
            val sinav = cursor.getString(5)

            list.add(Sonuc(tarih, kitapcik, sinav, dogru, yanlis, bos))
        } while (cursor.moveToNext())
    }

    cursor.close()
    db.close()
    return list
}

}
