package com.societyconnect.data.firebase

import androidx.lifecycle.LiveData
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.societyconnect.data.models.FirestoreEntity

fun <T : FirestoreEntity> QuerySnapshot.toEntities(clazz: Class<T>): List<T> =
    documents.mapNotNull { d -> d.toObject(clazz)?.also { it.id = d.id } }

class FirestoreDocumentLiveData<T>(
    private val ref: DocumentReference,
    private val mapper: (DocumentSnapshot) -> T?
) : LiveData<T>() {
    private var registration: ListenerRegistration? = null

    override fun onActive() {
        registration = ref.addSnapshotListener { snap, _ -> if (snap != null) value = mapper(snap) }
    }

    override fun onInactive() {
        registration?.remove()
        registration = null
    }
}

class FirestoreQueryLiveData<T>(
    private val query: Query,
    private val mapper: (QuerySnapshot) -> List<T>
) : LiveData<List<T>>() {
    private var registration: ListenerRegistration? = null

    override fun onActive() {
        registration = query.addSnapshotListener { snap, _ -> if (snap != null) value = mapper(snap) }
    }

    override fun onInactive() {
        registration?.remove()
        registration = null
    }
}
