package com.payme.sdk.ui

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.R
import com.payme.sdk.models.ActionOpenMiniApp
import com.payme.sdk.models.PayMEError
import com.payme.sdk.models.PayMEErrorType
import com.payme.sdk.utils.Utils

class MiniAppBottomSheetDialog : BottomSheetDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = BottomSheetDialog(requireContext(), theme)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.parseColor("#80000000")))
        dialog.setOnShowListener {
            val bottomSheetDialog = it as BottomSheetDialog
            bottomSheetDialog.setOnDismissListener {
                val action = MiniAppFragment.getMiniAppAction()
                MiniAppFragment.closeMiniApp()
                PayMEMiniApp.onError(action, PayMEError(PayMEErrorType.MiniApp, "USER_CANCEL", "User đóng PayMEMiniApp"))
            }
            val parentLayout =
                bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            parentLayout?.let { it ->
                val behaviour = BottomSheetBehavior.from(it)
                val backgroundDrawable = context?.let { it1 ->
                    ContextCompat.getDrawable(
                        it1,
                        R.drawable.rounded_dialog
                    )
                }
                it.background = backgroundDrawable

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

    private fun isFullHeightModal(): Boolean {
        val action = MiniAppFragment.getMiniAppAction()
        return action != ActionOpenMiniApp.PAY &&
                action != ActionOpenMiniApp.SERVICE &&
                action != ActionOpenMiniApp.PAYMENT &&
                action != ActionOpenMiniApp.TRANSFER_QR
        //khác các action này thì để max height default
    }

    private fun convertContentHeight(contentHeight: Int): Int {
        val maxHeight = (resources.displayMetrics.heightPixels * 0.9).toInt()
        val minHeight = (resources.displayMetrics.heightPixels * 0.4).toInt()

        if (isFullHeightModal()) {
            return maxHeight
        }

        return if (contentHeight == 0) minHeight
        else {
            val convertHeight = context?.let { Utils.dpToPx(it, contentHeight + 10) }
            if (convertHeight!! > maxHeight) // giới hạn ở 90% màn hình
                return maxHeight else return convertHeight!!
        }
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