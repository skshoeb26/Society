package com.societyconnect.data.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude

data class SocietyProfile(
    val name: String = "",
    val secretaryUid: String = "",
    val inviteCode: String = "",
    val inviteRole: String = "RESIDENT",
    val subscriptionActive: Boolean = false,
    val subscriptionPlan: String = "",
    val subscriptionStartedAt: Timestamp? = null,
    val subscriptionExpiresAt: Timestamp? = null,
    val upiId: String? = null,
    val createdAt: Timestamp? = null
) {
    @get:Exclude @set:Exclude
    var id: String = ""
}
