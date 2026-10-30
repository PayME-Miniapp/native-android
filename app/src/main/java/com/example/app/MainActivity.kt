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
                appId = BuildConfig.PAYME_PRESET_0_APP_ID,
                publicKey = BuildConfig.PAYME_PRESET_0_PUBLIC_KEY,
                privateKey = BuildConfig.PAYME_PRESET_0_PRIVATE_KEY,
                defaultEnv = ENV.SANDBOX
            ),
            ExampleCredential(
                label = "SANDBOX partner 143090043042",
                appId = BuildConfig.PAYME_PRESET_1_APP_ID,
                publicKey = BuildConfig.PAYME_PRESET_1_PUBLIC_KEY,
                privateKey = BuildConfig.PAYME_PRESET_1_PRIVATE_KEY,
                defaultEnv = ENV.SANDBOX
            ),
            ExampleCredential(
                label = "STAGING ecosystem 264245066910",
                appId = BuildConfig.PAYME_PRESET_2_APP_ID,
                publicKey = BuildConfig.PAYME_PRESET_2_PUBLIC_KEY,
                privateKey = BuildConfig.PAYME_PRESET_2_PRIVATE_KEY,
                defaultEnv = ENV.STAGING
            ),
            ExampleCredential(
                label = "SANDBOX app",
                appId = BuildConfig.PAYME_PRESET_3_APP_ID,
                publicKey = BuildConfig.PAYME_PRESET_3_PUBLIC_KEY,
                privateKey = BuildConfig.PAYME_PRESET_3_PRIVATE_KEY,
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

        if (!hasDemoCredentials(currentCredential.appId, currentCredential.publicKey, currentCredential.privateKey)) {
            showStatus("Configure demo credentials in local.properties before opening the SDK")
            return
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
