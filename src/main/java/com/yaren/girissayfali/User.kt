package com.yaren.girissayfali

class User {
    //Köprü sınıf

    var id: Int = 0;
    var username: String = "";
    var password: String = "";

    constructor(userName: String, password: String){
        this.username = userName
        this.password = password
    }
    constructor(userId: Int, userName: String, password: String){
        this.id = userId
        this.username = userName
        this.password = password
    }

    constructor(){
        //veritabanından veri okurken kullanılacak
    }
}