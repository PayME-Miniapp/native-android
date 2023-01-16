package com.payme.sdk.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import com.payme.sdk.R
import com.payme.sdk.camerax.GraphicOverlay
import com.payme.sdk.object_detection.IdentityCardActiveFrame
import com.payme.sdk.object_detection.IdentityCardCameraManager
import com.payme.sdk.utils.PermissionCameraUtil
import com.payme.sdk.utils.Utils
import com.payme.sdk.viewmodels.IdentityCardViewModel
import java.util.*
import kotlin.concurrent.schedule

class IdentityCardActivity : AppCompatActivity() {
    private lateinit var cameraManager: IdentityCardCameraManager
    private lateinit var preview: PreviewView
    private lateinit var buttonBack: ImageView
    private lateinit var buttonTakePic: ImageView
    private lateinit var graphicOverlay: GraphicOverlay
    private lateinit var activeFrame: IdentityCardActiveFrame
    private lateinit var textHint: TextView
    private lateinit var textTitle: TextView
    private lateinit var textError: TextView
    private lateinit var rootView: ConstraintLayout
    private lateinit var reviewImage: ImageView
    private lateinit var imageCCCD: ImageView
    private lateinit var timer: TimerTask

    private lateinit var identityCardViewModel: IdentityCardViewModel

    private lateinit var timerEnableButton: TimerTask
    var doneTimerEnable: Boolean = false
    private var description = ""

    private var identityCardType: String = "FRONT"

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_identity_card)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        rootView = findViewById(R.id.rootView)
        preview = findViewById(R.id.preview)
        buttonBack = findViewById(R.id.buttonBackHeader)
        buttonTakePic = findViewById(R.id.btn_take_picture)
        graphicOverlay = findViewById(R.id.graphicOverlay_finder)
        activeFrame = findViewById(R.id.activeFrame)
        activeFrame.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        textHint = findViewById(R.id.textHint)
        textTitle = findViewById(R.id.textTitle)
        textError = findViewById(R.id.textError)
        reviewImage = findViewById(R.id.imageTaken)
        imageCCCD = findViewById(R.id.imageCCCD)

        identityCardViewModel = IdentityCardViewModel()

        identityCardViewModel.getEnableButton().observe(this) {
            if (doneTimerEnable) {
                return@observe
            }
            if (it) {
                buttonTakePic.isEnabled = true
                buttonTakePic.alpha = 1F
            } else {
                buttonTakePic.isEnabled = false
                buttonTakePic.alpha = 0.4F
            }
        }

        identityCardViewModel.getTextHintValue().observe(this) {
            runOnUiThread {
                if (it.isEmpty()) {
                    textHint.text = this.description
                } else {
                    textHint.text = it
                }
            }
        }

        setUpUI()

        timer = Timer("HideImageCCCD", false).schedule(3000) {
            runOnUiThread {
                imageCCCD.visibility = View.GONE
                textError.visibility = View.GONE
            }
        }

        timerEnableButton = Timer("HideImageCCCD", false).schedule(10) {
            runOnUiThread {
                identityCardViewModel.setEnableButton(true)
                doneTimerEnable = true
            }
        }

        graphicOverlay.cameraSelector = CameraSelector.LENS_FACING_BACK

        buttonBack.setOnClickListener {
            setResult(Activity.RESULT_CANCELED, Intent())
            finish()
        }

        cameraManager =
            IdentityCardCameraManager(
                this,
                preview,
                this,
                graphicOverlay,
                activeFrame,
                buttonTakePic,
                reviewImage,
                identityCardType,
                identityCardViewModel
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
        timer.cancel()
        timerEnableButton.cancel()
    }

    private fun setUpUI() {
        adjustLayout()
        val title = intent.extras?.getString("title")
        textTitle.text = title
        val type = intent.extras?.getString("type")
        if (type != null) {
            identityCardType = type
        }
        val description =
            intent.extras?.getString("description") ?: getString(R.string.identity_card_hint)
        textHint.text = description
        this.description = description
        val toastError = intent.extras?.getString("toastError")
        if (toastError != null) {
            if (toastError.isNotEmpty()) {
                textError.text = toastError
                textError.visibility = View.VISIBLE
            }
        }
        if (type == "BACK") {
            imageCCCD.setImageResource(R.drawable.ic_cccd_back)
        }
    }

    private fun adjustLayout() {
        // text hint
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val viewportMargin = Utils.dpToPx(this, 20)
        val width = displayMetrics.widthPixels.toFloat() - viewportMargin
        val bottom = width * 0.7 + Utils.dpToPx(this, 66)
        val lp = textHint.layoutParams as ConstraintLayout.LayoutParams
        lp.leftMargin = 0
        lp.topMargin = bottom.toInt() + Utils.dpToPx(this, 24)
        lp.rightMargin = 0
        lp.bottomMargin = 0
        textHint.requestLayout()

        // artwork
        val layoutParams = imageCCCD.layoutParams as ConstraintLayout.LayoutParams
        layoutParams.leftMargin = 0
        layoutParams.topMargin = viewportMargin + Utils.dpToPx(this, 134)
        layoutParams.rightMargin = 0
        layoutParams.bottomMargin = 0
        imageCCCD.requestLayout()
    }
}