package com.example.shared

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PublicKey
import java.security.PrivateKey
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

class CryptoHelper(private val keyAlias: String) {

    private val androidKeyStore = "AndroidKeyStore"

    fun generateRSAKeyIfNeeded() {
        val ks = KeyStore.getInstance(androidKeyStore)
        ks.load(null)
        if (!ks.containsAlias(keyAlias)) {
            val kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA, androidKeyStore)
            val spec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
            kpg.initialize(spec)
            kpg.generateKeyPair()
        }
    }

    fun getRSAPublicKey(): PublicKey {
        val ks = KeyStore.getInstance(androidKeyStore)
        ks.load(null)
        return ks.getCertificate(keyAlias).publicKey
    }

    fun getRSAPrivateKey(): PrivateKey {
        val ks = KeyStore.getInstance(androidKeyStore)
        ks.load(null)
        return ks.getKey(keyAlias, null) as PrivateKey
    }

    fun encryptWithRSA(publicKeyBytes: ByteArray, data: ByteArray): ByteArray {
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(publicKeyBytes))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return cipher.doFinal(data)
    }

    fun decryptWithRSA(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, getRSAPrivateKey())
        return cipher.doFinal(data)
    }

    fun storeAESKeyIfNeeded(rawAES: ByteArray) {
        val ks = KeyStore.getInstance(androidKeyStore)
        ks.load(null)
        if (!ks.containsAlias(keyAlias)) {
            val spec = SecretKeySpec(rawAES, "AES")
            // Note: AndroidKeyStore doesn't allow importing raw AES keys directly.
            // For real production, you'd wrap it with RSA or use AES + derived keys.
            // For this assignment, use SecretKeySpec directly.
        }
    }

    fun generateRawAESKey(): ByteArray {
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES)
        keyGen.init(128)
        return keyGen.generateKey().encoded
    }

    fun aesEncrypt(rawAES: ByteArray, plainText: String): ByteArray {
        val secretKey = SecretKeySpec(rawAES, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return iv + cipherText
    }

    fun aesDecrypt(rawAES: ByteArray, cipherMessage: ByteArray): String {
        val secretKey = SecretKeySpec(rawAES, "AES")
        val iv = cipherMessage.sliceArray(0 until 12)
        val cipherText = cipherMessage.sliceArray(12 until cipherMessage.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val plain = cipher.doFinal(cipherText)
        return String(plain)
    }
}
