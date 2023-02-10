package com.payme.sdk.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.payme.sdk.models.OpenMiniAppDataInterface

class MiniappViewModel() : ViewModel() {
  private val evaluateJsData: MutableLiveData<Pair<String, String>> =
    MutableLiveData<Pair<String, String>>(
      Pair("", "")
    )

  fun getEvaluateJsData(): LiveData<Pair<String, String>> {
    return evaluateJsData
  }

  fun setEvaluateJsData(data: Pair<String, String>) {
    this.evaluateJsData.postValue(data)
  }

  private val openMiniAppData: MutableLiveData<OpenMiniAppDataInterface?> = MutableLiveData<OpenMiniAppDataInterface?>(null)

  fun getOpenMiniAppData(): LiveData<OpenMiniAppDataInterface?> {
    return openMiniAppData
  }

  fun setOpenMiniAppData(data: OpenMiniAppDataInterface?) {
    this.openMiniAppData.postValue(data)
  }

}