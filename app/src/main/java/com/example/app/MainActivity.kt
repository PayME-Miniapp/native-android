package com.example.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.models.ActionOpenMiniApp
import com.payme.sdk.models.DepositWithdrawTransferData
import com.payme.sdk.models.ENV
import com.payme.sdk.models.Locale
import com.payme.sdk.models.OpenMiniAppDataInterface
import com.payme.sdk.models.OpenMiniAppDepositData
import com.payme.sdk.models.OpenMiniAppKYCData
import com.payme.sdk.models.OpenMiniAppOpenData
import com.payme.sdk.models.OpenMiniAppPayMEData
import com.payme.sdk.models.OpenMiniAppPaymentData
import com.payme.sdk.models.OpenMiniAppPaymentDirectData
import com.payme.sdk.models.OpenMiniAppServiceData
import com.payme.sdk.models.OpenMiniAppTransferData
import com.payme.sdk.models.OpenMiniAppTransferQRData
import com.payme.sdk.models.OpenMiniAppType
import com.payme.sdk.models.OpenMiniAppWithdrawData
import com.payme.sdk.models.PayMEError
import com.payme.sdk.models.PaymentData
import com.payme.sdk.models.PaymentDirectData
import com.payme.sdk.models.ServiceData
import com.payme.sdk.models.TransferQRData
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var payMEMiniApp: PayMEMiniApp
    private lateinit var statusText: TextView
    private lateinit var currentCredential: ExampleCredential
    private var currentCredentialIndex: Int = DEFAULT_CREDENTIAL_INDEX
    private var currentEnv: ENV = ENV.PRODUCTION
    private var currentLocale: Locale = Locale.vi

    private data class ExampleCredential(
        val label: String,
        val appId: String,
        val publicKey: String,
        val privateKey: String,
        val defaultEnv: ENV
    )

    companion object {
        private const val PREF_NAME = "PAYME_WALLET"
        private const val PREF_CREDENTIAL_INDEX = "PAYME_WALLET_CREDENTIAL_INDEX"
        private const val PREF_ENV = "PAYME_WALLET_ENV"
        private const val PREF_LOCALE = "PAYME_WALLET_LOCALE"
        private const val DEFAULT_CREDENTIAL_INDEX = 1
        private const val SAMPLE_PHONE = "0795550300"
        private const val SAMPLE_BANK_NUMBER = "9704000000000018"
        private const val SAMPLE_SWIFT_CODE = "SBITVNVX"
        private val CREDENTIAL_PRESETS = listOf(
            ExampleCredential(
                label = "SANDBOX ecosystem 250069027220",
                appId = "250069027220",
                publicKey = "-----BEGIN PUBLIC KEY-----MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAIB6eR2SMUCqy7LkXmVF1xf37pJn5yCpGMGpOd6fc/dFkqIkNBDHoTRIhMdBucauf3i7S2g+fl6g+Kte4MlaYkcCAwEAAQ==-----END PUBLIC KEY-----",
                privateKey = "-----BEGIN RSA PRIVATE KEY-----MIIBOgIBAAJBAIB6eR2SMUCqy7LkXmVF1xf37pJn5yCpGMGpOd6fc/dFkqIkNBDHoTRIhMdBucauf3i7S2g+fl6g+Kte4MlaYkcCAwEAAQJAUlyxGfjnJBqZvRPTQ77y9cWWJjr/mxtr6HJwy7uSnvgNRY1zfpRLccR4NvMS7LtgK47sx1vJmCOgtVCGwCVUUQIhALlGTGM1Q4E5L2xCX0SfCY6vdKOdwvD5NyaUSP7ZJVf/AiEAsYXYoEApSorjtLg4JjLJhpE8H8Lf6o1AFpX9g83aNbkCICRY1zmLRIAAcP5DEx+KN7zHTRGgLJNLwPcPljZw8TOPAiEAg1P0XSD6KwYyzEgYadHamm2pIAoHorpaNhtCEBbinikCIFtk7by4tboFtUkXf7X+/Y1jX1owrT4xDO2sBKrUs/9F-----END RSA PRIVATE KEY-----",
                defaultEnv = ENV.SANDBOX
            ),
            ExampleCredential(
                label = "SANDBOX partner 143090043042",
                appId = "143090043042",
                publicKey = "-----BEGIN PUBLIC KEY-----MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAK//Ost3vYkmPXuAlIf0jVnfJjVraljwgY52hx6I1R3de1zLxIMOZPIYaDa+yxuMyyxNy/iDqcnE5GbKh/GHdn8CAwEAAQ==-----END PUBLIC KEY-----",
                privateKey = "-----BEGIN RSA PRIVATE KEY-----MIIBOgIBAAJBALnXgEAHajptceS3CA1PZVCypx4tkNTUdRsY+vgCwkzx21Qj/7g9FV7sVRvivUZ4mt/zbAHQtc5gm1bMIQZJa9UCAwEAAQJADH/qKGRXSMbDulZ1PC/y6JKbmvQFocsIdWIgvz2wQ+yDKfgwIWH9qP17dMvwUfd01dNai/LtK4IQNdLs+C3EAQIhAOYKusFQRcsCYauMcBXMfU6Ov7Byeam5g93AWyXRMUH1AiEAzs/0CkdR8emZvHocZ3tjcLbwzqZArlI8O/fTx+uG9mECIQCRI7Hi6Aew50bCWrAZQNTKrMwKwp86U578WTHo8Uy3xQIgMWu2SKKEbYfCKi0QDpaIy82bu/Y0rLOQG1B8tmCNLuECIFBKEdpz9mhjTDmL6yEyewBFWaD+GyY2C4WvvTI+N8tk-----END RSA PRIVATE KEY-----",
                defaultEnv = ENV.SANDBOX
            ),
            ExampleCredential(
                label = "STAGING ecosystem 264245066910",
                appId = "264245066910",
                publicKey = "-----BEGIN PUBLIC KEY-----MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAJoxQCA/D1qx2aUC65SxP+ZsCo/+YYVnV5pTeLgwCjgkZa1mac2q0vgWXvCamT9jjPBOhyuIX+wdHOGufwqtZQ8CAwEAAQ==-----END PUBLIC KEY-----",
                privateKey = "-----BEGIN RSA PRIVATE KEY-----MIIBOgIBAAJBAJtl/BFoCHwGCwfgiNKEJXkIQIpBnworMkX1I56UlcLkWhgug+KdxGPoTObufx8IHbBTQIl748uIDUpMjIPSL68CAwEAAQJAdZGoLsclvCeaSuBew97Etxg+NBvHpprd0z3PMBg8YhDQWIIo2kaqq7AfDmJU2Cb+SaTGIPCFZWP2lK60abiKIQIhAN6BCYNCu8kQ1rZ1FhGU5Ri9pvN3RSSnUpzE+h7GJsG/AiEAssrMYGxBLwU3S3l2zwlMc1tP/exEZWMjsboWRKlRLhECIQCJIZACVPO1VOpv4zOpvFGB8QjfDogPsgwJUKEyrD8gswIgGe+nVDl//zUvf0hgfsonh/hwEzLJ/Tczf12yS0WQnDECIH7Xew2RJwXInrPIaV0USQfY1GC14X+GedJBRru4HWwN-----END RSA PRIVATE KEY-----",
                defaultEnv = ENV.STAGING
            ),
            ExampleCredential(
                label = "SANDBOX app",
                appId = "app",
                publicKey = "-----BEGIN PUBLIC KEY-----MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAKWcehEELB4GdQ4cTLLQroLqnD3AhdKiwIhTJpAi1XnbfOSrW/Ebw6h1485GOAvuG/OwB+ScsfPJBoNJeNFU6J0CAwEAAQ==-----END PUBLIC KEY-----",
                privateKey = """-----BEGIN RSA PRIVATE KEY-----MIIBPAIBAAJBAKWcehEELB4GdQ4cTLLQroLqnD3AhdKiwIhTJpAi1XnbfOSrW/Ebw6h1485GOAvuG/OwB+ScsfPJBoNJeNFU6J0CAwEAAQJBAJSfTrSCqAzyAo59Ox+mQ1ZdsYWBhxc2084DwTHM8QN/TZiyF4fbVYtjvyhG8ydJ37CiG7d9FY1smvNG3iDC
dwECIQDygv2UOuR1ifLTDo4YxOs2cK3+dAUy6s54mSuGwUeo4QIhAK7SiYDyGwGoCwqjOdgOsQkJTGoUkDs8MST0MtmPAAs9AiEAjLT1/nBhJ9V/X3f9eF+g/bhJK+8TKSTV4WE1wP0Z3+ECIA9E3DWi77DpWG2JbBfu0I+VfFMXkLFbxH8RxQ8zajGRAiEA8Ly1xJ7UW3up25h9aa9SILBpGqWtJlNQgfVKBoabzsU=-----END RSA PRIVATE KEY-----""",
                defaultEnv = ENV.SANDBOX
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.status)

        val sharedPreference = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val hasSavedCredential = sharedPreference.contains(PREF_CREDENTIAL_INDEX)
        currentCredentialIndex = normalizeCredentialIndex(
            sharedPreference.getInt(PREF_CREDENTIAL_INDEX, DEFAULT_CREDENTIAL_INDEX)
        )
        currentCredential = CREDENTIAL_PRESETS[currentCredentialIndex]
        currentEnv = if (hasSavedCredential) {
            parseEnv(sharedPreference.getString(PREF_ENV, currentCredential.defaultEnv.name))
        } else {
            currentCredential.defaultEnv
        }
        currentLocale = parseLocale(sharedPreference.getString(PREF_LOCALE, Locale.vi.name))
        if (!hasSavedCredential) {
            sharedPreference.edit {
                putInt(PREF_CREDENTIAL_INDEX, currentCredentialIndex)
                putString(PREF_ENV, currentEnv.name)
            }
        }

        payMEMiniApp = PayMEMiniApp(
            this,
            currentCredential.appId,
            currentCredential.publicKey,
            currentCredential.privateKey,
            currentEnv,
            currentLocale
        )

        configurePayME(sharedPreference)
        bindActions(sharedPreference)
        showStatus(readyStatus())
    }

    private fun configurePayME(sharedPreference: SharedPreferences) {
        payMEMiniApp.setMode(modeFor(currentEnv))

        payMEMiniApp.setUpListener(
            onResponse = { actionOpenMiniApp: ActionOpenMiniApp, json: JSONObject? ->
                Log.d(PayMEMiniApp.TAG, "onResponse action: $actionOpenMiniApp")
                val prettyJson = json?.toString(4) ?: ""
                showStatus("Response: $actionOpenMiniApp\n$prettyJson")
            },
            onError = { actionOpenMiniApp: ActionOpenMiniApp, payMEError: PayMEError ->
                Log.d(
                    PayMEMiniApp.TAG,
                    "onError action: $actionOpenMiniApp code: ${payMEError.code}"
                )
                showStatus("Error: $actionOpenMiniApp ${payMEError.description}")
                runOnUiThread {
                    Toast.makeText(this, payMEError.description, Toast.LENGTH_LONG).show()
                }
            }
        )

        payMEMiniApp.setChangeEnvFunction(onChangeEnv = { data: String ->
            val nextEnv = parseEnv(data)
            sharedPreference.edit() {
                putString(PREF_ENV, nextEnv.name)
            }
            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        })

        payMEMiniApp.setChangeLocaleFunction(onChangeLocale = { data: String ->
            val nextLocale = parseLocale(data)
            sharedPreference.edit() {
                putString(PREF_LOCALE, nextLocale.name)
            }
            currentLocale = nextLocale
            showStatus("Locale changed: ${nextLocale.name}")
        })

        payMEMiniApp.setOneSignalFunctions(
            onOneSignalSendTags = { tags ->
                Log.d(PayMEMiniApp.TAG, "OneSignal send tags requested")
                showStatus("OneSignal send tags: $tags")
            },
            onOneSignalDeleteTags = { tags ->
                Log.d(PayMEMiniApp.TAG, "OneSignal delete tags requested")
                showStatus("OneSignal delete tags: $tags")
            }
        )
    }

    private fun bindActions(sharedPreference: SharedPreferences) {
        findViewById<Button>(R.id.open_payme_screen).setOnClickListener {
            openMiniApp(OpenMiniAppType.screen, OpenMiniAppPayMEData())
        }
        findViewById<Button>(R.id.open_payme_modal).setOnClickListener {
            openMiniApp(OpenMiniAppType.modal, OpenMiniAppPayMEData())
        }
        findViewById<Button>(R.id.open_wallet).setOnClickListener {
            openMiniApp(OpenMiniAppType.screen, OpenMiniAppOpenData(phone = SAMPLE_PHONE))
        }
        findViewById<Button>(R.id.open_payment).setOnClickListener {
            openMiniApp(
                OpenMiniAppType.modal,
                OpenMiniAppPaymentData(phone = SAMPLE_PHONE, paymentData = samplePaymentData())
            )
        }
        findViewById<Button>(R.id.open_payment_direct).setOnClickListener {
            openMiniApp(
                OpenMiniAppType.modal,
                OpenMiniAppPaymentDirectData(
                    phone = SAMPLE_PHONE,
                    paymentDirectData = samplePaymentDirectData()
                )
            )
        }
        findViewById<Button>(R.id.open_transfer_qr).setOnClickListener {
            openMiniApp(
                OpenMiniAppType.modal,
                OpenMiniAppTransferQRData(phone = SAMPLE_PHONE, transferQRData = sampleTransferQRData())
            )
        }
        findViewById<Button>(R.id.open_service).setOnClickListener {
            openMiniApp(
                OpenMiniAppType.modal,
                OpenMiniAppServiceData(
                    phone = SAMPLE_PHONE,
                    additionalData = ServiceData(
                        service = "POWE",
                        extraData = sampleExtraData(),
                        isBackToApp = true,
                        isShowResult = true
                    )
                )
            )
        }
        findViewById<Button>(R.id.open_deposit).setOnClickListener {
            openMiniApp(
                OpenMiniAppType.modal,
                OpenMiniAppDepositData(
                    phone = SAMPLE_PHONE,
                    additionalData = sampleWalletActionData("Deposit from Android example")
                )
            )
        }
        findViewById<Button>(R.id.open_withdraw).setOnClickListener {
            openMiniApp(
                OpenMiniAppType.modal,
                OpenMiniAppWithdrawData(
                    phone = SAMPLE_PHONE,
                    additionalData = sampleWalletActionData("Withdraw from Android example")
                )
            )
        }
        findViewById<Button>(R.id.open_transfer).setOnClickListener {
            openMiniApp(
                OpenMiniAppType.modal,
                OpenMiniAppTransferData(
                    phone = SAMPLE_PHONE,
                    additionalData = sampleWalletActionData("Transfer from Android example")
                )
            )
        }
        findViewById<Button>(R.id.open_kyc).setOnClickListener {
            openMiniApp(OpenMiniAppType.screen, OpenMiniAppKYCData(phone = SAMPLE_PHONE))
        }
        findViewById<Button>(R.id.get_balance).setOnClickListener {
            showStatus("Requesting balance for $SAMPLE_PHONE")
            payMEMiniApp.getBalance(SAMPLE_PHONE)
        }
        findViewById<Button>(R.id.get_account_information).setOnClickListener {
            showStatus("Requesting account information for $SAMPLE_PHONE")
            payMEMiniApp.getAccountInformation(SAMPLE_PHONE)
        }
        findViewById<Button>(R.id.switch_language).setOnClickListener {
            currentLocale = if (currentLocale == Locale.vi) Locale.en else Locale.vi
            sharedPreference.edit { putString(PREF_LOCALE, currentLocale.name) }
            payMEMiniApp.setLanguage(currentLocale)
            showStatus("Locale set: ${currentLocale.name}")
        }
        findViewById<Button>(R.id.switch_credentials).setOnClickListener {
            val nextIndex = nextCredentialIndex(currentCredentialIndex)
            val nextCredential = CREDENTIAL_PRESETS[nextIndex]
            sharedPreference.edit {
                putInt(PREF_CREDENTIAL_INDEX, nextIndex)
                putString(PREF_ENV, nextCredential.defaultEnv.name)
            }
            recreate()
        }
        findViewById<Button>(R.id.switch_env).setOnClickListener {
            currentEnv = nextEnv(currentEnv)
            sharedPreference.edit { putString(PREF_ENV, currentEnv.name) }
            recreate()
        }
        findViewById<Button>(R.id.close_sdk).setOnClickListener {
            payMEMiniApp.close()
            showStatus("SDK session closed")
        }
    }

    private fun openMiniApp(openType: OpenMiniAppType, data: OpenMiniAppDataInterface) {
        showStatus("Open ${data.action} as ${openType.name}")
        payMEMiniApp.openMiniApp(openType = openType, openMiniAppData = data)
    }

    private fun samplePaymentData(): PaymentData {
        return PaymentData(
            transactionId = newTransactionId("PAY"),
            amount = 10000,
            note = "Android example payment",
            ipnUrl = "https://example.com/payme/ipn",
            extraData = sampleExtraData(),
            isShowResult = true
        )
    }

    private fun samplePaymentDirectData(): PaymentDirectData {
        return PaymentDirectData(
            transaction = newTransactionId("PAYMENT"),
            extraData = sampleExtraData(),
            isShowResult = true
        )
    }

    private fun sampleTransferQRData(): TransferQRData {
        return TransferQRData(
            amount = 2000000,
            bankNumber = SAMPLE_BANK_NUMBER,
            swiftCode = SAMPLE_SWIFT_CODE,
            cardHolder = "NGUYEN VAN A",
            note = "Android example transfer QR",
            partnerTransaction = newTransactionId("TRANSFERQR"),
            extraData = sampleExtraData(),
            isShowResult = true
        )
    }

    private fun sampleWalletActionData(description: String): DepositWithdrawTransferData {
        return DepositWithdrawTransferData(
            description = description,
            amount = 10000,
            extraData = sampleExtraData(),
            isBackToApp = true,
            isShowResult = true
        )
    }

    private fun sampleExtraData(): Map<String, Any> {
        return mapOf(
            "source" to "android-example",
            "requestId" to newTransactionId("REQ"),
            "metadata" to mapOf(
                "platform" to "android",
                "sdkExample" to true
            )
        )
    }

    private fun newTransactionId(prefix: String): String {
        return "$prefix${System.currentTimeMillis()}"
    }

    private fun parseEnv(value: String?): ENV {
        return try {
            ENV.valueOf(value.orEmpty().replace("\"", "").uppercase())
        } catch (_: Exception) {
            ENV.PRODUCTION
        }
    }

    private fun parseLocale(value: String?): Locale {
        return try {
            Locale.valueOf(value.orEmpty().replace("\"", ""))
        } catch (_: Exception) {
            Locale.vi
        }
    }

    private fun modeFor(env: ENV): String {
        return when (env) {
            ENV.PRODUCTION -> "miniapp_product"
            ENV.STAGING -> "miniapp_staging"
            ENV.SANDBOX, ENV.DEV, ENV.LOCAL -> "miniapp_sandbox"
        }
    }

    private fun nextEnv(env: ENV): ENV {
        return when (env) {
            ENV.PRODUCTION -> ENV.SANDBOX
            ENV.SANDBOX -> ENV.STAGING
            ENV.STAGING -> ENV.PRODUCTION
            ENV.DEV, ENV.LOCAL -> ENV.PRODUCTION
        }
    }

    private fun normalizeCredentialIndex(index: Int): Int {
        return if (index in CREDENTIAL_PRESETS.indices) index else DEFAULT_CREDENTIAL_INDEX
    }

    private fun nextCredentialIndex(index: Int): Int {
        return (normalizeCredentialIndex(index) + 1) % CREDENTIAL_PRESETS.size
    }

    private fun readyStatus(): String {
        return "Ready: credential=${currentCredential.label}, appId=${currentCredential.appId}, " +
            "env=${currentEnv.name}, locale=${currentLocale.name}, mode=${modeFor(currentEnv)}"
    }

    private fun showStatus(message: String) {
        Log.d(PayMEMiniApp.TAG, message)
        runOnUiThread {
            statusText.text = message
        }
    }
}
