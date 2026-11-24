package com.pocketdev.dropway.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import com.pocketdev.dropway.core.util.PhoneUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.InputStreamReader
import java.util.regex.Pattern

data class ImportResult(
    val totalRecordsParsed: Int,
    val householdsCreated: Int,
    val residentsCreated: Int,
    val durationMs: Long
)

object HouseholdCsvImporter {

    private val provPattern = Pattern.compile("(?:จังหวัด|จ\\.)\\s*([ก-๙a-zA-Z]+)")
    private val distPattern = Pattern.compile("(?:อำเภอ|อ\\.|เขต)\\s*([ก-๙a-zA-Z]+)")
    private val subdistPattern = Pattern.compile("(?:ตำบล|ต\\.|แขวง)\\s*([ก-๙a-zA-Z]+)")
    private val vnamePattern = Pattern.compile("(?:บ\\.|บ้าน)\\s*([ก-๙a-zA-Z]+)")
    private val mooPattern = Pattern.compile("(?:หมู่ที่|หมู่|ม\\.)\\s*([0-9]+)")
    private val hnPattern1 = Pattern.compile("^([0-9]+(?:/[0-9]+)?(?:\\s*\\|\\s*[0-9]+)?)")
    private val hnPattern2 = Pattern.compile("([0-9]+(?:/[0-9]+)?)")

    suspend fun importFromAssets(
        context: Context,
        dbHelper: DropwayDatabaseHelper,
        assetFileName: String = "households_master.csv",
        clearExisting: Boolean = false
    ): ImportResult = withContext(Dispatchers.IO) {
        val stream = context.assets.open(assetFileName)
        stream.use { s ->
            importFromInputStream(dbHelper, s, clearExisting)
        }
    }

    suspend fun importFromInputStream(
        dbHelper: DropwayDatabaseHelper,
        inputStream: InputStream,
        clearExisting: Boolean = false
    ): ImportResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val db = dbHelper.writableDatabase

        val rows = parseCsv(inputStream)
        if (rows.isEmpty()) {
            return@withContext ImportResult(0, 0, 0, 0)
        }

        // Header mapping
        val header = rows[0].map { it.trim().lowercase().removePrefix("\uFEFF") }
        val idIdx = header.indexOf("id").takeIf { it >= 0 } ?: 0
        val nameIdx = header.indexOf("full_name").takeIf { it >= 0 } ?: 1
        val phoneIdx = header.indexOf("phone").takeIf { it >= 0 } ?: 2
        val addrIdx = header.indexOf("address").takeIf { it >= 0 } ?: 3
        val latIdx = header.indexOf("lat").takeIf { it >= 0 } ?: 4
        val lngIdx = header.indexOf("lng").takeIf { it >= 0 } ?: 5
        val noteIdx = header.indexOf("note").takeIf { it >= 0 } ?: 6

        data class CsvRowData(
            val id: String,
            val fullName: String,
            val phone: String,
            val address: String,
            val lat: Double?,
            val lng: Double?,
            val note: String
        )

        data class HouseGrouping(
            val houseNumber: String,
            val village: String,
            val villageName: String,
            val subdistrict: String,
            val district: String,
            val province: String,
            val postalCode: String,
            var latitude: Double?,
            var longitude: Double?,
            val addressDescription: String,
            var notes: String,
            val residents: MutableList<Pair<String, String>> = mutableListOf() // (name, phone)
        )

        val houseMap = linkedMapOf<String, HouseGrouping>()
        var parsedCount = 0

