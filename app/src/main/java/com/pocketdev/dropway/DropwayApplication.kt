package com.pocketdev.dropway

import android.app.Application
import com.pocketdev.dropway.core.database.DropwayDatabaseHelper
import com.pocketdev.dropway.core.database.dao.DeliveryDayDao
import com.pocketdev.dropway.core.database.dao.DeliveryEventDao
import com.pocketdev.dropway.core.database.dao.HouseholdDao
import com.pocketdev.dropway.core.database.dao.ParcelDao
import com.pocketdev.dropway.core.database.dao.PaymentRecordDao
import com.pocketdev.dropway.core.database.dao.ResidentDao
import com.pocketdev.dropway.data.repository.DeliveryRepository
import com.pocketdev.dropway.data.repository.HouseholdRepository
import com.pocketdev.dropway.data.repository.PaymentRepository

class DropwayApplication : Application() {

    lateinit var databaseHelper: DropwayDatabaseHelper
        private set

    lateinit var householdRepository: HouseholdRepository
        private set

    lateinit var deliveryRepository: DeliveryRepository
        private set

    lateinit var paymentRepository: PaymentRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        databaseHelper = DropwayDatabaseHelper.getInstance(this)

        val householdDao = HouseholdDao(databaseHelper)
        val residentDao = ResidentDao(databaseHelper)
        val deliveryDayDao = DeliveryDayDao(databaseHelper)
        val parcelDao = ParcelDao(databaseHelper)
        val paymentRecordDao = PaymentRecordDao(databaseHelper)
        val eventDao = DeliveryEventDao(databaseHelper)

        householdRepository = HouseholdRepository(householdDao, residentDao, eventDao)
        deliveryRepository = DeliveryRepository(deliveryDayDao, parcelDao, eventDao)
        paymentRepository = PaymentRepository(paymentRecordDao, parcelDao, eventDao)
    }

    companion object {
        lateinit var instance: DropwayApplication
            private set
    }
}
