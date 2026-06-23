package com.societyconnect.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.societyconnect.data.models.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        User::class,
        Maintenance::class,
        Complaint::class,
        Notice::class,
        Visitor::class,
        EmergencyContact::class,
        RecurringConfig::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun maintenanceDao(): MaintenanceDao
    abstract fun complaintDao(): ComplaintDao
    abstract fun noticeDao(): NoticeDao
    abstract fun visitorDao(): VisitorDao
    abstract fun emergencyContactDao(): EmergencyContactDao
    abstract fun recurringConfigDao(): RecurringConfigDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "society_connect.db"
                )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Seed default emergency contacts
                            CoroutineScope(Dispatchers.IO).launch {
                                INSTANCE?.emergencyContactDao()?.let { dao ->
                                    dao.insert(EmergencyContact(name = "Police", phone = "100", type = "POLICE", isDefault = true))
                                    dao.insert(EmergencyContact(name = "Fire Brigade", phone = "101", type = "FIRE", isDefault = true))
                                    dao.insert(EmergencyContact(name = "Ambulance", phone = "108", type = "MEDICAL", isDefault = true))
                                    dao.insert(EmergencyContact(name = "Women Helpline", phone = "1091", type = "POLICE", isDefault = true))
                                }
                            }
                        }
                    })
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
