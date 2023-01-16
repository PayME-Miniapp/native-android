package com.example.app

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.models.ActionOpenMiniApp
import com.payme.sdk.models.OpenMiniAppData
import com.payme.sdk.models.OpenMiniAppType

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
                OpenMiniAppType.modal, OpenMiniAppData(
                    ActionOpenMiniApp.PayME,
                    "853702955206",
                    "0372823042",
                    "-----BEGIN PUBLIC KEY-----\n" +
                            "MFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAJICs/GmGlDYW3jQiIMzt+SATx81CmOc\n" +
                            "SMACY0kznT4XG9wiTE22CETHGoocNTkDam0IM6Q4Utp5Ku6kbdBLnn0CAwEAAQ==\n" +
                            "-----END PUBLIC KEY-----",
                    "-----BEGIN RSA PRIVATE KEY-----\n" +
                            "MIIBOwIBAAJBAK/nYQsbwmhqEudNjL3qEQDnWPrlFjJbqGRmeTvTKKsl+bklXtbT\n" +
                            "+KAbf+Gd1BdSTwcWY3WNaU1+4EHoaBmuzv0CAwEAAQJAVwZKrXs7T/sChSqJsb9m\n" +
                            "UCMkk2PY+mr8QUetPNq36QuaHjJ9EQW7SiVB4/uwnJH79nOSp9qgFO3smtn+lRuV\n" +
                            "RQIhAO+XlPuECbKQE0EzwQn/M2AChXyCPxkMCxZLBl6S626HAiEAu/M+U51t2lXr\n" +
                            "hS9q0bgnE+cccNYZBsI+6mK3e/cBk1sCIQDhTh1GrDLmXQAOV5nXScpJJfXbUSv+\n" +
                            "5MlkTGcP9n847wIgHzySAzuK4lqdRglXa3t7oycp5ubuSd1Gr5WwgP3QWTkCIQDG\n" +
                            "IuSTOw3KVf4m42HDiomfgkAaHG8LviwLJlPAMmgliQ==\n" +
                            "-----END RSA PRIVATE KEY-----"
                )
            )
        }
    }
}