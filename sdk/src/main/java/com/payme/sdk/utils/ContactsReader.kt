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
import com.google.gson.JsonArray
import com.google.gson.JsonObject

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

            val contacts = mutableListOf<Pair<String?, String?>>()
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
                        )
                        contacts.add(name to phoneNumber)
                    }
                }
            }
            WebViewJsDispatcher.evaluate(
                context as Activity,
                webView,
                "nativeContacts",
                contactsJson(contacts),
                null
            )
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "util get contacts exception ${e.message}")
        }
    }

    /** Contact names are arbitrary text: build the payload with a JSON writer, never by templating. */
    internal fun contactsJson(contacts: List<Pair<String?, String?>>): String {
        val array = JsonArray()
        contacts.forEach { (name, phone) ->
            array.add(JsonObject().apply {
                addProperty("name", name.orEmpty())
                addProperty("phone", phone.orEmpty().replace(NON_DIGITS, ""))
            })
        }
        return array.toString()
    }

    private val NON_DIGITS = Regex("[^0-9]")
}
