package com.societyconnect.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
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
        RecurringConfig::class,
        Society::class
    ],
    version = 3,
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
    abstract fun societyDao(): SocietyDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // v1 -> v2: bulk-maintenance feature added flatType to users and a new recurring_config table.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN flatType TEXT NOT NULL DEFAULT '1BHK'")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recurring_config (
                        id INTEGER NOT NULL PRIMARY KEY,
                        isEnabled INTEGER NOT NULL,
                        mode TEXT NOT NULL,
                        sameAmount REAL NOT NULL,
                        amount1BHK REAL NOT NULL,
                        amount2BHK REAL NOT NULL,
                        amount3BHK REAL NOT NULL,
                        amountShop REAL NOT NULL,
                        dueDay INTEGER NOT NULL,
                        lastGeneratedMonth TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        // v2 -> v3: secretary subscriptions + member invite codes.
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS societies (
                        name TEXT NOT NULL PRIMARY KEY,
                        secretaryUserId INTEGER NOT NULL,
                        inviteCode TEXT NOT NULL,
                        inviteRole TEXT NOT NULL,
                        subscriptionActive INTEGER NOT NULL,
                        subscriptionPlan TEXT NOT NULL,
                        subscriptionStartedAt INTEGER,
                        subscriptionExpiresAt INTEGER,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
