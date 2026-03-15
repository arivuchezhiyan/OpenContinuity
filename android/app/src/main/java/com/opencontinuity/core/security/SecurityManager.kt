package com.opencontinuity.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.security.*
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private val Context.securityDataStore: DataStore<Preferences> by preferencesDataStore(name = "security_prefs")

/**
 * Security Manager handles all cryptographic operations
 * - Key pair generation and storage
 * - Encryption/Decryption
 * - Device pairing and authentication
 * - Session token management
 */
class SecurityManager(private val context: Context) {

    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    companion object {
        private const val KEY_ALIAS = "opencontinuity_keypair"
        private const val AES_KEY_ALIAS = "opencontinuity_aes"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128

        // DataStore keys
        private val DEVICE_ID_KEY = stringPreferencesKey("device_id")
        private val PAIRED_DEVICES_KEY = stringPreferencesKey("paired_devices")
    }

    // Device ID - generated once and persisted
    val deviceId: String by lazy {
        runBlocking {
            getOrCreateDeviceId()
        }
    }

    private suspend fun getOrCreateDeviceId(): String {
        val stored = context.securityDataStore.data.map { prefs ->
            prefs[DEVICE_ID_KEY]
        }.first()

        return stored ?: run {
            val newId = java.util.UUID.randomUUID().toString()
            context.securityDataStore.edit { prefs ->
                prefs[DEVICE_ID_KEY] = newId
            }
            newId
        }
    }

    /**
     * Initialize or retrieve the device's key pair
     */
    fun initializeKeyPair(): KeyPair {
        return if (keyStore.containsAlias(KEY_ALIAS)) {
            val privateKey = keyStore.getKey(KEY_ALIAS, null) as PrivateKey
            val publicKey = keyStore.getCertificate(KEY_ALIAS).publicKey
            KeyPair(publicKey, privateKey)
        } else {
            generateKeyPair()
        }
    }

    private fun generateKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            "AndroidKeyStore"
        )

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
            .build()

        keyPairGenerator.initialize(spec)
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Get the public key encoded as Base64 string for sharing
     */
    fun getPublicKeyBase64(): String {
        val keyPair = initializeKeyPair()
        return Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
    }

    /**
     * Parse a Base64 encoded public key
     */
    fun parsePublicKey(base64Key: String): PublicKey {
        val keyBytes = Base64.decode(base64Key, Base64.NO_WRAP)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePublic(keySpec)
    }

    /**
     * Generate a random pairing code (6 digits)
     */
    fun generatePairingCode(): String {
        val random = SecureRandom()
        return String.format("%06d", random.nextInt(1000000))
    }

    /**
     * Generate a secure session token
     */
    fun generateSessionToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Derive a shared secret from ECDH key exchange.
     * Uses HKDF (RFC 5869) to expand the raw ECDH secret into a 256-bit AES key.
     * Parameters mirror the Windows side so keys are symmetric.
     */
    fun deriveSharedSecret(peerPublicKey: PublicKey): SecretKey {
        val keyPair = initializeKeyPair()
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(keyPair.private)
        keyAgreement.doPhase(peerPublicKey, true)

        val rawSecret = keyAgreement.generateSecret()
        val derivedKey = hkdf(
            ikm = rawSecret,
            salt = "opencontinuity-v2".toByteArray(Charsets.UTF_8),
            info = "aes-256-gcm encryption key".toByteArray(Charsets.UTF_8),
            length = 32
        )
        return SecretKeySpec(derivedKey, "AES")
    }

    /**
     * RFC 5869 HKDF-Extract + HKDF-Expand (single block, length ≤ 32).
     * Available without any external dependency on all supported Android API levels.
     */
    private fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int = 32): ByteArray {
        // Extract
        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = hmac.doFinal(ikm)
        // Expand (one block is always enough for AES-256)
        hmac.init(SecretKeySpec(prk, "HmacSHA256"))
        hmac.update(info)
        hmac.update(0x01.toByte())
        return hmac.doFinal().copyOf(length)
    }

    /**
     * Encrypt data using AES-GCM
     */
    fun encrypt(data: ByteArray, secretKey: SecretKey): EncryptedData {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)

        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val cipherText = cipher.doFinal(data)

        return EncryptedData(
            iv = Base64.encodeToString(iv, Base64.NO_WRAP),
            cipherText = Base64.encodeToString(cipherText, Base64.NO_WRAP)
        )
    }

    /**
     * Decrypt data using AES-GCM
     */
    fun decrypt(encryptedData: EncryptedData, secretKey: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = Base64.decode(encryptedData.iv, Base64.NO_WRAP)
        val cipherText = Base64.decode(encryptedData.cipherText, Base64.NO_WRAP)

        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        return cipher.doFinal(cipherText)
    }

    /**
     * Sign data using device's private key
     */
    fun sign(data: ByteArray): String {
        val keyPair = initializeKeyPair()
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(keyPair.private)
        signature.update(data)
        val signatureBytes = signature.sign()
        return Base64.encodeToString(signatureBytes, Base64.NO_WRAP)
    }

    /**
     * Verify signature using peer's public key
     */
    fun verify(data: ByteArray, signatureBase64: String, publicKey: PublicKey): Boolean {
        return try {
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initVerify(publicKey)
            signature.update(data)
            val signatureBytes = Base64.decode(signatureBase64, Base64.NO_WRAP)
            signature.verify(signatureBytes)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Generate a hash for message integrity
     */
    fun hash(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data)
        return Base64.encodeToString(hashBytes, Base64.NO_WRAP)
    }
}

/**
 * Container for encrypted data with IV
 */
data class EncryptedData(
    val iv: String,
    val cipherText: String
)

/**
 * Represents a paired device
 */
data class PairedDevice(
    val deviceId: String,
    val deviceName: String,
    val publicKey: String,
    val sessionToken: String,
    val pairedAt: Long = System.currentTimeMillis(),
    val lastConnected: Long? = null
)
