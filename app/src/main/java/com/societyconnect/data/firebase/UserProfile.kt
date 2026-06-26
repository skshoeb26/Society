package com.societyconnect.data.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude

data class UserProfile(
    val name: String = "",
    val email: String? = null,
    val phone: String? = null,
    val role: String = "RESIDENT",
    val societyId: String = "",
    val flatNo: String = "",
    val flatType: String = "1BHK",
    val fcmToken: String? = null,
    val createdAt: Timestamp? = null
) {
    @get:Exclude @set:Exclude
    var uid: String = ""
}
