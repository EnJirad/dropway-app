package com.pocketdev.dropway.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import android.net.Uri
import com.pocketdev.dropway.core.util.PhoneUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
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

    suspend fun importFromUri(
        context: Context,
        dbHelper: DropwayDatabaseHelper,
        uri: Uri,
        clearExisting: Boolean = false
    ): ImportResult = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: return@withContext ImportResult(0, 0, 0, 0)
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
        val rawHeader = rows[0]
        val header = rawHeader.map { it.trim().lowercase().removePrefix("\uFEFF") }

        val idIdx = findColumnIndex(header, listOf("id", "no", "no.", "num", "ลำดับ", "ลำดับที่", "code"), listOf("id", "ลำดับ"))
        val nameIdx = findColumnIndex(header, listOf("full_name", "fullname", "name", "customer_name", "receiver_name", "recipient", "ชื่อ", "ชื่อผู้รับ", "ชื่อ-สกุล", "ชื่อ-นามสกุล", "ผู้รับ", "ชื่อผู้รับพัสดุ"), listOf("name", "ชื่อ", "ผู้รับ"))
        val phoneIdx = findColumnIndex(header, listOf("phone", "phone_number", "phonenumber", "tel", "telephone", "mobile", "contact", "เบอร์", "เบอร์โทร", "เบอร์โทรศัพท์", "โทร"), listOf("phone", "tel", "โทร", "เบอร์"))
        val addrIdx = findColumnIndex(header, listOf("address", "addr", "full_address", "ที่อยู่", "ที่อยู่จัดส่ง", "ที่อยู่ผู้รับ"), listOf("address", "addr", "ที่อยู่"))
        val houseNumIdx = findColumnIndex(header, listOf("house_number", "house_no", "housenumber", "houseno", "บ้านเลขที่", "เลขที่"), listOf("house_number", "บ้านเลขที่"))
        val villageIdx = findColumnIndex(header, listOf("village", "moo", "หมู่", "หมู่ที่", "ม.", "หมู่บ้าน"), listOf("village", "moo", "หมู่"))
        val subdistIdx = findColumnIndex(header, listOf("subdistrict", "sub_district", "tambon", "ตำบล", "ต.", "แขวง"), listOf("subdistrict", "tambon", "ตำบล"))
        val distIdx = findColumnIndex(header, listOf("district", "amphoe", "amphur", "อำเภอ", "อ.", "เขต"), listOf("district", "amphoe", "อำเภอ"))
        val provIdx = findColumnIndex(header, listOf("province", "changwat", "จังหวัด", "จ."), listOf("province", "จังหวัด"))
        val postalIdx = findColumnIndex(header, listOf("postal_code", "postalcode", "postcode", "zip", "zipcode", "zip_code", "รหัสไปรษณีย์"), listOf("postal", "zip", "ไปรษณีย์"))
        val latIdx = findColumnIndex(header, listOf("lat", "latitude", "ละติจูด", "พิกัดละติจูด", "y"), listOf("lat", "ละติจูด"))
        val lngIdx = findColumnIndex(header, listOf("lng", "lon", "long", "longitude", "ลองจิจูด", "พิกัดลองจิจูด", "x"), listOf("lng", "lon", "longitude", "ลองจิจูด"))
        val gpsIdx = findColumnIndex(header, listOf("gps", "coord", "coords", "coordinate", "coordinates", "location", "พิกัด", "ตำแหน่ง"), listOf("gps", "coord", "พิกัด"))
        val noteIdx = findColumnIndex(header, listOf("note", "notes", "remark", "remarks", "comment", "detail", "หมายเหตุ", "โน้ต", "จุดสังเกต"), listOf("note", "remark", "หมายเหตุ", "โน้ต"))

        val hasRecognizedHeader = nameIdx >= 0 || phoneIdx >= 0 || addrIdx >= 0 || latIdx >= 0 || houseNumIdx >= 0
        val startRow = if (hasRecognizedHeader) 1 else 0

        val effIdIdx = if (hasRecognizedHeader && idIdx >= 0) idIdx else 0
        val effNameIdx = if (hasRecognizedHeader && nameIdx >= 0) nameIdx else 1
        val effPhoneIdx = if (hasRecognizedHeader && phoneIdx >= 0) phoneIdx else 2
        val effAddrIdx = if (hasRecognizedHeader && addrIdx >= 0) addrIdx else 3
        val effLatIdx = if (hasRecognizedHeader && latIdx >= 0) latIdx else 4
        val effLngIdx = if (hasRecognizedHeader && lngIdx >= 0) lngIdx else 5
        val effNoteIdx = if (hasRecognizedHeader && noteIdx >= 0) noteIdx else 6

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
            val residents: MutableList<Pair<String, String>> = mutableListOf()
        )

        val houseMap = linkedMapOf<String, HouseGrouping>()
        var parsedCount = 0

        for (i in startRow until rows.size) {
            val r = rows[i]
            if (r.isEmpty() || r.all { it.isBlank() }) continue
            parsedCount++

            val id = if (effIdIdx < r.size) sanitizeText(r[effIdIdx]) else ""
            val name = if (effNameIdx < r.size) sanitizeText(r[effNameIdx]) else ""
            val phone = if (effPhoneIdx < r.size) PhoneUtils.normalize(sanitizeText(r[effPhoneIdx])) else ""
            val rawAddr = if (effAddrIdx < r.size) sanitizeText(r[effAddrIdx]) else ""
            val note = if (effNoteIdx < r.size) sanitizeText(r[effNoteIdx]) else ""

            // Coordinates
            var lat: Double? = null
            var lng: Double? = null
            if (effLatIdx < r.size) {
                lat = r[effLatIdx].trim().toDoubleOrNull()
            }
            if (effLngIdx < r.size) {
                lng = r[effLngIdx].trim().toDoubleOrNull()
            }
            if ((lat == null || lng == null) && gpsIdx >= 0 && gpsIdx < r.size) {
                val gpsStr = r[gpsIdx].trim()
                val parts = gpsStr.split(Pattern.compile("[,\\s]+"))
                if (parts.size >= 2) {
                    lat = parts[0].toDoubleOrNull()
                    lng = parts[1].toDoubleOrNull()
                }
            }

            // Separate address fields if available
            val rawHouseNum = if (houseNumIdx >= 0 && houseNumIdx < r.size) sanitizeText(r[houseNumIdx]) else ""
            val rawVillage = if (villageIdx >= 0 && villageIdx < r.size) sanitizeText(r[villageIdx]).removePrefix("หมู่").removePrefix("ม.").trim() else ""
            val rawSubdist = if (subdistIdx >= 0 && subdistIdx < r.size) sanitizeText(r[subdistIdx]).removePrefix("ตำบล").removePrefix("ต.").trim() else ""
            val rawDist = if (distIdx >= 0 && distIdx < r.size) sanitizeText(r[distIdx]).removePrefix("อำเภอ").removePrefix("อ.").trim() else ""
            val rawProv = if (provIdx >= 0 && provIdx < r.size) sanitizeText(r[provIdx]).removePrefix("จังหวัด").removePrefix("จ.").trim() else ""
            val rawPostal = if (postalIdx >= 0 && postalIdx < r.size) sanitizeText(r[postalIdx]) else ""

            val parsedAddr = if (rawAddr.isNotBlank()) {
                val p = parseThaiAddress(rawAddr, name, note)
                p.copy(
                    houseNumber = if (rawHouseNum.isNotBlank()) rawHouseNum else p.houseNumber,
                    village = if (rawVillage.isNotBlank()) rawVillage else p.village,
                    subdistrict = if (rawSubdist.isNotBlank()) rawSubdist else p.subdistrict,
                    district = if (rawDist.isNotBlank()) rawDist else p.district,
                    province = if (rawProv.isNotBlank()) rawProv else p.province,
                    postalCode = if (rawPostal.isNotBlank()) rawPostal else p.postalCode
                )
            } else {
                val hn = if (rawHouseNum.isNotBlank()) rawHouseNum else {
                    when {
                        note.isNotBlank() -> note.split("\\s+".toRegex()).firstOrNull()?.take(25) ?: "จุดส่งพัสดุ"
                        name.isNotBlank() -> name.split("\\s+".toRegex()).firstOrNull()?.take(25) ?: "จุดส่งพัสดุ"
                        else -> "จุดส่งพัสดุ"
                    }
                }
                val descParts = mutableListOf<String>()
                if (hn.isNotBlank()) descParts.add("เลขที่ $hn")
                if (rawVillage.isNotBlank()) descParts.add("หมู่ $rawVillage")
                if (rawSubdist.isNotBlank()) descParts.add("ต.$rawSubdist")
                if (rawDist.isNotBlank()) descParts.add("อ.$rawDist")
                if (rawProv.isNotBlank()) descParts.add("จ.$rawProv")
                if (rawPostal.isNotBlank()) descParts.add(rawPostal)
                val desc = if (descParts.isNotEmpty()) descParts.joinToString(" ") else note.ifBlank { "จุดส่งพัสดุ" }
                ParsedAddressComponents(
                    houseNumber = hn,
                    village = rawVillage,
                    villageName = "",
                    subdistrict = rawSubdist,
                    district = rawDist,
                    province = rawProv,
                    postalCode = rawPostal,
                    addressDescription = desc
                )
            }

            val key = when {
                parsedAddr.houseNumber.isNotBlank() && parsedAddr.village.isNotBlank() -> {
                    "${parsedAddr.houseNumber}__M${parsedAddr.village}__$note"
                }
                rawAddr.isNotBlank() -> {
                    if (note.isNotBlank()) "$rawAddr || $note" else rawAddr
                }
                else -> {
                    "$id || $note || ${parsedAddr.houseNumber}"
                }
            }

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

    private fun findColumnIndex(header: List<String>, exactKeywords: List<String>, partialKeywords: List<String>): Int {
        for ((idx, col) in header.withIndex()) {
            val normalized = col.lowercase().trim().replace("\uFEFF", "")
            if (exactKeywords.any { it.equals(normalized, ignoreCase = true) }) {
                return idx
            }
        }
        for ((idx, col) in header.withIndex()) {
            val clean = col.lowercase().trim().replace("\uFEFF", "").replace("_", "").replace("-", "").replace(" ", "")
            if (exactKeywords.any { it.replace("_", "").replace("-", "").replace(" ", "").equals(clean, ignoreCase = true) }) {
                return idx
            }
        }
        for (kw in partialKeywords) {
            val kwClean = kw.lowercase().trim()
            for ((idx, col) in header.withIndex()) {
                val clean = col.lowercase().trim().replace("\uFEFF", "")
                if (clean.contains(kwClean)) {
                    return idx
                }
            }
        }
        return -1
    }

    private fun sanitizeText(input: String): String {
        if (input.isBlank()) return ""
        var s = input.replace("\u27a1", "->")
        s = s.replace("[\\uD800-\\uDBFF][\\uDC00-\\uDFFF]".toRegex(), "")
        s = s.replace("[\\u2600-\\u27BF\\u2B50-\\u2B55\\u203C\\u2049\\u2139\\u2194-\\u21AA]".toRegex(), "")
        return s.trim()
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
     * RFC 4180 compliant CSV parser supporting multi-line quoted fields,
     * UTF-8 BOM, and automatic delimiter detection (comma, semicolon, tab).
     */
    fun parseCsv(inputStream: InputStream): List<List<String>> {
        val bytes = inputStream.readBytes()
        if (bytes.isEmpty()) return emptyList()

        // Strip UTF-8 BOM if present
        val content = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        } else {
            String(bytes, Charsets.UTF_8)
        }

        val firstLine = content.lineSequence().firstOrNull() ?: ""
        var commaCount = 0
        var semicolonCount = 0
        var tabCount = 0
        var inQ = false
        for (ch in firstLine) {
            if (ch == '"') inQ = !inQ
            else if (!inQ) {
                when (ch) {
                    ',' -> commaCount++
                    ';' -> semicolonCount++
                    '\t' -> tabCount++
                }
            }
        }
        val delimiter = when {
            semicolonCount > commaCount && semicolonCount > tabCount -> ';'
            tabCount > commaCount && tabCount > semicolonCount -> '\t'
            else -> ','
        }

        val records = mutableListOf<List<String>>()
        val currentField = StringBuilder()
        val currentRow = mutableListOf<String>()
        var inQuotes = false

        var i = 0
        val len = content.length
        while (i < len) {
            val c = content[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < len && content[i + 1] == '"') {
                        currentField.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    currentField.append(c)
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    delimiter -> {
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
            i++
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
