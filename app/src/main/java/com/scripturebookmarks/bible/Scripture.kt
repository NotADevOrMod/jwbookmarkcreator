package com.scripturebookmarks.bible

data class Reference(val book: Book, val chapter: Int, val verseStart: Int, val verseEnd: Int? = null) {
    val display: String
        get() = buildString {
            append("${book.name} $chapter:$verseStart")
            if (verseEnd != null) append("–$verseEnd")
        }
}

object ScriptureParser {
    // "John 3:16", "1 Cor 13:4-7", "Ps 23" (a bare chapter points at verse 1)
    private val rx = Regex("""^\s*([1-3]?\s*[A-Za-z][A-Za-z .]*?)\s*(\d+)(?:\s*:\s*(\d+)(?:\s*[-–]\s*(\d+))?)?\s*$""")

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private val keys: List<Pair<String, Book>> =
        BibleBooks.all.flatMap { b -> (listOf(b.name) + b.abbrs).map { norm(it) to b } }

    fun findBook(input: String): Book? {
        val n = norm(input)
        if (n.isEmpty()) return null
        keys.firstOrNull { it.first == n }?.let { return it.second }
        return keys.filter { it.first.startsWith(n) }.map { it.second }.distinct().singleOrNull()
    }

    fun parse(text: String): Reference? {
        val m = rx.matchEntire(text) ?: return null
        val book = findBook(m.groupValues[1]) ?: return null
        val first = m.groupValues[2].toIntOrNull() ?: return null
        val v1 = m.groupValues[3]
        val v2 = m.groupValues[4].toIntOrNull()

        // One-chapter books (Jude 5) treat the number as a verse.
        if (book.chapters == 1 && v1.isEmpty()) return Reference(book, 1, first).takeIf { first >= 1 }
        if (first !in 1..book.chapters) return null
        if (v1.isEmpty()) return Reference(book, first, 1)

        val start = v1.toIntOrNull() ?: return null
        if (start < 1) return null
        if (v2 != null && v2 < start) return null
        return Reference(book, first, start, v2?.takeIf { it > start })
    }
}

object LinkBuilder {
    // jw.org finder ids are BBCCCVVV: book (2 digits), chapter (3), verse (3).
    private fun code(b: Int, c: Int, v: Int) = "%02d%03d%03d".format(b, c, v)

    private fun finderId(r: Reference): String {
        val start = code(r.book.number, r.chapter, r.verseStart)
        val end = r.verseEnd?.let { code(r.book.number, r.chapter, it) }
        return if (end != null) "$start-$end" else start
    }

    const val LOCALE = "E" // English. Change to your language code.

    fun appLink(r: Reference) = "jwlibrary:///finder?wtlocale=$LOCALE&bible=${finderId(r)}"
    fun webLink(r: Reference) = "https://www.jw.org/finder?wtlocale=$LOCALE&bible=${finderId(r)}"
}
