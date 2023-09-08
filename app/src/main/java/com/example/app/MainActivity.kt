package com.example.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.models.*
import com.payme.sdk.ui.MiniAppFragment
import org.json.JSONObject
import org.json.JSONStringer
import java.net.URLEncoder
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

        val env = try {
            ENV.valueOf(savedEnv.replace("\"", "").toUpperCase())
        } catch (e: Exception) {
            ENV.PRODUCTION
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
            "app",
            """-----BEGIN PUBLIC KEY-----\nMFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAKWcehEELB4GdQ4cTLLQroLqnD3AhdKi\nwIhTJpAi1XnbfOSrW/Ebw6h1485GOAvuG/OwB+ScsfPJBoNJeNFU6J0CAwEAAQ==\n-----END PUBLIC KEY-----""",
            """-----BEGIN RSA PRIVATE KEY-----\nMIIBPAIBAAJBAKWcehEELB4GdQ4cTLLQroLqnD3AhdKiwIhTJpAi1XnbfOSrW/Eb\nw6h1485GOAvuG/OwB+ScsfPJBoNJeNFU6J0CAwEAAQJBAJSfTrSCqAzyAo59Ox+m\nQ1ZdsYWBhxc2084DwTHM8QN/TZiyF4fbVYtjvyhG8ydJ37CiG7d9FY1smvNG3iDC\ndwECIQDygv2UOuR1ifLTDo4YxOs2cK3+dAUy6s54mSuGwUeo4QIhAK7SiYDyGwGo\nCwqjOdgOsQkJTGoUkDs8MST0MtmPAAs9AiEAjLT1/nBhJ9V/X3f9eF+g/bhJK+8T\nKSTV4WE1wP0Z3+ECIA9E3DWi77DpWG2JbBfu0I+VfFMXkLFbxH8RxQ8zajGRAiEA\n8Ly1xJ7UW3up25h9aa9SILBpGqWtJlNQgfVKBoabzsU=\n-----END RSA PRIVATE KEY-----""",
            ENV.SANDBOX,
        )

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
            val sharedPreference = getSharedPreferences("PAYME_WALLET", Context.MODE_PRIVATE)
            val editor = sharedPreference.edit()
            editor.putString("PAYME_WALLET_ENV", data.replace("\"", "").toUpperCase())
            editor.apply()
            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
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


//        payMEMiniApp!!.openMiniApp(OpenMiniAppType.screen, OpenMiniAppPayMEData())

        openSdkButton.setOnClickListener {
            payMEMiniApp!!.getBalance("0366332032")
            payMEMiniApp!!.getAccountInformation("0366332032")
            // payMEMiniApp!!.openMiniApp(
            //     OpenMiniAppType.screen, OpenMiniAppPaymentData("0366332032", PaymentData("14", 100000, "aaaa", "dafds")),
            // )
           payMEMiniApp!!.openMiniApp(OpenMiniAppType.screen, OpenMiniAppPayMEData())
//            payMEMiniApp!!.openMiniApp(OpenMiniAppType.screen, OpenMiniAppServiceData("0372823042", "POWE"))
        }
    }
}