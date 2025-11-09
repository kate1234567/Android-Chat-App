package com.github.kate1234567.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NavigationViewModel : ViewModel() {
    private val _selectedChannel = MutableStateFlow<String?>(null)
    val selectedChannel: StateFlow<String?> = _selectedChannel

    private val _openedImageLink = MutableStateFlow<String?>(null)
    val openedImageLink: StateFlow<String?> = _openedImageLink

    fun selectChannel(channel: String) {
        _selectedChannel.value = channel
    }

    fun clearChannel() {
        _selectedChannel.value = null
    }

    fun openImage(link: String) {
        _openedImageLink.value = link
    }

    fun closeImage() {
        _openedImageLink.value = null
    }
}

