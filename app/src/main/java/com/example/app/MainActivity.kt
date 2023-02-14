package com.example.app

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.models.*
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
        payMEMiniApp = PayMEMiniApp(
            this,
            "250069027220",
            """-----BEGIN PUBLIC KEY-----\n        MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAIB6eR2SMUCqy7LkXmVF1xf37pJn5yCpGMGpOd6fc/dFkqIkNBDHoTRIhMdBucauf3i7S2g+fl6g+Kte4MlaYkcCAwEAAQ==-----END PUBLIC KEY-----""",
            """-----BEGIN RSA PRIVATE KEY-----MIIBOgIBAAJBAIB6eR2SMUCqy7LkXmVF1xf37pJn5yCpGMGpOd6fc/dFkqIkNBDHoTRIhMdBucauf3i7S2g+fl6g+Kte4MlaYkcCAwEAAQJAUlyxGfjnJBqZvRPTQ77y9cWWJjr/mxtr6HJwy7uSnvgNRY1zfpRLccR4NvMS7LtgK47sx1vJmCOgtVCGwCVUUQIhALlGTGM1Q4E5L2xCX0SfCY6vdKOdwvD5NyaUSP7ZJVf/AiEAsYXYoEApSorjtLg4JjLJhpE8H8Lf6o1AFpX9g83aNbkCICRY1zmLRIAAcP5DEx+KN7zHTRGgLJNLwPcPljZw8TOPAiEAg1P0XSD6KwYyzEgYadHamm2pIAoHorpaNhtCEBbinikCIFtk7by4tboFtUkXf7X+/Y1jX1owrT4xDO2sBKrUs/9F-----END RSA PRIVATE KEY-----""",
            ENV.SANDBOX
        )
        openSdkButton.setOnClickListener {
            payMEMiniApp!!.openMiniApp(
                OpenMiniAppType.screen, OpenMiniAppOpenData("0372823042"),
                onSuccess = { actionOpenMiniApp: ActionOpenMiniApp, json: JSONObject? ->
                    Log.d("PAYME", "onSuccess action: $actionOpenMiniApp ${json?.toString()}")
                },
                onError = { actionOpenMiniApp: ActionOpenMiniApp, payMEError: PayMEError ->
                    Log.d(
                        "PAYME",
                        "onError actionOpenMiniApp: $actionOpenMiniApp payMEError: $payMEError"
                    )
                    Toast.makeText(this, payMEError.message, Toast.LENGTH_LONG).show()
                }
            )
        }
    }
}