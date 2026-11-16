package com.payme.sdk.utils

import com.google.gson.JsonObject

/** Payload của nativePickedContact. Tên là chữ tuỳ ý: dựng bằng JSON writer, không ghép chuỗi. */
internal object PickedContact {
    private val NON_DIGITS = Regex("[^0-9]")

    /** `null` khi không còn chữ số nào: không có gì để gửi web. */
    fun json(name: String?, number: String?): String? {
        val phone = number.orEmpty().replace(NON_DIGITS, "")
        if (phone.isEmpty()) return null
        return JsonObject().apply {
            addProperty("name", name.orEmpty())
            addProperty("phone", phone)
        }.toString()
    }
}