        for (i in 1 until rows.size) {
            val r = rows[i]
            if (r.isEmpty()) continue
            parsedCount++

            val id = if (idIdx < r.size) r[idIdx].trim() else ""
            val name = if (nameIdx < r.size) r[nameIdx].trim() else ""
            val phone = if (phoneIdx < r.size) PhoneUtils.normalize(r[phoneIdx].trim()) else ""
            val addr = if (addrIdx < r.size) r[addrIdx].trim() else ""
            val latStr = if (latIdx < r.size) r[latIdx].trim() else ""
            val lngStr = if (lngIdx < r.size) r[lngIdx].trim() else ""
            val lat = latStr.toDoubleOrNull()
            val lng = lngStr.toDoubleOrNull()
            val note = if (noteIdx < r.size) r[noteIdx].trim().replace("\u27a1", "->") else ""

            val key = if (addr.isNotBlank()) {
                if (note.isNotBlank()) "$addr || $note" else addr
            } else {
                "$id || $note"
            }

            val parsedAddr = parseThaiAddress(addr, name, note)

            val existing = houseMap[key]
            if (existing == null) {
                val grouping = HouseGrouping(
                    houseNumber = parsedAddr.houseNumber,
                    village = parsedAddr.village,
                    villageName = parsedAddr.villageName,
                    subdistrict = parsedAddr.subdistrict,
                    district = parsedAddr.district,
                    province = parsedAddr.province,
                    postalCode = parsedAddr.postalCode,
                    latitude = lat,
                    longitude = lng,
                    addressDescription = parsedAddr.addressDescription,
                    notes = note
                )
                if (name.isNotBlank() || phone.isNotBlank()) {
                    grouping.residents.add(Pair(name.ifBlank { "ไม่ระบุชื่อ" }, phone))
                }
                houseMap[key] = grouping
            } else {
                if (existing.latitude == null && lat != null) {
                    existing.latitude = lat
                    existing.longitude = lng
                }
                if (existing.notes.isBlank() && note.isNotBlank()) {
                    existing.notes = note
                }
                if (name.isNotBlank() || phone.isNotBlank()) {
                    val pair = Pair(name.ifBlank { "ไม่ระบุชื่อ" }, phone)
                    if (!existing.residents.contains(pair)) {
                        existing.residents.add(pair)
                    }
                }
            }
        }

        var insertedHouses = 0
        var insertedResidents = 0

