package com.payme.sdk.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import android.webkit.WebView
import androidx.core.content.ContextCompat
import com.payme.sdk.PayMEMiniApp
import org.json.JSONArray
import org.json.JSONObject

internal object ContactsReader {
    @SuppressLint("Range")
    fun sendContacts(context: Context, webView: WebView) {
        try {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_CONTACTS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            val contacts = JSONArray()
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null,
                null,
                null,
                null
            )
            cursor.use { cur ->
                if ((cur?.count ?: 0) > 0) {
                    while (cur != null && cur.moveToNext()) {
                        val name = cur.getString(
                            cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        )
                        val phoneNumber = cur.getString(
                            cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        ).replace("[^0-9]".toRegex(), "")
                        contacts.put(JSONObject("""{name:"$name",phone:"$phoneNumber"}"""))
                    }
                }
            }
            WebViewJsDispatcher.evaluate(
                context as Activity,
                webView,
                "nativeContacts",
                contacts.toString(),
                null
            )
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "util get contacts exception ${e.message}")
        }
    }
}
