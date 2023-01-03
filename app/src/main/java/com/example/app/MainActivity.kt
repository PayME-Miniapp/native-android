package com.example.app

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import com.payme.sdk.PayMEMiniApp

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
        payMEMiniApp = PayMEMiniApp(this, null, null)
        openSdkButton.setOnClickListener {
            payMEMiniApp!!.openMiniApp()
        }
    }
}