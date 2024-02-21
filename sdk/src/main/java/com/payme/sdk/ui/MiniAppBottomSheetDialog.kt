package com.payme.sdk.ui

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.payme.sdk.R
import com.payme.sdk.models.ActionOpenMiniApp
import org.json.JSONObject

class MiniAppBottomSheetDialog : BottomSheetDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = BottomSheetDialog(requireContext(), theme)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.parseColor("#80000000")))
        dialog.setOnShowListener {
            val bottomSheetDialog = it as BottomSheetDialog
            val parentLayout =
                bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            parentLayout?.let { it ->
                val behaviour = BottomSheetBehavior.from(it)
                it.layoutParams.height = convertContentHeight(MiniAppFragment.modalHeight)
                MiniAppFragment.onSetModalHeight = { ot ->
                    setupModalHeight(it, ot)
                }
                behaviour.state = BottomSheetBehavior.STATE_EXPANDED
                behaviour.skipCollapsed = true
                behaviour.isHideable = false
                behaviour.isDraggable = false
                it.clipToOutline = true
            }
        }
        return dialog
    }

    private fun getMiniAppAction(): ActionOpenMiniApp {
        val json = MiniAppFragment.openMiniAppData.toJsonData()
        val jsonObject = JSONObject(json.toString())
        val actionString = jsonObject?.getString("action")

        return try {
            ActionOpenMiniApp.valueOf(((actionString ?: ActionOpenMiniApp.PAYME).toString()))
        } catch (e: IllegalArgumentException) {
            ActionOpenMiniApp.PAYME
        }
    }

    private fun isFullHeightModal(): Boolean {
        val action = getMiniAppAction()
        return action != ActionOpenMiniApp.PAY &&
                action != ActionOpenMiniApp.SERVICE &&
                action != ActionOpenMiniApp.PAYMENT
    }

    private fun convertContentHeight(contentHeight: Int): Int {
        if (isFullHeightModal()) {
            return (resources.displayMetrics.heightPixels * 0.9).toInt()
        }
        val scale: Float = resources.displayMetrics.density
        val defaultHeight =
            (resources.displayMetrics.heightPixels * 0.4).toInt()
        return if (contentHeight == 0) defaultHeight else ((contentHeight + 10) * scale).toInt()
    }

    private fun setupModalHeight(bottomSheet: View, contentHeight: Int?) {
        if (contentHeight == null || contentHeight == 0) {
            return
        }

        val windowHeight = convertContentHeight(contentHeight)
        bottomSheet.layoutParams.height = windowHeight
        bottomSheet.post {
            bottomSheet.requestLayout()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view: View = inflater.inflate(R.layout.bottom_sheet_dialog_miniapp, container, false)
        return view
    }
}