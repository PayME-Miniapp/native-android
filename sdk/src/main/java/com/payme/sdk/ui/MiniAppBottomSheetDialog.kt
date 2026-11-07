package com.payme.sdk.ui

import android.app.Dialog
import android.content.DialogInterface
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
import com.payme.sdk.R
import com.payme.sdk.models.ActionOpenMiniApp
import com.payme.sdk.runtime.PayMERuntime
import com.payme.sdk.utils.LightContext
import com.payme.sdk.utils.Utils

class MiniAppBottomSheetDialog : BottomSheetDialogFragment() {
    private val sessionId: String?
        get() = arguments?.getString(PayMERuntime.EXTRA_SESSION_ID)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Modal luôn sáng mà không đụng tới Activity của partner
        val dialog = BottomSheetDialog(LightContext.wrap(requireContext()), theme)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.parseColor("#80000000")))
        dialog.setOnShowListener {
            val bottomSheetDialog = it as BottomSheetDialog
            val parentLayout =
                bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            parentLayout?.let { parentView ->
                val behaviour = BottomSheetBehavior.from(parentView)
                val backgroundDrawable = context?.let { ctx ->
                    ContextCompat.getDrawable(
                        ctx, R.drawable.rounded_dialog
                    )
                }
                parentView.background = backgroundDrawable

                parentView.layoutParams.height = convertContentHeight(currentModalHeight())
                PayMERuntime.getSession(sessionId)?.onSetModalHeight = { ot ->
                    setupModalHeight(parentView, ot)
                }

                behaviour.state = BottomSheetBehavior.STATE_EXPANDED
                behaviour.skipCollapsed = true
                behaviour.isHideable = false
                behaviour.isDraggable = false
                parentView.clipToOutline = true
            }
        }
        return dialog
    }

    // Không dùng setOnDismissListener: nó thay listener của DialogFragment, fragment không được gỡ khi dismiss
    override fun onDismiss(dialog: DialogInterface) {
        PayMERuntime.getSession(sessionId)?.let { session ->
            val shouldNotifyCancel = session.shouldNotifyUserCancelOnDismiss()
            PayMERuntime.markSessionClosed(session.id)
            session.closeAction = null
            if (shouldNotifyCancel) {
                session.notifyUserCancel(getString(R.string.user_cancel_miniapp))
            }
            PayMERuntime.removeSession(session.id)
        }
        super.onDismiss(dialog)
    }

    private fun isFullHeightModal(): Boolean {
        val action = PayMERuntime.getSession(sessionId)?.action ?: ActionOpenMiniApp.PAYME
        return action != ActionOpenMiniApp.PAY && action != ActionOpenMiniApp.SERVICE && action != ActionOpenMiniApp.PAYMENT && action != ActionOpenMiniApp.TRANSFER_QR
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
                return maxHeight else return convertHeight
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
        // Activity của partner được khôi phục sau khi process bị huỷ: phiên đã mất, đóng modal
        // (MiniAppFragment con cũng tự đóng khi thiếu phiên).
        val session = PayMERuntime.getSession(sessionId) ?: run {
            dismissAllowingStateLoss()
            return
        }
        PayMERuntime.activate(sessionId)
        session.closeAction = {
            session.isCloseRequested = true
            PayMERuntime.markSessionClosed(session.id)
            dismissAllowingStateLoss()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_dialog_miniapp, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState == null && childFragmentManager.findFragmentById(R.id.fragment_container_view) == null) {
            val id = sessionId
            if (!id.isNullOrEmpty()) {
                childFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container_view, MiniAppFragment.newInstance(id))
                    .commitNow()
            }
        }
    }

    private fun currentModalHeight(): Int {
        return PayMERuntime.getSession(sessionId)?.modalHeight ?: 0
    }

    companion object {
        fun newInstance(sessionId: String): MiniAppBottomSheetDialog {
            return MiniAppBottomSheetDialog().apply {
                arguments = Bundle().apply {
                    putString(PayMERuntime.EXTRA_SESSION_ID, sessionId)
                }
            }
        }
    }
}
