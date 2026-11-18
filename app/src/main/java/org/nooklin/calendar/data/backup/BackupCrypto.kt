package org.nooklin.calendar.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.text.Normalizer
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** The file is not a backup of this app, is damaged, or comes from a newer file format. */
class BackupFormatException(message: String) : Exception(message)

/** Decrypting failed: the password or PIN is wrong, or the file was changed. The two cannot be told apart. */
class WrongSecretException : Exception("Wrong password or PIN, or the file is damaged.")

/**
 * The encrypted file of a backup. Layout:
 * 4 bytes "NKB" and the file format version, 4 bytes the number of PBKDF2 rounds, 16 bytes salt, 12 bytes IV,
 * then the data (compressed JSON) encrypted with AES-256-GCM. The first 24 bytes are authenticated too.
 * The key is made from the password or PIN with PBKDF2-HMAC-SHA256.
 */
object BackupCrypto {
    const val FORMAT_VERSION = 1
    const val ROUNDS = 600_000

    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128
    private const val HEADER_SIZE = 4 + 4 + SALT_SIZE
    private const val MIN_ROUNDS = 1
    private const val MAX_ROUNDS = 5_000_000
    private val MAGIC = byteArrayOf('N'.code.toByte(), 'K'.code.toByte(), 'B'.code.toByte())

    fun encrypt(plain: ByteArray, secret: String, rounds: Int = ROUNDS, random: SecureRandom = SecureRandom()): ByteArray {
        require(secret.isNotEmpty()) { "The password or PIN is empty." }
        val salt = ByteArray(SALT_SIZE).also { random.nextBytes(it) }
        val iv = ByteArray(IV_SIZE).also { random.nextBytes(it) }
        val header = ByteBuffer.allocate(HEADER_SIZE).put(MAGIC).put(FORMAT_VERSION.toByte()).putInt(rounds).put(salt).array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key(secret, salt, rounds), "AES"), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(header)
        return header + iv + cipher.doFinal(gzip(plain))
    }

    fun decrypt(file: ByteArray, secret: String): ByteArray {
        if (file.size < HEADER_SIZE + IV_SIZE + TAG_BITS / 8 || !file.copyOfRange(0, 3).contentEquals(MAGIC)) {
            throw BackupFormatException("This is not a Nooklin Calendar backup.")
        }
        val version = file[3].toInt()
        if (version > FORMAT_VERSION) {
            throw BackupFormatException("This backup was made by a newer version of the app. Update the app to read it.")
        }
        if (version < 1) throw BackupFormatException("This is not a Nooklin Calendar backup.")
        val buffer = ByteBuffer.wrap(file)
        buffer.position(4)
        val rounds = buffer.getInt()
        if (rounds < MIN_ROUNDS || rounds > MAX_ROUNDS) throw BackupFormatException("The backup file is damaged.")
        val salt = ByteArray(SALT_SIZE).also { buffer.get(it) }
        val iv = ByteArray(IV_SIZE).also { buffer.get(it) }
        if (secret.isEmpty()) throw WrongSecretException()
        val plain = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key(secret, salt, rounds), "AES"), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(file, 0, HEADER_SIZE)
            cipher.doFinal(file, HEADER_SIZE + IV_SIZE, file.size - HEADER_SIZE - IV_SIZE)
        } catch (e: GeneralSecurityException) {
            throw WrongSecretException()
        }
        return try {
            gunzip(plain)
        } catch (e: java.io.IOException) {
            throw BackupFormatException("The backup file is damaged.")
        }
    }

    private fun key(secret: String, salt: ByteArray, rounds: Int): ByteArray =
        pbkdf2(Normalizer.normalize(secret, Normalizer.Form.NFC).toByteArray(Charsets.UTF_8), salt, rounds)

    /** PBKDF2 with HMAC-SHA256, one block of 32 bytes (all that AES-256 needs). */
    internal fun pbkdf2(password: ByteArray, salt: ByteArray, rounds: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password, "HmacSHA256"))
        mac.update(salt)
        mac.update(byteArrayOf(0, 0, 0, 1))
        var block = mac.doFinal()
        val result = block.copyOf()
        for (i in 2..rounds) {
            block = mac.doFinal(block)
            for (j in result.indices) result[j] = (result[j].toInt() xor block[j].toInt()).toByte()
        }
        return result
    }

    private fun gzip(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(data) }
        return out.toByteArray()
    }

    private fun gunzip(data: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
}
