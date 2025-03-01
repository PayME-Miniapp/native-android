package com.payme.sdk.ui

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.payme.sdk.R
import com.payme.sdk.utils.MixpanelUtil

class MiniAppActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      // Cho phép layout kéo dài vào khu vực của hệ thống (ví dụ: status bar, navigation bar)
      WindowCompat.setDecorFitsSystemWindows(window, false)
      window.insetsController?.apply {
        // Thiết lập hành vi của thanh hệ thống: cho phép hiển thị tạm thời khi vuốt
        systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      }
    } else {
      // Phương pháp cũ cho các phiên bản Android trước API 30:
      // Mở rộng layout vào khu vực hệ thống bằng cách sử dụng systemUiVisibility
      window.decorView.systemUiVisibility =
          View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    }

    // Tắt chế độ tối (dark mode)
    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

    // Đặt màu của thanh trạng thái thành trong suốt
    window.statusBarColor = ContextCompat.getColor(this, android.R.color.transparent)

    // Gán layout cho Activity từ file activity_miniapp.xml
    setContentView(R.layout.activity_miniapp)
  }

  override fun onDestroy() {
    super.onDestroy()
    MixpanelUtil.flushEvents()
  }
}