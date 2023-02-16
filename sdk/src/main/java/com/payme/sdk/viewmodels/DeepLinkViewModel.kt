package com.payme.sdk.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class DeepLinkViewModel: ViewModel() {
    private val deepLinkUrl: MutableLiveData<String> = MutableLiveData<String>("")

    fun getDeepLinkUrl(): LiveData<String> {
        return deepLinkUrl
    }

    fun setDeepLinkUrl(data: String) {
        this.deepLinkUrl.postValue(data)
    }
}