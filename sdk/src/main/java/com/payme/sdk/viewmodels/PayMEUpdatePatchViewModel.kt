package com.payme.sdk.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class PayMEUpdatePatchViewModel() : ViewModel() {
    private val doneUpdate: MutableLiveData<Boolean> = MutableLiveData<Boolean>(false)
    private val showUpdatingUI: MutableLiveData<Boolean> = MutableLiveData<Boolean>(false)
    private val isForceUpdating: MutableLiveData<Boolean> = MutableLiveData<Boolean>(false)
    private val lostConnection: MutableLiveData<Boolean> = MutableLiveData<Boolean>(false)
    private val webLoaded: MutableLiveData<Boolean> = MutableLiveData<Boolean>(false)

    fun getDoneUpdate(): LiveData<Boolean> {
        return doneUpdate
    }

    fun setDoneUpdate(done: Boolean) {
        this.doneUpdate.postValue(done)
    }

    fun getShowUpdatingUI(): LiveData<Boolean> {
        return showUpdatingUI
    }

    fun setShowUpdatingUI(done: Boolean) {
        this.showUpdatingUI.postValue(done)
    }

    fun getIsForceUpdating(): LiveData<Boolean> {
        return isForceUpdating
    }

    fun setIsForceUpdating(isForceUpdating: Boolean) {
        this.isForceUpdating.postValue(isForceUpdating)
    }

    fun getIsLostConnection(): LiveData<Boolean> {
        return lostConnection
    }

    fun setIsLostConnection(value: Boolean) {
        this.lostConnection.postValue(value)
    }

    fun getWebLoaded(): LiveData<Boolean> {
        return webLoaded
    }

    fun setWebLoaded(value: Boolean) {
        this.webLoaded.postValue(value)
    }

}