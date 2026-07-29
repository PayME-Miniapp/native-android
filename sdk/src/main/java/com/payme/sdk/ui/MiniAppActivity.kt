package com.payme.sdk.ui

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.R
import com.payme.sdk.runtime.PayMERuntime
import com.payme.sdk.utils.MixpanelUtil

class MiniAppActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Cho phép layout kéo dài vào khu vực của hệ thống (ví dụ: status bar, navigation bar)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    
    // Sử dụng WindowInsetsControllerCompat để thiết lập hệ thống thanh
    val insetsController = WindowInsetsControllerCompat(window, window.decorView)
    insetsController.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

    // Phương pháp cũ cho Android dưới API 30 nếu cần
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
      window.decorView.systemUiVisibility =
          View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    }

    // Tắt chế độ tối (dark mode)
    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

    // Đặt màu của thanh trạng thái thành trong suốt
    window.statusBarColor = ContextCompat.getColor(this, android.R.color.transparent)

    // Gán layout cho Activity từ file activity_miniapp.xml
    setContentView(R.layout.activity_miniapp)

    val sessionId = intent.getStringExtra(PayMERuntime.EXTRA_SESSION_ID)
    val session = PayMERuntime.getSession(sessionId)
    if (session == null) {
      Log.e(PayMEMiniApp.TAG, "Missing miniapp session. Finishing MiniAppActivity.")
      finish()
      return
    }
    PayMERuntime.activate(sessionId)
    session.closeAction = {
      session.isCloseRequested = true
      PayMERuntime.markSessionClosed(session.id)
      if (!isFinishing) {
        finish()
      }
    }

    if (savedInstanceState == null) {
      supportFragmentManager.beginTransaction()
          .replace(R.id.fragment_container_view, MiniAppFragment.newInstance(sessionId!!))
          .commit()
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    MixpanelUtil.flushEvents()
  }
}
