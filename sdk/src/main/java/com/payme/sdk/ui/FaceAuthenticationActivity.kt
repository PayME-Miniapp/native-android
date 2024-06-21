package com.payme.sdk.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import com.airbnb.lottie.LottieAnimationView
import com.payme.sdk.R
import com.payme.sdk.camerax.GraphicOverlay
import com.payme.sdk.face_authentication.FaceAuthenticationActiveFrame
import com.payme.sdk.face_authentication.FaceAuthenticationCameraManager
import com.payme.sdk.face_detection.FaceDetectorActiveFrame
import com.payme.sdk.utils.PermissionCameraUtil
import com.payme.sdk.utils.Utils
import com.payme.sdk.viewmodels.FaceDetectorStepViewModel
import java.util.*

class FaceAuthenticationActivity : AppCompatActivity() {
    private lateinit var cameraManager: FaceAuthenticationCameraManager
    private lateinit var preview: PreviewView
    private lateinit var graphicOverlay: GraphicOverlay
    private lateinit var buttonBack: ImageView
    private lateinit var textHint: TextView
    private lateinit var activeFrame: FaceDetectorActiveFrame
    private lateinit var lottieView: LottieAnimationView
    private lateinit var hintContainer: LinearLayout
    private lateinit var faceDetectorStepViewModel: FaceDetectorStepViewModel

    var timerTask: TimerTask? = null

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face_authentication)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        preview = findViewById(R.id.preview)
        buttonBack = findViewById(R.id.buttonBackHeader)
        graphicOverlay = findViewById(R.id.graphicOverlay_finder)
        textHint = findViewById(R.id.textHint)
        activeFrame = findViewById(R.id.activeFrame)
        activeFrame.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        lottieView = findViewById(R.id.lottieView)
        hintContainer = findViewById(R.id.hintContainer)

        buttonBack.setOnClickListener {
            setResult(Activity.RESULT_CANCELED, Intent())
            finish()

        }

        val hint1 = intent.extras?.getString("hint1")
        val hint2 = intent.extras?.getString("hint2")
        val hint3 = intent.extras?.getString("hint3")
        textHint.text = hint1 ?: getString(R.string.face_detector_hint1)
        faceDetectorStepViewModel = FaceDetectorStepViewModel()

        adjustLayout()
        faceDetectorStepViewModel.getTextHint().observe(this) {
            runOnUiThread {
                if (it.isEmpty()) {
                    when (faceDetectorStepViewModel.getStep().value) {
                        1 -> textHint.text = hint1 ?: getString(R.string.face_detector_hint1)
                        2 -> textHint.text = hint2 ?: getString(R.string.face_detector_hint2)
                        3 -> textHint.text = hint3 ?: getString(R.string.face_detector_hint3)
                    }
                } else {
                    textHint.text = it
                }
            }
        }

        cameraManager = FaceAuthenticationCameraManager(
            this,
            preview,
            this,
            graphicOverlay,
            activeFrame,
            timerTask,
            textHint,
            lottieView,
            faceDetectorStepViewModel,
            hint2,
            hint3,
        )

        if (PermissionCameraUtil().isGrantedCamera(this)) {
            cameraManager.startCamera()
        }

        if (intent.getBooleanExtra("EXIT", false)) {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timerTask?.cancel()
    }

    private fun adjustLayout() {
        val layoutParams = hintContainer.layoutParams as ConstraintLayout.LayoutParams
        layoutParams.leftMargin = 0
        layoutParams.topMargin = Utils.dpToPx(this, 430)
        layoutParams.rightMargin = 0
        layoutParams.bottomMargin = 0
        hintContainer.requestLayout()
    }
}