package com.payme.sdk.models

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon
import vn.kalapa.ekyc.nfcsdk.models.NFCResultData

private val klaxon = Klaxon()

data class NFCVerificationData(
    @Json(name = "nfc_data")
    var data: NFCCardData? = NFCCardData(),
    @Json(name = "is_match")
    var isMatch: Boolean? = null,
    @Json(name = "matching_score")
    var matchingScore: Int? = null
) {
    fun toJson() = klaxon.toJsonString(this)

    companion object {
        fun fromJson(json: String) = klaxon.parse<NFCVerificationData>(json)
    }
}

data class NFCCardData(
    @Json(name = "card_data")
    var data: NFCResultData? = NFCResultData(),
    @Json(name = "is_valid")
    var isValid: Boolean? = null
) {
    fun toJson() = klaxon.toJsonString(this)

    companion object {
        fun fromJson(json: String) = klaxon.parse<NFCCardData>(json)
    }
}

data class NFCResultData(
    val mrz: String,
    val idCardNo: String,
    val oldIdCardNo: String,
    val name: String,
    val dateOfBirth: String,
    val gender: String,
    val nationality: String,
    val ethnic: String,
    val religion: String,
    val placeOfOrigin: String,
    val residenceAddress: String,
    val personalSpecificIdentification: String,
    val dateOfIssuance: String,
    val dateOfExpiry: String,
    val motherName: String,
    val fatherName: String,
    val spouseName: String? = null,
    val serial: String,
    val transID: String,
    val image: String
)