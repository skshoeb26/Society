package com.societyconnect.data.firebase

import androidx.lifecycle.LiveData
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot

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
