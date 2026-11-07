package com.payme.sdk.ui

import android.content.Context
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
import com.payme.sdk.utils.SdkLocale
import com.payme.sdk.runtime.HostLifecycle
import com.payme.sdk.runtime.PayMERuntime
import com.payme.sdk.utils.MixpanelUtil
import java.lang.ref.WeakReference

class MiniAppActivity : AppCompatActivity() {
  // Tắt dark mode chỉ cho mini-app; setDefaultNightMode sẽ đổi chế độ tối của cả app partner
  override fun attachBaseContext(newBase: Context) {
    delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_NO
    super.attachBaseContext(SdkLocale.configurationContext(newBase))
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    val sessionId = intent.getStringExtra(PayMERuntime.EXTRA_SESSION_ID)
    val session = PayMERuntime.getSession(sessionId)
    // Sau khi process bị huỷ, phiên (cùng identityToken) đã mất: không cho hệ thống khôi phục MiniAppFragment cũ,
    // nếu không fragment sẽ crash vì thiếu phiên. Activity đóng ngay bên dưới.
    super.onCreate(if (session == null) null else savedInstanceState)
    
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

    // Đặt màu của thanh trạng thái thành trong suốt
    window.statusBarColor = ContextCompat.getColor(this, android.R.color.transparent)

    // Gán layout cho Activity từ file activity_miniapp.xml
    setContentView(R.layout.activity_miniapp)

    if (session == null) {
      Log.e(PayMEMiniApp.TAG, "Missing miniapp session. Finishing MiniAppActivity.")
      finish()
      return
    }
    PayMERuntime.activate(sessionId)
    session.attachHost(this) { !it.isFinishing && !it.isDestroyed }
    // Giữ yếu: phiên static không được giữ Activity đã huỷ
    val selfRef = WeakReference(this)
    session.closeAction = {
      PayMERuntime.markCloseRequested(session)
      PayMERuntime.markSessionClosed(session.id)
      selfRef.get()?.takeIf { !it.isFinishing }?.finish()
    }

    if (savedInstanceState == null) {
      supportFragmentManager.beginTransaction()
          .replace(R.id.fragment_container_view, MiniAppFragment.newInstance(sessionId!!))
          .commit()
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    // Activity bị đóng hẳn mà chưa qua close()/jsClose (vd. finishAffinity, xoá task): dọn phiên, báo USER_CANCEL một lần.
    // Xoay màn hình hoặc hệ thống huỷ tạm thì giữ phiên để khôi phục.
    if (HostLifecycle.shouldEndSessionOnActivityDestroy(isFinishing)) {
      val description = getString(R.string.user_cancel_miniapp)
      PayMERuntime.endSessionFromHost(intent.getStringExtra(PayMERuntime.EXTRA_SESSION_ID)) { description }
    }
    MixpanelUtil.flushEvents()
  }
}
