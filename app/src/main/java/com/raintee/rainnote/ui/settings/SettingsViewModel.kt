package com.raintee.rainnote.ui.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SettingsViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "设置功能正在完善中。"
    }
    val text: LiveData<String> = _text
}
