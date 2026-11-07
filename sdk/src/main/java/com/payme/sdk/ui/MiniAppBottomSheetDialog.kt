package com.payme.sdk.ui

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.R
import com.payme.sdk.models.ActionOpenMiniApp
import com.payme.sdk.runtime.HostLifecycle
import com.payme.sdk.runtime.PayMERuntime
import com.payme.sdk.utils.LightContext
import com.payme.sdk.utils.SdkLocale
import com.payme.sdk.utils.Utils
import java.lang.ref.WeakReference

/**
 * close() không được ném lỗi: fragment đã tách khỏi Activity (Don't keep activities, Activity chưa dựng lại)
 * thì bỏ qua; bản khôi phục tự đóng vì phiên đã bị xoá.
 */
internal fun DialogFragment.dismissSafely() {
    if (isRemoving) return
    try {
        dismissAllowingStateLoss()
    } catch (e: IllegalStateException) {
        Log.w(PayMEMiniApp.TAG, "dismiss skipped: fragment detached")
    }
}

class MiniAppBottomSheetDialog : BottomSheetDialogFragment() {
    private val sessionId: String?
        get() = arguments?.getString(PayMERuntime.EXTRA_SESSION_ID)
    private var isDestroyingView = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Modal luôn sáng mà không đụng tới Activity của partner
        val dialog = BottomSheetDialog(LightContext.wrap(requireContext()), theme)
        // Không cancelable: back của Material chỉ thu nhỏ sheet (không hideable) và chặn back của mini-app.
        // Back do MiniAppFragment xử lý qua onBackPressedDispatcher của dialog; chạm ngoài vẫn đóng (bên dưới).
        isCancelable = false
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.parseColor("#80000000")))
        dialog.setOnShowListener {
            // onShow tới qua Handler: fragment có thể đã bị huỷ (xoay màn hình liên tiếp) -> resources sẽ crash
            if (!isAdded) return@setOnShowListener
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
                // Phiên nằm trong PayMERuntime (static): chỉ giữ yếu view/fragment
                val viewRef = WeakReference(parentView)
                val selfRef = WeakReference(this)
                PayMERuntime.getSession(sessionId)?.onSetModalHeight = { ot ->
                    val v = viewRef.get()
                    if (v != null) selfRef.get()?.setupModalHeight(v, ot)
                }

                behaviour.state = BottomSheetBehavior.STATE_EXPANDED
                behaviour.skipCollapsed = true
                behaviour.isHideable = false
                behaviour.isDraggable = false
                parentView.clipToOutline = true
            }
            bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.touch_outside)?.setOnClickListener {
                // Người dùng chạm ngoài: đóng như trước, onDismiss báo USER_CANCEL
                bottomSheetDialog.cancel()
            }
        }
        return dialog
    }

    // Không dùng setOnDismissListener: nó thay listener của DialogFragment, fragment không được gỡ khi dismiss
    // DialogFragment.onDestroyView cũng gọi onDismiss (cả khi xoay màn hình hoặc Activity partner bị huỷ)
    override fun onDismiss(dialog: DialogInterface) {
        val hostRecreating = HostLifecycle.isModalHostRecreating(isDestroyingView, isRemoving, activity?.isFinishing == true)
        // Xoay màn hình / hệ thống huỷ tạm Activity: phiên giữ nguyên cho modal được khôi phục
        if (!hostRecreating) {
            val description = SdkLocale.wrap(requireContext()).getString(R.string.user_cancel_miniapp)
            PayMERuntime.endSessionFromHost(sessionId) { description }
        }
        super.onDismiss(dialog)
    }

    override fun onDestroyView() {
        isDestroyingView = true
        super.onDestroyView()
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
        if (contentHeight == null || contentHeight == 0 || !isAdded) {
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
        session.attachHost(this) { f -> f.isAdded && f.activity?.let { !it.isFinishing && !it.isDestroyed } == true }
        // Giữ yếu: phiên static không được giữ fragment/Activity đã huỷ
        val selfRef = WeakReference(this)
        session.closeAction = {
            PayMERuntime.markCloseRequested(session)
            PayMERuntime.markSessionClosed(session.id)
            selfRef.get()?.dismissSafely()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        // Fragment được dựng lại view (cùng instance): bỏ cờ của lần huỷ view trước
        isDestroyingView = false
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
