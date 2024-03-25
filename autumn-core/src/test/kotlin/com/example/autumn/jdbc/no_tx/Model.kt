package com.example.autumn.jdbc.no_tx

class Address {
    var id: Int = 0
    var userId: Int = 0
    var address: String? = null
    var zipcode: Int = 0

    fun setZip(zip: Int?) {
        this.zipcode = zip ?: 0
    }
}

class User {
    var id: Int = 0
    var name: String? = null
    var theAge: Int? = null

    fun setAge(age: Int?) {
        this.theAge = age
    }
}
