package com.payme.sdk.network_requests

import com.payme.sdk.runtime.PayMEConfig
import org.json.JSONObject
import kotlin.random.Random

internal data class EncryptedNetworkRequest(
    val body: JSONObject,
    val headers: Map<String, String>
)

internal class EncryptedNetworkRequestBuilder(
    private val config: PayMEConfig,
    private val randomKeyProvider: () -> String = { Random.nextInt(0, 10000000).toString() }
) {
    fun build(
        apiPath: String,
        token: String,
        params: MutableMap<String, String>
    ): EncryptedNetworkRequest {
        val cryptoRSA = CryptoRSA(config.publicKey, config.privateKey)
        val encryptionKey = randomKeyProvider()
        val encryptedKey = cryptoRSA.encrypt(encryptionKey)
        val cryptoAES = CryptoAES()
        val encryptedAction = cryptoAES.encryptAES(encryptionKey, apiPath)
        val encryptedMessage = cryptoAES.encryptAES(
            encryptionKey,
            JSONObject(params as Map<*, *>).toString()
        )
        val validationString = encryptedAction + "POST" + token + encryptedMessage + encryptionKey
        val validationHash = cryptoAES.getMD5(validationString)

        return EncryptedNetworkRequest(
            body = JSONObject(mapOf("x-api-message" to encryptedMessage)),
            headers = mapOf(
                "Authorization" to token,
                "Accept" to "application/json",
                "Content-Type" to "application/json",
                "x-api-client" to config.appId,
                "x-api-key" to encryptedKey,
                "x-api-action" to encryptedAction,
                "x-api-validate" to validationHash
            )
        )
    }
}
