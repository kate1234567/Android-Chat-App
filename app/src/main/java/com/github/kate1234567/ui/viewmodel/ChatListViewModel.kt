package com.github.kate1234567.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.kate1234567.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val repository: ChatRepository
) : ViewModel() {

    private val _channelsState = MutableStateFlow<ChannelsState>(ChannelsState.Idle)
    val channelsState: StateFlow<ChannelsState> = _channelsState

    private val _isNetworkAvailable = MutableStateFlow(true)

    private var isChannelsLoaded = false

    init {
        viewModelScope.launch {
            repository.networkStatus.collect { isAvailable ->
                _isNetworkAvailable.value = isAvailable
                android.util.Log.d("ChatListViewModel", "Network status changed: $isAvailable")
            }
        }
    }

    fun loadChannels() {
        if (isChannelsLoaded && _channelsState.value is ChannelsState.Success) {
            android.util.Log.d("ChatListViewModel", "Channels already loaded, skipping")
            return
        }

        android.util.Log.d("ChatListViewModel", "Loading channels...")
        _channelsState.value = ChannelsState.Loading
        viewModelScope.launch {
            val result = repository.getChannels()
            _channelsState.value = if (result.isSuccess) {
                isChannelsLoaded = true
                android.util.Log.d("ChatListViewModel", "Channels loaded successfully, count: ${result.getOrDefault(emptyList()).size}")
                ChannelsState.Success(result.getOrDefault(emptyList()))
            } else {
                ChannelsState.Error(result.exceptionOrNull()?.message ?: "Неизвестная ошибка")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
        }
    }
}

sealed class ChannelsState {
    data object Idle : ChannelsState()
    data object Loading : ChannelsState()
    data class Success(val channels: List<String>) : ChannelsState()
    data class Error(val message: String) : ChannelsState()
}
