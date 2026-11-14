package com.example.domain

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class HashedPassword(
    val hashBase64: String,
    val saltBase64: String,
    val parameters: String
)

object PasswordHasher {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 65_536
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16
    private val secureRandom = SecureRandom()

    fun hashPassword(password: CharArray): HashedPassword {
        val salt = ByteArray(SALT_BYTES)
        secureRandom.nextBytes(salt)
        val derived = deriveKey(password, salt, ITERATIONS, KEY_LENGTH_BITS)
        return HashedPassword(
            hashBase64 = Base64.getEncoder().encodeToString(derived),
            saltBase64 = Base64.getEncoder().encodeToString(salt),
            parameters = "$ALGORITHM:$ITERATIONS:$KEY_LENGTH_BITS"
        )
    }

    fun verifyPassword(
        password: CharArray,
        expectedHashBase64: String,
        saltBase64: String,
        parameters: String
    ): Boolean {
        return try {
            val parts = parameters.split(":")
            val algo = parts.getOrNull(0) ?: ALGORITHM
            val iterations = parts.getOrNull(1)?.toIntOrNull() ?: ITERATIONS
            val keyLen = parts.getOrNull(2)?.toIntOrNull() ?: KEY_LENGTH_BITS
            val salt = Base64.getDecoder().decode(saltBase64)
            val expectedHash = Base64.getDecoder().decode(expectedHashBase64)
            val actualHash = deriveKey(password, salt, iterations, keyLen, algo)
            MessageDigest.isEqual(expectedHash, actualHash)
        } catch (_: Exception) {
            false
        }
    }

    private fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        keyLengthBits: Int,
        algorithm: String = ALGORITHM
    ): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, keyLengthBits)
        val factory = SecretKeyFactory.getInstance(algorithm)
        return factory.generateSecret(spec).encoded
    }
}
