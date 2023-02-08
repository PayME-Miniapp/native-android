package com.example.app

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
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
        payMEMiniApp = PayMEMiniApp(this)
        openSdkButton.setOnClickListener {
            payMEMiniApp!!.openMiniApp(
                OpenMiniAppType.screen, OpenMiniAppData(
                    ActionOpenMiniApp.PAY,
                    "559163930378",
                    "0372823042",
                    """-----BEGIN PUBLIC KEY-----\nMFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAK0RONYVPYn/3IWloU83Qy16hKNHhlCx\ngTJay6/rERk8tmsMbILLzTYW7H9WOqN2gS0s0ymD+3TxP+q+MxEp0qECAwEAAQ==\n-----END PUBLIC KEY-----""",
                    """-----BEGIN RSA PRIVATE KEY-----\nMIIBOgIBAAJBAMXIuvTT8Z5U/AqyFvBbDApQ2STLm9Ca2nmu2pxqwhrhN+80mOLb\nMzbQDRCNpro6S61d34A7cEIX/5gxxrAaVAkCAwEAAQJAfzB70e/uJHTgdHxcNgtG\n7edaDMiHFhpPPwtL+GTLGH70yhFDs2eIXFHLY/wfRRcxzwGyGOyvXlGbDjsMFdpn\nlQIhAPIoUVsADDfI4KNZEKHaJRVAmz2D0xdiB6R716HA7A0XAiEA0RcPxHzYLhVp\n+adoGpJBq7e87BzQrVBJQFSOg8Kim98CIQCYmynyFEye1zwiFR3zMfuOsiFjGfFs\n2f2A/f69VEwuTwIgFN/3jAdm0dsDdJBZHWYCtnEmpHAQCW2dkpWekNsKvwMCIGXm\nrg+mppNNZQx6+6Swsp8L8Hgc+HikKy02Okijjw0W\n-----END RSA PRIVATE KEY-----""",
                    ENV.LOCAL,
                    PaymentData(UUID.randomUUID().toString(), 10000, "aaaaaa", "adfkajljfds")
                ),
                onSuccess = { actionOpenMiniApp: ActionOpenMiniApp, json: JSONObject? ->
                    Log.d("HIEU", "onSuccess action: $actionOpenMiniApp ${json?.toString()}")
                },
                onError = { actionOpenMiniApp: ActionOpenMiniApp, payMEError: PayMEError ->
                    Log.d(
                        "HIEU",
                        "onError actionOpenMiniApp: $actionOpenMiniApp payMEError: $payMEError"
                    )
                    Toast.makeText(this, payMEError.message, Toast.LENGTH_LONG).show()
                }
            )
        }
    }
}