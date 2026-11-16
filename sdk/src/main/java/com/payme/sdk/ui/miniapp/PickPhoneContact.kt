package com.payme.sdk.ui.miniapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.result.contract.ActivityResultContract

/**
 * Bộ chọn số điện thoại của hệ thống: không cần READ_CONTACTS (hệ thống cấp quyền đọc tạm cho URI trả về).
 * Android 17 tự chuyển ACTION_PICK theo Phone.CONTENT_TYPE sang Contact Picker mới.
 */
internal class PickPhoneContact : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_PICK).setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}
