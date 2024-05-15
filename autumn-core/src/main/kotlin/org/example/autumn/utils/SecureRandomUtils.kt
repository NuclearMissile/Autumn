package org.example.autumn.utils

import java.security.SecureRandom

object SecureRandomUtils {
    private val SECURE_RANDOM by lazy { SecureRandom() }

    // char ranges
    private const val ALPHABET_UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val ALPHABET_LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val DIGITS = "0123456789"
    private const val HEX_DIGITS = "0123456789abcdef"
    private const val WORDS = ALPHABET_UPPER + ALPHABET_LOWER + DIGITS

    /**
     * Create a random bytes with specific length.
     *
     * @param length Length of bytes.
     * @return Random bytes.
     */
    fun genRandomBytes(length: Int): ByteArray {
        val buffer = ByteArray(length)
        SECURE_RANDOM.nextBytes(buffer)
        return buffer
    }

    /**
     * Generate a secure random string with specific length based on char list.
     *
     * @param length   The length of random string.
     * @param charRange A string that holds chars.
     * @return Random string.
     */
    fun genRandomString(length: Int, charRange: String = WORDS): String {
        val buffer = CharArray(length)
        for (i in 0 until length) {
            buffer[i] = charRange[SECURE_RANDOM.nextInt(charRange.length)]
        }
        return String(buffer)
    }
}
