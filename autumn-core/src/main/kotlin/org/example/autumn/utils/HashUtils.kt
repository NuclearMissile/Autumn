package org.example.autumn.utils

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@OptIn(ExperimentalStdlibApi::class)
object HashUtils {
    /**
     * Generate SHA-256 as hex string (all lower-case).
     *
     * @param input Input as String.
     * @return Hex string.
     */
    fun sha256(input: String): String {
        return sha256(input.toByteArray()).toHexString()
    }

    /**
     * Generate SHA-256 as ByteArray
     *
     * @param input Input as bytes.
     * @return ByteArray
     */
    fun sha256(input: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").also { it.update(input) }.digest()
    }

    /**
     * Do HMAC-SHA256.
     *
     * @return ByteArray
     */
    fun hmacSha256(data: ByteArray, key: ByteArray): ByteArray {
        return Mac.getInstance("HmacSHA256").also {
            it.init(SecretKeySpec(key, "HmacSHA256"))
            it.update(data)
        }.doFinal()
    }

    /**
     * Do HMAC-SHA256.
     *
     * @return Hex string
     */
    fun hmacSha256(data: String, key: String): String {
        return hmacSha256(data.toByteArray(), key.toByteArray()).toHexString()
    }
}
