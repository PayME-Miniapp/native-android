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
import com.payme.sdk.models.*
import com.payme.sdk.models.Locale
import org.json.JSONObject
import java.util.*

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

        //  payMEMiniApp = PayMEMiniApp(
        //      this,
        //      "523388220210",
        //      """-----BEGIN PUBLIC KEY-----\nMFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAIzrDm+HZ+a73vq2lInP0/xjOU0qwfKw\n8vvbZ3B//0kmwX3Fhni21fq+sWWcezio48uSdPfmMsRmS8ux640pla8CAwEAAQ==\n-----END PUBLIC KEY-----""",
        //      """-----BEGIN RSA PRIVATE KEY-----\nMIIBOwIBAAJBAIiFLoyPAhko6wHAVlUueZZO/W6YzZY1AfpNLsPn5HbYxLf6cWHs\nNQGp4O5oba9Kveel/NohCYuwHBSviQl6NZECAwEAAQJAZihLAfFNn6gn20KjF9DU\nOS7YpDcBuIHn/fZdpUlUg71tinAIBjRhHFdGgbs0J4DjmbpypLmKBId/hvhLjyde\nQQIhANWPPediBshtZ34PngJjKa7OPfP9x457PAFaCxq7QfdJAiEAo6aYieeGzB6I\nekL4uOK1Y2FO3yasNJDwXShsImMQZAkCIQCTc72oPxSz2mY0sg/FUjZ7jcdU6gqZ\nJBmATW2RXW3kkQIgNsNqKkPTJP1WuGsu5lffUUlf5mb/m3uhI9uCDCPQeVkCIQC8\nGzSSCFond0HtETumAldK3UPPdq3nmBCkyOwIbg0tZQ==\n-----END RSA PRIVATE KEY-----""",
        //      ENV.SANDBOX,
        //  )

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
            val sharedPreference = getSharedPreferences("PAYME_WALLET", Context.MODE_PRIVATE)
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
            payMEMiniApp!!.openMiniApp(
                OpenMiniAppType.screen, OpenMiniAppDepositData("0795550300", DepositWithdrawTransferData("", 0,
                    isBackToApp = true,
                    isShowResult = true
                )
                ),
            )
            // payMEMiniApp!!.openMiniApp(OpenMiniAppType.modal, OpenMiniAppOpenData("0795550300"))
//                        payMEMiniApp!!.openMiniApp(OpenMiniAppType.screen, OpenMiniAppServiceData("0795550300", ServiceData("POWE", true)))
        }
    }
}