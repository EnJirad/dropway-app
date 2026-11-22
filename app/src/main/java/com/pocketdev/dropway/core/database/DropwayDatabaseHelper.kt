package com.pocketdev.dropway.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DropwayDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "dropway_master.db"
        const val DATABASE_VERSION = 1

        @Volatile
        private var INSTANCE: DropwayDatabaseHelper? = null

        fun getInstance(context: Context): DropwayDatabaseHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DropwayDatabaseHelper(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.beginTransaction()
        try {
            // 1. Households
            db.execSQL("""
                CREATE TABLE households (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    house_number TEXT NOT NULL,
                    village TEXT DEFAULT '',
                    village_name TEXT DEFAULT '',
                    subdistrict TEXT DEFAULT '',
                    district TEXT DEFAULT '',
                    province TEXT DEFAULT '',
                    postal_code TEXT DEFAULT '',
                    latitude REAL,
                    longitude REAL,
                    address_description TEXT DEFAULT '',
                    notes TEXT DEFAULT '',
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX idx_households_number ON households(house_number)")
            db.execSQL("CREATE INDEX idx_households_village ON households(village)")

            // 2. Residents
            db.execSQL("""
                CREATE TABLE residents (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    household_id INTEGER NOT NULL,
                    full_name TEXT NOT NULL,
                    phone_number TEXT DEFAULT '',
                    notes TEXT DEFAULT '',
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    FOREIGN KEY (household_id) REFERENCES households(id) ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX idx_residents_household ON residents(household_id)")
            db.execSQL("CREATE INDEX idx_residents_phone ON residents(phone_number)")
            db.execSQL("CREATE INDEX idx_residents_name ON residents(full_name)")

            // 3. Delivery Days
            db.execSQL("""
                CREATE TABLE delivery_days (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    date TEXT NOT NULL UNIQUE,
                    status TEXT NOT NULL DEFAULT 'ACTIVE',
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    closed_at INTEGER
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX idx_delivery_days_date ON delivery_days(date)")

            // 4. Parcels
            db.execSQL("""
                CREATE TABLE parcels (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    delivery_day_id INTEGER NOT NULL,
                    tracking_number TEXT NOT NULL,
                    recipient_name TEXT NOT NULL,
                    recipient_phone TEXT DEFAULT '',
                    household_id INTEGER,
                    destination_address TEXT DEFAULT '',
                    latitude REAL,
                    longitude REAL,
                    cod_amount_satang INTEGER NOT NULL DEFAULT 0,
                    delivery_status TEXT NOT NULL DEFAULT 'PENDING',
                    payment_status TEXT NOT NULL DEFAULT 'UNPAID',
                    delivery_notes TEXT DEFAULT '',
                    source TEXT NOT NULL DEFAULT 'MANUAL',
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    FOREIGN KEY (delivery_day_id) REFERENCES delivery_days(id) ON DELETE CASCADE,
                    FOREIGN KEY (household_id) REFERENCES households(id) ON DELETE SET NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX idx_parcels_tracking ON parcels(tracking_number)")
            db.execSQL("CREATE INDEX idx_parcels_day ON parcels(delivery_day_id)")
            db.execSQL("CREATE INDEX idx_parcels_household ON parcels(household_id)")
            db.execSQL("CREATE INDEX idx_parcels_status ON parcels(delivery_status)")
            db.execSQL("CREATE INDEX idx_parcels_payment ON parcels(payment_status)")

            // 5. Payment Records (Append-only ledger)
            db.execSQL("""
                CREATE TABLE payment_records (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    parcel_id INTEGER NOT NULL,
                    amount_satang INTEGER NOT NULL,
                    payment_method TEXT NOT NULL DEFAULT 'CASH',
                    status TEXT NOT NULL DEFAULT 'COMPLETED',
                    note TEXT DEFAULT '',
                    reference TEXT,
                    recorded_at INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    FOREIGN KEY (parcel_id) REFERENCES parcels(id) ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX idx_payments_parcel ON payment_records(parcel_id)")

            // 6. Delivery Events (Audit log)
            db.execSQL("""
                CREATE TABLE delivery_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    parcel_id INTEGER,
                    household_id INTEGER,
                    event_type TEXT NOT NULL,
                    details TEXT NOT NULL,
                    timestamp INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX idx_events_parcel ON delivery_events(parcel_id)")
            db.execSQL("CREATE INDEX idx_events_timestamp ON delivery_events(timestamp)")

            // 7. App Settings
            db.execSQL("""
                CREATE TABLE app_settings (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )
            """.trimIndent())

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Safe incremental migrations will go here
    }
}
