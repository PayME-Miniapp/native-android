package com.payme.sdk.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class IdentityCardViewModel() : ViewModel() {
    private val enableButton: MutableLiveData<Boolean> = MutableLiveData<Boolean>(false)
    private val textHintValue: MutableLiveData<String> = MutableLiveData<String>("")

    fun getEnableButton(): LiveData<Boolean> {
        return enableButton
    }

    fun setEnableButton(enable: Boolean) {
        this.enableButton.value = enable
    }

    fun getTextHintValue(): LiveData<String> {
        return textHintValue
    }

    fun setTextHintValue(textValue: String) {
        this.textHintValue.value = textValue
    }

}