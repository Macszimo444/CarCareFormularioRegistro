package com.example.carcareformularioregistro.utils

object ProfileValidation {
    fun name(value: String): Boolean = value.trim().let {
        it.length in 1..60 && it.any(Char::isLetter) && it.all { c -> c.isLetter() || c.isWhitespace() || c in "'-’" }
    }
    /** Optional Mexico contact; normalizes display punctuation and an explicit +52 prefix. */
    fun phone(value: String): String? {
        val text = value.trim()
        if (text.isEmpty()) return ""
        if (text.any { !it.isDigit() && it !in "+()- " }) return null
        val compact = text.filterNot { it in "()- " }
        val digits = if (compact.startsWith("+52")) compact.drop(3) else compact
        return if (digits.length == 10 && digits.all { it in '0'..'9' }) digits else null
    }
}
