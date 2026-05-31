package com.raintee.rainnote.ui.slideshow

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SlideshowViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "雨笺是一款以便签、卡片和行块为核心的轻量记录应用。"
    }
    val text: LiveData<String> = _text
}
