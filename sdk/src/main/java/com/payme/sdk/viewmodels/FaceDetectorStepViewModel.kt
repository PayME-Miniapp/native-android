package com.payme.sdk.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class FaceDetectorStepViewModel() : ViewModel() {
    private val step: MutableLiveData<Int> = MutableLiveData<Int>(1)
    private val textHint: MutableLiveData<String> = MutableLiveData<String>("")

    fun getStep(): LiveData<Int> {
        return step
    }

    fun setStep(step: Int) {
        this.step.value = step
    }

    fun getTextHint(): LiveData<String> {
        return textHint
    }

    fun setTextHint(text: String) {
        this.textHint.value = text
    }

}