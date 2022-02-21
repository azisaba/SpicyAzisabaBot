package net.azisaba.spicyAzisabaBot.util

class StringReader(private val text: String) {
    var index = 0

    fun peek(): Char = text[index]

    fun peekString(): String = peek().toString()

    fun read(): String = text.substring(index)

    fun read(amount: Int): String {
        val string = text.substring(index, index + amount)
        index += amount
        return string
    }

    fun startsWith(prefix: String): Boolean = read().startsWith(prefix)

    fun skip(amount: Int): StringReader {
        index += amount
        return this
    }

    fun isEOF(): Boolean = index >= text.length
}