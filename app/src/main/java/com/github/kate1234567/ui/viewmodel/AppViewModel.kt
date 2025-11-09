package com.github.kate1234567.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.kate1234567.data.repository.ChatRepository
import com.github.kate1234567.data.websocket.WebSocketManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val repository: ChatRepository,
    val webSocketManager: WebSocketManager
) : ViewModel() {

    val currentUser = repository.savedCredentials
        .map { it.first }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val authToken = repository.authToken
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            authToken.collect { token ->
                if (token != null) {
                    currentUser.value?.let { user ->
                        webSocketManager.connect(user, token)
                    }
                } else {
                    webSocketManager.disconnect()
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
        }
    }

    override fun onCleared() {
        super.onCleared()
        webSocketManager.disconnect()
    }
}