        db.beginTransaction()
        try {
            if (clearExisting) {
                db.execSQL("DELETE FROM residents")
                db.execSQL("DELETE FROM households")
                try {
                    db.execSQL("DELETE FROM sqlite_sequence WHERE name IN ('households', 'residents')")
                } catch (_: Exception) {}
            }

            val houseSql = """
                INSERT INTO households (
                    house_number, village, village_name, subdistrict, district, province, postal_code,
                    latitude, longitude, address_description, notes, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

            val residentSql = """
                INSERT INTO residents (
                    household_id, full_name, phone_number, notes, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()

            val houseStmt: SQLiteStatement = db.compileStatement(houseSql)
            val residentStmt: SQLiteStatement = db.compileStatement(residentSql)
            val now = System.currentTimeMillis()

            for ((_, house) in houseMap) {
                houseStmt.clearBindings()
                houseStmt.bindString(1, house.houseNumber)
                houseStmt.bindString(2, house.village)
                houseStmt.bindString(3, house.villageName)
                houseStmt.bindString(4, house.subdistrict)
                houseStmt.bindString(5, house.district)
                houseStmt.bindString(6, house.province)
                houseStmt.bindString(7, house.postalCode)
                if (house.latitude != null) houseStmt.bindDouble(8, house.latitude!!) else houseStmt.bindNull(8)
                if (house.longitude != null) houseStmt.bindDouble(9, house.longitude!!) else houseStmt.bindNull(9)
                houseStmt.bindString(10, house.addressDescription)
                houseStmt.bindString(11, house.notes)
                houseStmt.bindLong(12, now)
                houseStmt.bindLong(13, now)

                val houseId = houseStmt.executeInsert()
                if (houseId != -1L) {
                    insertedHouses++

                    for ((rName, rPhone) in house.residents) {
                        residentStmt.clearBindings()
                        residentStmt.bindLong(1, houseId)
                        residentStmt.bindString(2, rName)
                        residentStmt.bindString(3, rPhone)
                        residentStmt.bindString(4, "")
                        residentStmt.bindLong(5, now)
                        residentStmt.bindLong(6, now)
                        val resId = residentStmt.executeInsert()
                        if (resId != -1L) insertedResidents++
                    }
                }
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        DatabaseChangeNotifier.notifyChanged("households", "residents")
        val duration = System.currentTimeMillis() - startTime
        ImportResult(
            totalRecordsParsed = parsedCount,
            householdsCreated = insertedHouses,
            residentsCreated = insertedResidents,
            durationMs = duration
        )
    }

    private fun parseThaiAddress(rawAddr: String, fullName: String, note: String): ParsedAddressComponents {
        val addr = rawAddr.trim()
        if (addr.isBlank()) {
            val hn = when {
                note.isNotBlank() -> note.split("\\s+".toRegex()).firstOrNull()?.take(25) ?: "จุดส่งพัสดุ"
                fullName.isNotBlank() -> fullName.split("\\s+".toRegex()).firstOrNull()?.take(25) ?: "จุดส่งพัสดุ"
                else -> "จุดส่งพัสดุ"
            }
            return ParsedAddressComponents(
                houseNumber = hn,
                village = "",
                villageName = "",
                subdistrict = "",
                district = "",
                province = "",
                postalCode = "",
                addressDescription = note.ifBlank { "จุดส่งพัสดุ" }
            )
        }

        var prov = ""
        val mProv = provPattern.matcher(addr)
        if (mProv.find()) prov = mProv.group(1)?.trim() ?: ""

        var dist = ""
        val mDist = distPattern.matcher(addr)
        if (mDist.find()) dist = mDist.group(1)?.trim() ?: ""

        var subdist = ""
        val mSubdist = subdistPattern.matcher(addr)
        if (mSubdist.find()) subdist = mSubdist.group(1)?.trim() ?: ""

        var vname = ""
        val mVname = vnamePattern.matcher(addr)
        if (mVname.find()) vname = "บ้าน" + (mVname.group(1)?.trim() ?: "")

        var moo = ""
        val mMoo = mooPattern.matcher(addr)
        if (mMoo.find()) moo = mMoo.group(1)?.trim() ?: ""

        var houseNum = ""
        val mHn1 = hnPattern1.matcher(addr)
        if (mHn1.find()) {
            houseNum = mHn1.group(1)?.trim() ?: ""
        } else {
            val mHn2 = hnPattern2.matcher(addr)
            if (mHn2.find()) {
                houseNum = mHn2.group(1)?.trim() ?: ""
            } else {
                houseNum = addr.split("\\s+".toRegex()).firstOrNull() ?: "ไม่ระบุ"
            }
        }

        return ParsedAddressComponents(
            houseNumber = houseNum,
            village = moo,
            villageName = vname,
            subdistrict = subdist,
            district = dist,
            province = prov,
            postalCode = "",
            addressDescription = addr
        )
    }

    private data class ParsedAddressComponents(
        val houseNumber: String,
        val village: String,
        val villageName: String,
        val subdistrict: String,
        val district: String,
        val province: String,
        val postalCode: String,
        val addressDescription: String
    )

    /**
     * RFC 4180 compliant CSV parser supporting multi-line quoted fields.
     */
    fun parseCsv(inputStream: InputStream): List<List<String>> {
        val reader = InputStreamReader(inputStream, Charsets.UTF_8)
        val records = mutableListOf<List<String>>()
        val currentField = StringBuilder()
        val currentRow = mutableListOf<String>()
        var inQuotes = false

        var chInt: Int
        while (reader.read().also { chInt = it } != -1) {
            val c = chInt.toChar()
            if (inQuotes) {
                if (c == '"') {
                    // Peek ahead
                    reader.mark(1)
                    val nextInt = reader.read()
                    if (nextInt != -1 && nextInt.toChar() == '"') {
                        currentField.append('"')
                    } else {
                        inQuotes = false
                        reader.reset()
                    }
                } else {
                    currentField.append(c)
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    ',' -> {
                        currentRow.add(currentField.toString())
                        currentField.setLength(0)
                    }
                    '\r' -> {}
                    '\n' -> {
                        currentRow.add(currentField.toString())
                        currentField.setLength(0)
                        if (currentRow.any { it.isNotBlank() }) {
                            records.add(ArrayList(currentRow))
                        }
                        currentRow.clear()
                    }
                    else -> currentField.append(c)
                }
            }
        }

        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentField.toString())
            if (currentRow.any { it.isNotBlank() }) {
                records.add(ArrayList(currentRow))
            }
        }

        return records
    }
}
