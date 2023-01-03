package com.payme.sdk.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.json.JSONObject

class NotificationViewModel() : ViewModel() {
  private val notificationData: MutableLiveData<JSONObject> =
    MutableLiveData<JSONObject>(JSONObject())

  private val notificationJSON: MutableLiveData<JSONObject> =
    MutableLiveData<JSONObject>(JSONObject())

  fun getNotificationData(): LiveData<JSONObject> {
    return notificationData
  }

  fun setNotificationData(data: JSONObject) {
    this.notificationData.postValue(data)
  }

  fun getNotificationJSON(): LiveData<JSONObject> {
    return notificationJSON
  }

  fun setNotificationJSON(data: JSONObject) {
    this.notificationJSON.postValue(data)
  }

}