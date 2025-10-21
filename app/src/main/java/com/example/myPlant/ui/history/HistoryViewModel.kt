package com.example.myPlant.ui.history

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class HistoryViewModel : ViewModel() {
    private val _text = MutableLiveData<String>().apply {
        value = "This is the history screen"
    }
    val text: LiveData<String> = _text
}
