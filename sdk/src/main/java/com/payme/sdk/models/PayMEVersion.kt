package com.payme.sdk.models

class PayMEVersion(patch: Int, version: String, mandatory: Boolean, url: String?) {
    var patch: Int = 0
    var version: String = "1.0.0"
    var mandatory: Boolean = false
    var url: String? = null

    init {
        this.patch = patch
        this.version = version
        this.mandatory = mandatory
        this.url = url
    }
}