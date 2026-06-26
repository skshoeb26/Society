package com.societyconnect.data.firebase

import androidx.lifecycle.LiveData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val functions = FirebaseFunctions.getInstance()

    val currentUser: FirebaseUser? get() = auth.currentUser

    suspend fun signInWithGoogle(idToken: String): FirebaseUser {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        return auth.signInWithCredential(credential).await().user
            ?: throw IllegalStateException("Google sign-in failed")
    }

    fun signOut() = auth.signOut()

    suspend fun getMyProfile(): UserProfile? {
        val uid = auth.currentUser?.uid ?: return null
        val snap = db.collection("users").document(uid).get().await()
        if (!snap.exists()) return null
        return snap.toObject(UserProfile::class.java)?.also { it.uid = snap.id }
    }

    suspend fun getSociety(societyId: String): SocietyProfile? {
        val snap = db.collection("societies").document(societyId).get().await()
        if (!snap.exists()) return null
        return snap.toObject(SocietyProfile::class.java)?.also { it.id = snap.id }
    }

    fun getSocietyLive(societyId: String): LiveData<SocietyProfile?> =
        FirestoreDocumentLiveData(db.collection("societies").document(societyId)) { snap ->
            if (!snap.exists()) null else snap.toObject(SocietyProfile::class.java)?.also { it.id = snap.id }
        }

    fun getMembersLive(societyId: String): LiveData<List<UserProfile>> =
        FirestoreQueryLiveData(
            db.collection("users").whereEqualTo("societyId", societyId).orderBy("flatNo")
        ) { snap ->
            snap.documents.mapNotNull { d -> d.toObject(UserProfile::class.java)?.also { it.uid = d.id } }
        }

    suspend fun getResidents(societyId: String): List<UserProfile> {
        val snap = db.collection("users")
            .whereEqualTo("societyId", societyId)
            .whereEqualTo("role", "RESIDENT")
            .get().await()
        return snap.documents.mapNotNull { d -> d.toObject(UserProfile::class.java)?.also { it.uid = d.id } }
    }

    suspend fun removeMember(uid: String) {
        db.collection("users").document(uid).delete().await()
    }

    suspend fun createSociety(name: String, flatNo: String, flatType: String): Map<String, Any?> {
        val data = hashMapOf("name" to name, "flatNo" to flatNo, "flatType" to flatType)
        val result = functions.getHttpsCallable("createSociety").call(data).await()
        @Suppress("UNCHECKED_CAST")
        return result.data as Map<String, Any?>
    }

    suspend fun redeemInviteCode(inviteCode: String, flatNo: String, flatType: String): Map<String, Any?> {
        val data = hashMapOf("inviteCode" to inviteCode, "flatNo" to flatNo, "flatType" to flatType)
        val result = functions.getHttpsCallable("redeemInviteCode").call(data).await()
        @Suppress("UNCHECKED_CAST")
        return result.data as Map<String, Any?>
    }

    suspend fun activateSubscription(plan: String) {
        functions.getHttpsCallable("activateSubscription").call(hashMapOf("plan" to plan)).await()
    }

    suspend fun cancelSubscription() {
        functions.getHttpsCallable("cancelSubscription").call(hashMapOf<String, Any?>()).await()
    }

    suspend fun updateUpiId(societyId: String, upiId: String) {
        db.collection("societies").document(societyId).update("upiId", upiId).await()
    }

    suspend fun updateProfile(name: String, phone: String, flatType: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid)
            .update(mapOf("name" to name, "phone" to phone, "flatType" to flatType))
            .await()
    }

    fun societiesCollection(): CollectionReference = db.collection("societies")

    suspend fun registerFcmToken() {
        val uid = auth.currentUser?.uid ?: return
        val token = FirebaseMessaging.getInstance().token.await()
        db.collection("users").document(uid).update("fcmToken", token).await()
    }
}
