package com.payme.sdk.network_requests

import com.payme.sdk.PayMEMiniApp
import java.math.BigInteger
import java.nio.charset.StandardCharsets.UTF_8
import java.security.InvalidKeyException
import java.security.KeyFactory
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPrivateKeySpec
import java.security.spec.RSAPublicKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.NoSuchPaddingException

class CryptoRSA @JvmOverloads @Throws(NoSuchAlgorithmException::class, InvalidKeySpecException::class) constructor(
    publicKey: String = PayMEMiniApp.publicKey,
    privateKey: String = PayMEMiniApp.privateKey
) {
    private var publicKey: PublicKey = stringToPublicKey(publicKey)
    private var privateKey: PrivateKey = stringToPrivateKey(privateKey)

    @Throws(
        NoSuchAlgorithmException::class,
        NoSuchPaddingException::class,
        InvalidKeyException::class,
        IllegalBlockSizeException::class,
        BadPaddingException::class
    )
    fun encrypt(vararg args: Any): String {
        val plain = args[0] as String
        val rsaPublicKey = if (args.size == 1) {
            publicKey
        } else {
            args[1] as PublicKey
        }
        val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey)
        val encryptedBytes = cipher.doFinal(plain.toByteArray(UTF_8))
        return Base64.getEncoder().encodeToString(encryptedBytes)
    }

    @Throws(
        NoSuchAlgorithmException::class,
        NoSuchPaddingException::class,
        InvalidKeyException::class,
        IllegalBlockSizeException::class,
        BadPaddingException::class,
        InvalidKeySpecException::class
    )
    fun encryptWebView(key: String, plainText: String): String {
        val rsaPublicKey = stringToPublicKey(key)
        val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey)
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(UTF_8))
        return Base64.getEncoder().encodeToString(encryptedBytes)
    }

    @Throws(
        NoSuchAlgorithmException::class,
        NoSuchPaddingException::class,
        InvalidKeyException::class,
        IllegalBlockSizeException::class,
        BadPaddingException::class
    )
    fun decrypt(result: String, vararg args: PrivateKey): String {
        val rsaPrivateKey = if (args.size == 1) args[0] else privateKey
        val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, rsaPrivateKey)
        val decoded = Base64.getMimeDecoder().decode(result)
        val decryptedBytes = cipher.doFinal(decoded)
        return String(decryptedBytes, UTF_8)
    }

    companion object {
        private const val RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA1AndMGF1Padding"

        @JvmStatic
        @Throws(NoSuchAlgorithmException::class, InvalidKeySpecException::class)
        fun stringToPrivateKey(privateKeyPEM: String): PrivateKey {
            val normalizedKey = privateKeyPEM
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
            val encoded = Base64.getMimeDecoder().decode(normalizedKey)
            val keyFactory = KeyFactory.getInstance("RSA")
            return keyFactory.generatePrivate(PKCS8EncodedKeySpec(encoded))
        }

        @JvmStatic
        @Throws(NoSuchAlgorithmException::class, InvalidKeySpecException::class)
        fun stringToPublicKey(publicKeyPEM: String): PublicKey {
            val normalizedKey = publicKeyPEM
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
            val encoded = Base64.getMimeDecoder().decode(normalizedKey)
            val keyFactory = KeyFactory.getInstance("RSA")
            return keyFactory.generatePublic(X509EncodedKeySpec(encoded))
        }

        @JvmStatic
        @Throws(NoSuchAlgorithmException::class, InvalidKeySpecException::class)
        fun getPublicKey(privateKey: PrivateKey): PublicKey {
            val keyFactory = KeyFactory.getInstance("RSA")
            val privateKeySpec = keyFactory.getKeySpec(privateKey, RSAPrivateKeySpec::class.java)
            val publicKeySpec = RSAPublicKeySpec(privateKeySpec.modulus, BigInteger.valueOf(65537))
            return keyFactory.generatePublic(publicKeySpec)
        }
    }
}
