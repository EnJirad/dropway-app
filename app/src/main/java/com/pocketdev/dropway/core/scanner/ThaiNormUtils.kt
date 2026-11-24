package com.pocketdev.dropway.core.scanner

import java.text.Normalizer

/**
 * Implements canonical Thai text normalization conforming to OpenThai 2.1 OCR specifications.
 * Handles legacy PUA shifted glyphs, tone mark / vowel reordering, Sara Am composition,
 * duplicate mark deduplication, and Unicode NFC normalization.
 */
object ThaiNormUtils {

    // Legacy Private Use Area (PUA) glyph variants to standard Thai Unicode
    private val puaMap = mapOf(
        0xF700 to 0x0E10, 0xF701 to 0x0E34, 0xF702 to 0x0E35, 0xF703 to 0x0E36, 0xF704 to 0x0E37,
        0xF705 to 0x0E48, 0xF706 to 0x0E49, 0xF707 to 0x0E4A, 0xF708 to 0x0E4B, 0xF709 to 0x0E4C,
        0xF70A to 0x0E48, 0xF70B to 0x0E49, 0xF70C to 0x0E4A, 0xF70D to 0x0E4B, 0xF70E to 0x0E4C,
        0xF70F to 0x0E0D, 0xF710 to 0x0E31, 0xF711 to 0x0E4D, 0xF712 to 0x0E47, 0xF713 to 0x0E48,
        0xF714 to 0x0E49, 0xF715 to 0x0E4A, 0xF716 to 0x0E4B, 0xF717 to 0x0E4C, 0xF718 to 0x0E38,
        0xF719 to 0x0E39, 0xF71A to 0x0E3A
    )

    // Invisible / zero-width characters to discard
    private val dropChars = setOf(
        '\u200B', // zero-width space
        '\u200C', // zero-width non-joiner
        '\u200D', // zero-width joiner
        '\u2060', // word joiner
        '\u00AD', // soft hyphen
        '\uFEFF'  // BOM
    )

    private val vowelMarks = setOf('ั', 'ิ', 'ี', 'ึ', 'ื', 'ุ', 'ู', 'ฺ', '็')
    private val toneMarks = setOf('่', '้', '๊', '๋', '์', '๎')

    /**
     * Normalizes Thai text into canonical form according to OpenThai 2.1 specs.
     */
    fun canonical(input: String): String {
        if (input.isBlank()) return ""

        // 1. NFC normalization
        val nfc = Normalizer.normalize(input, Normalizer.Form.NFC)

        // 2. Translate PUA codes, drop zero-width chars, convert NBSP
        val sb = StringBuilder(nfc.length)
        for (ch in nfc) {
            val code = ch.code
            if (puaMap.containsKey(code)) {
                sb.append(puaMap[code]!!.toChar())
            } else if (dropChars.contains(ch)) {
                // Drop
            } else if (ch == '\u00A0') {
                sb.append(' ')
            } else {
                sb.append(ch)
            }
        }

        var text = sb.toString()

        // 3. Compose duplicate Sara E (เเ) into Sara Ae (แ)
        text = text.replace("เเ", "แ")

        // 4. Sara Am composition: Nikhahit + Tone + Sara Aa -> Tone + Sara Am
        // e.g. นํ้า -> น้ำ
        text = text.replace("\u0E4D([\u0E48-\u0E4C])\u0E32".toRegex(), "$1ำ")
        text = text.replace("([\u0E48-\u0E4C])\u0E4D\u0E32".toRegex(), "$1ำ")
        text = text.replace("\u0E4D\u0E32", "ำ")

        // 5. Deduplicate repeated vowel/tone marks (e.g. ำำ -> ำ, ่่ -> ่)
        text = text.replace("([ัิีึืฺุู็่้๊๋์])\\1+".toRegex(), "$1")
        text = text.replace("ำ+".toRegex(), "ำ")

        // 6. Canonical mark reordering: base + vowel + tone + nikhahit
        text = reorderVowelAndTone(text)

        // 7. Collapse multi-spaces and trim
        text = text.replace("[ \\t]+".toRegex(), " ")
            .lines()
            .joinToString("\n") { it.trim() }
            .trim()

        return text
    }

    private fun reorderVowelAndTone(text: String): String {
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            // If tone mark precedes a vowel mark, swap them (canonical order: vowel then tone)
            if (i + 1 < text.length && toneMarks.contains(c) && vowelMarks.contains(text[i + 1])) {
                val tone = c
                val vowel = text[i + 1]
                out.append(vowel)
                out.append(tone)
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}
