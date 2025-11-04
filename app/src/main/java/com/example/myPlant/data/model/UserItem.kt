package com.example.myPlant.data.model

import com.google.firebase.Timestamp

data class UserItem(
    val email: String = "",
    val role: String = "",
    val dateJoined: Timestamp? = null
)