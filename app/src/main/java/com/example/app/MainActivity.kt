package com.example.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.models.ActionOpenMiniApp
import com.payme.sdk.models.ENV
import com.payme.sdk.models.Locale
import com.payme.sdk.models.OpenMiniAppTransferQRData
import com.payme.sdk.models.OpenMiniAppType
import com.payme.sdk.models.PayMEError
import com.payme.sdk.models.TransferQRData
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var openSdkButton: TextView

    companion object {
        @SuppressLint("StaticFieldLeak")
        var payMEMiniApp: PayMEMiniApp? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        openSdkButton = findViewById(R.id.open_sdk)
//        val a =
//            "payme://view?data=00020101021226280010A000000775011001021822925204481453037045405100005802VN5904VBAN6005HANOI610610000062710309VbanTopup05210123021611401315946400708II0QWCYU0817Thanh%20toan%20QRCode630401B7&callbackurl=https%3a%2f%2fpay.vnpay.vn%2fqrback.html%3ftoken%3d4033925c9e394817a883591843f471a9/"
//        MiniAppFragment.setDeepLink(a)

        val sharedPreference = getSharedPreferences("PAYME_WALLET", Context.MODE_PRIVATE)

        val savedEnv = sharedPreference.getString("PAYME_WALLET_ENV", "PRODUCTION") ?: "PRODUCTION"
        val savedLocale = sharedPreference.getString("PAYME_WALLET_LOCALE", "vi") ?: "vi"

        val env = try {
            ENV.valueOf(savedEnv.replace("\"", "").uppercase())
        } catch (e: Exception) {
            ENV.PRODUCTION
        }

        val locale = try {
            Locale.valueOf(savedLocale.replace("\"", ""))
        } catch (e: Exception) {
            Locale.vi
        }

//        payMEMiniApp = PayMEMiniApp(
//            this,
//            "250069027220",
//            """-----BEGIN PUBLIC KEY-----MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAIB6eR2SMUCqy7LkXmVF1xf37pJn5yCpGMGpOd6fc/dFkqIkNBDHoTRIhMdBucauf3i7S2g+fl6g+Kte4MlaYkcCAwEAAQ==-----END PUBLIC KEY-----""",
//            """-----BEGIN RSA PRIVATE KEY-----MIIBOgIBAAJBAIB6eR2SMUCqy7LkXmVF1xf37pJn5yCpGMGpOd6fc/dFkqIkNBDHoTRIhMdBucauf3i7S2g+fl6g+Kte4MlaYkcCAwEAAQJAUlyxGfjnJBqZvRPTQ77y9cWWJjr/mxtr6HJwy7uSnvgNRY1zfpRLccR4NvMS7LtgK47sx1vJmCOgtVCGwCVUUQIhALlGTGM1Q4E5L2xCX0SfCY6vdKOdwvD5NyaUSP7ZJVf/AiEAsYXYoEApSorjtLg4JjLJhpE8H8Lf6o1AFpX9g83aNbkCICRY1zmLRIAAcP5DEx+KN7zHTRGgLJNLwPcPljZw8TOPAiEAg1P0XSD6KwYyzEgYadHamm2pIAoHorpaNhtCEBbinikCIFtk7by4tboFtUkXf7X+/Y1jX1owrT4xDO2sBKrUs/9F-----END RSA PRIVATE KEY-----""",
//            ENV.DEV,
//        )

       payMEMiniApp = PayMEMiniApp(
           this,
           "250069027220",
           """-----BEGIN PUBLIC KEY-----\nMFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAIB6eR2SMUCqy7LkXmVF1xf37pJn5yCp\nGMGpOd6fc/dFkqIkNBDHoTRIhMdBucauf3i7S2g+fl6g+Kte4MlaYkcCAwEAAQ==\n-----END PUBLIC KEY-----""",
           """-----BEGIN RSA PRIVATE KEY-----MIIBOgIBAAJBAIB6eR2SMUCqy7LkXmVF1xf37pJn5yCpGMGpOd6fc/dFkqIkNBDH\noTRIhMdBucauf3i7S2g+fl6g+Kte4MlaYkcCAwEAAQJAUlyxGfjnJBqZvRPTQ77y\n9cWWJjr/mxtr6HJwy7uSnvgNRY1zfpRLccR4NvMS7LtgK47sx1vJmCOgtVCGwCVU\nUQIhALlGTGM1Q4E5L2xCX0SfCY6vdKOdwvD5NyaUSP7ZJVf/AiEAsYXYoEApSorj\ntLg4JjLJhpE8H8Lf6o1AFpX9g83aNbkCICRY1zmLRIAAcP5DEx+KN7zHTRGgLJNL\nwPcPljZw8TOPAiEAg1P0XSD6KwYyzEgYadHamm2pIAoHorpaNhtCEBbinikCIFtk\n7by4tboFtUkXf7X+/Y1jX1owrT4xDO2sBKrUs/9F\n-----END RSA PRIVATE KEY-----""",
           ENV.SANDBOX,
       )

//          payMEMiniApp = PayMEMiniApp(
//              this,
//              "264245066910",
//              """-----BEGIN PUBLIC KEY-----MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAJoxQCA/D1qx2aUC65SxP+ZsCo/+YYVnV5pTeLgwCjgkZa1mac2q0vgWXvCamT9jjPBOhyuIX+wdHOGufwqtZQ8CAwEAAQ==-----END PUBLIC KEY-----""",
//              """-----BEGIN RSA PRIVATE KEY-----MIIBOgIBAAJBAJtl/BFoCHwGCwfgiNKEJXkIQIpBnworMkX1I56UlcLkWhgug+KdxGPoTObufx8IHbBTQIl748uIDUpMjIPSL68CAwEAAQJAdZGoLsclvCeaSuBew97Etxg+NBvHpprd0z3PMBg8YhDQWIIo2kaqq7AfDmJU2Cb+SaTGIPCFZWP2lK60abiKIQIhAN6BCYNCu8kQ1rZ1FhGU5Ri9pvN3RSSnUpzE+h7GJsG/AiEAssrMYGxBLwU3S3l2zwlMc1tP/exEZWMjsboWRKlRLhECIQCJIZACVPO1VOpv4zOpvFGB8QjfDogPsgwJUKEyrD8gswIgGe+nVDl//zUvf0hgfsonh/hwEzLJ/Tczf12yS0WQnDECIH7Xew2RJwXInrPIaV0USQfY1GC14X+GedJBRru4HWwN-----END RSA PRIVATE KEY-----""",
//              ENV.STAGING,
//          )

//         payMEMiniApp = PayMEMiniApp(
//             this,
//             "app",
//             """-----BEGIN PUBLIC KEY-----MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAKWcehEELB4GdQ4cTLLQroLqnD3AhdKiwIhTJpAi1XnbfOSrW/Ebw6h1485GOAvuG/OwB+ScsfPJBoNJeNFU6J0CAwEAAQ==-----END PUBLIC KEY-----""",
//             """-----BEGIN RSA PRIVATE KEY-----MIIBPAIBAAJBAKWcehEELB4GdQ4cTLLQroLqnD3AhdKiwIhTJpAi1XnbfOSrW/Ebw6h1485GOAvuG/OwB+ScsfPJBoNJeNFU6J0CAwEAAQJBAJSfTrSCqAzyAo59Ox+mQ1ZdsYWBhxc2084DwTHM8QN/TZiyF4fbVYtjvyhG8ydJ37CiG7d9FY1smvNG3iDC\ndwECIQDygv2UOuR1ifLTDo4YxOs2cK3+dAUy6s54mSuGwUeo4QIhAK7SiYDyGwGoCwqjOdgOsQkJTGoUkDs8MST0MtmPAAs9AiEAjLT1/nBhJ9V/X3f9eF+g/bhJK+8TKSTV4WE1wP0Z3+ECIA9E3DWi77DpWG2JbBfu0I+VfFMXkLFbxH8RxQ8zajGRAiEA8Ly1xJ7UW3up25h9aa9SILBpGqWtJlNQgfVKBoabzsU=-----END RSA PRIVATE KEY-----""",
//             ENV.SANDBOX,
//         )

        payMEMiniApp!!.setUpListener(
            onResponse = { actionOpenMiniApp: ActionOpenMiniApp, json: JSONObject? ->
                Log.d(PayMEMiniApp.TAG, "onSuccess action: $actionOpenMiniApp ${json?.toString()}")
            },
            onError = { actionOpenMiniApp: ActionOpenMiniApp, payMEError: PayMEError ->
                Log.d(
                    PayMEMiniApp.TAG,
                    "onError actionOpenMiniApp: $actionOpenMiniApp payMEError: ${payMEError.description}"
                )
                Toast.makeText(this, payMEError.description, Toast.LENGTH_LONG).show()
            }
        )

        payMEMiniApp!!.setChangeEnvFunction(onChangeEnv = { data: String ->
            val editor = sharedPreference.edit()
            editor.putString("PAYME_WALLET_ENV", data.replace("\"", "").uppercase())
            editor.apply()
            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        })

        payMEMiniApp!!.setChangeLocaleFunction(onChangeLocale = { data: String ->
            val editor = sharedPreference.edit()
            editor.putString("PAYME_WALLET_LOCALE", data.replace("\"", ""))
            editor.apply()
//            val intent = Intent(applicationContext, MainActivity::class.java).apply {
//                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
//            }
//            startActivity(intent)
        })

        when (savedEnv) {
            "PRODUCTION" -> {
                payMEMiniApp!!.setMode("pm_product")
            }
            "STAGING" -> {
                payMEMiniApp!!.setMode("pm_staging")
            }
            "SANDBOX" -> {
                payMEMiniApp!!.setMode("pm_sandbox")
            }
            else -> {
                payMEMiniApp!!.setMode("pm_product")
            }
        }

        payMEMiniApp!!.setMode("miniapp_sandbox")
//        payMEMiniApp!!.setLanguage(locale)

//        payMEMiniApp!!.openMiniApp(OpenMiniAppType.screen, OpenMiniAppPayMEData())

//        payMEMiniApp!!.openMiniApp(OpenMiniAppType.screen, OpenMiniAppPayMEData())

        openSdkButton.setOnClickListener {
            payMEMiniApp!!.getBalance("0795550300")
            payMEMiniApp!!.getAccountInformation("0795550300")
//            payMEMiniApp!!.openMiniApp(
//                OpenMiniAppType.modal,  OpenMiniAppPaymentDirectData("0795550301",
//                    PaymentDirectData("348115135612",true)
//                )
//            )

            payMEMiniApp!!.openMiniApp(
                OpenMiniAppType.modal,  OpenMiniAppTransferQRData("0795550300",
                    TransferQRData(amount = 20000, bankNumber = "9704000000000018", swiftCode = "SBITVNVX", cardHolder = "NGUYEN VAN A", note = "Test", extraData = mapOf(
                        "key1" to "value1",
                        "key2" to 123,
                        "key3" to mapOf("street" to "123 Main St",
                            "city" to "New York",
                            "country" to "USA",
                            "abc" to mapOf(
                                "aa" to "1111"
                            )
                        )
                    ), isShowResult = true)
                )
            )

//            payMEMiniApp!!.openMiniApp(
//                OpenMiniAppType.modal,  OpenMiniAppPaymentData("0795550301",
//                    PaymentData("348115135612", 10000, "", "", true)
//                )
//            )
//             payMEMiniApp!!.openMiniApp(OpenMiniAppType.modal, OpenMiniAppPayMEData())
//                        payMEMiniApp!!.openMiniApp(OpenMiniAppType.modal, OpenMiniAppServiceData("0795550300", ServiceData("POWE", isBackToApp = true, isShowResult = true)))
        }
    }
}