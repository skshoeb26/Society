package com.societyconnect.data.models

import androidx.lifecycle.LiveData
import androidx.room.*

// ─── USER DAO ─────────────────────────────────────────────────────────
@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: User): Long

    @Query("SELECT * FROM users WHERE phone = :phone AND password = :password LIMIT 1")
    suspend fun login(phone: String, password: String): User?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): User?

    @Query("SELECT * FROM users WHERE role = 'RESIDENT' ORDER BY flatNo ASC")
    fun getAllResidents(): LiveData<List<User>>

    // Bulk maintenance ke liye — list version (LiveData nahi)
    @Query("SELECT * FROM users WHERE role = 'RESIDENT' ORDER BY flatNo ASC")
    suspend fun getAllResidentsList(): List<User>

    @Delete
    suspend fun delete(user: User)
}

// ─── MAINTENANCE DAO ──────────────────────────────────────────────────
@Dao
interface MaintenanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(maintenance: Maintenance): Long

    // Bulk insert — ek saath saare flats ka maintenance
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<Maintenance>)

    // Check karo ki is mahine ka maintenance pehle se hai ya nahi (duplicate rokne ke liye)
    @Query("SELECT COUNT(*) FROM maintenance WHERE month = :month")
    suspend fun countByMonth(month: String): Int

    // Ek flat ka specific month already hai?
    @Query("SELECT COUNT(*) FROM maintenance WHERE flatNo = :flatNo AND month = :month")
    suspend fun countByFlatAndMonth(flatNo: String, month: String): Int

    @Update
    suspend fun update(maintenance: Maintenance)

    @Delete
    suspend fun delete(maintenance: Maintenance)

    @Query("SELECT * FROM maintenance ORDER BY dueDate DESC")
    fun getAll(): LiveData<List<Maintenance>>

    @Query("SELECT * FROM maintenance WHERE flatNo = :flatNo ORDER BY dueDate DESC")
    fun getByFlat(flatNo: String): LiveData<List<Maintenance>>

    @Query("SELECT * FROM maintenance WHERE isPaid = 0 ORDER BY dueDate ASC")
    fun getPending(): LiveData<List<Maintenance>>

    @Query("SELECT SUM(amount) FROM maintenance WHERE isPaid = 1")
    fun getTotalCollected(): LiveData<Double?>

    @Query("SELECT SUM(amount) FROM maintenance WHERE isPaid = 0")
    fun getTotalPending(): LiveData<Double?>

    @Query("SELECT COUNT(*) FROM maintenance WHERE isPaid = 0")
    fun getPendingCount(): LiveData<Int>
}

// ─── COMPLAINT DAO ────────────────────────────────────────────────────
@Dao
interface ComplaintDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(complaint: Complaint): Long

    @Update
    suspend fun update(complaint: Complaint)

    @Delete
    suspend fun delete(complaint: Complaint)

    @Query("SELECT * FROM complaints ORDER BY createdAt DESC")
    fun getAll(): LiveData<List<Complaint>>

    @Query("SELECT * FROM complaints WHERE flatNo = :flatNo ORDER BY createdAt DESC")
    fun getByFlat(flatNo: String): LiveData<List<Complaint>>

    @Query("SELECT * FROM complaints WHERE status = :status ORDER BY createdAt DESC")
    fun getByStatus(status: String): LiveData<List<Complaint>>

    @Query("SELECT COUNT(*) FROM complaints WHERE status = 'OPEN'")
    fun getOpenCount(): LiveData<Int>

    @Query("SELECT * FROM complaints WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): Complaint?
}

// ─── NOTICE DAO ───────────────────────────────────────────────────────
@Dao
interface NoticeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notice: Notice): Long

    @Update
    suspend fun update(notice: Notice)

    @Delete
    suspend fun delete(notice: Notice)

    @Query("SELECT * FROM notices ORDER BY isPinned DESC, createdAt DESC")
    fun getAll(): LiveData<List<Notice>>

    @Query("SELECT COUNT(*) FROM notices WHERE createdAt > :since")
    fun getNewCount(since: Long): LiveData<Int>
}

// ─── VISITOR DAO ──────────────────────────────────────────────────────
@Dao
interface VisitorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(visitor: Visitor): Long

    @Update
    suspend fun update(visitor: Visitor)

    @Query("SELECT * FROM visitors ORDER BY checkIn DESC")
    fun getAll(): LiveData<List<Visitor>>

    @Query("SELECT * FROM visitors WHERE visitingFlat = :flatNo ORDER BY checkIn DESC")
    fun getByFlat(flatNo: String): LiveData<List<Visitor>>

    @Query("SELECT * FROM visitors WHERE checkOut IS NULL ORDER BY checkIn DESC")
    fun getActiveVisitors(): LiveData<List<Visitor>>

    @Query("SELECT COUNT(*) FROM visitors WHERE date(checkIn/1000, 'unixepoch') = date('now')")
    fun getTodayCount(): LiveData<Int>
}

// ─── EMERGENCY CONTACT DAO ────────────────────────────────────────────
@Dao
interface EmergencyContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: EmergencyContact): Long

    @Update
    suspend fun update(contact: EmergencyContact)

    @Delete
    suspend fun delete(contact: EmergencyContact)

    @Query("SELECT * FROM emergency_contacts ORDER BY isDefault DESC, type ASC")
    fun getAll(): LiveData<List<EmergencyContact>>

    @Query("SELECT COUNT(*) FROM emergency_contacts")
    suspend fun getCount(): Int
}

// ─── RECURRING CONFIG DAO ─────────────────────────────────────────────
@Dao
interface RecurringConfigDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(config: RecurringConfig)

    @Query("SELECT * FROM recurring_config WHERE id = 1 LIMIT 1")
    suspend fun get(): RecurringConfig?

    @Query("SELECT * FROM recurring_config WHERE id = 1 LIMIT 1")
    fun getLive(): LiveData<RecurringConfig?>
}
