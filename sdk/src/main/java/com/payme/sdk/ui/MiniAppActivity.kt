package com.payme.sdk.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.*
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.webkit.*
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.airbnb.lottie.LottieAnimationView
import com.payme.sdk.BuildConfig.SDK_VERSION
import com.payme.sdk.R
import com.payme.sdk.models.PayMEVersion
import com.payme.sdk.utils.DeviceTypeResolver
import com.payme.sdk.utils.MixpanelUtil
import com.payme.sdk.utils.PermissionCameraUtil
import com.payme.sdk.utils.Utils
import com.payme.sdk.viewmodels.MiniappViewModel
import com.payme.sdk.viewmodels.NotificationViewModel
import com.payme.sdk.viewmodels.PayMEUpdatePatchViewModel
import com.payme.sdk.webServer.JavaScriptInterface
import com.payme.sdk.webServer.WebServer
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.net.ssl.SSLException


class MiniAppActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    window.decorView.systemUiVisibility =
      View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    window.statusBarColor = ContextCompat.getColor(this, android.R.color.transparent)
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_miniapp)
  }

  override fun onDestroy() {
    super.onDestroy()
    MixpanelUtil.flushEvents()
  }}

