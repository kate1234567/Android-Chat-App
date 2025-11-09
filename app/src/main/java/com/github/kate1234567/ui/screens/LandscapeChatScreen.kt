package com.github.kate1234567.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.kate1234567.R
import com.github.kate1234567.ui.viewmodel.ChatListViewModel
import com.github.kate1234567.ui.viewmodel.ChannelsState
import com.github.kate1234567.ui.viewmodel.MessageViewModel
import com.github.kate1234567.ui.viewmodel.MessagesState
import com.github.kate1234567.ui.viewmodel.SendState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandscapeChatScreen(
    username: String,
    selectedChannel: String?,
    onChannelSelected: (String) -> Unit,
    onLogout: () -> Unit,
    onImageClick: (String) -> Unit,
    chatListViewModel: ChatListViewModel,
    messageViewModel: MessageViewModel
) {
    var messageText by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }
    var dialogMessage by remember { mutableStateOf("") }

    val channelsState by chatListViewModel.channelsState.collectAsState()
    val messagesState by messageViewModel.messagesState.collectAsState()
    val sendState by messageViewModel.sendState.collectAsState()
    val isNetworkAvailable by messageViewModel.isNetworkAvailable.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        chatListViewModel.loadChannels()
    }

    LaunchedEffect(selectedChannel) {
        selectedChannel?.let { messageViewModel.loadMessages(it) }
    }

    var hasScrolledToBottom by remember(selectedChannel) { mutableStateOf(false) }
    LaunchedEffect(messagesState, selectedChannel) {
        if (messagesState is MessagesState.Success && !hasScrolledToBottom && selectedChannel != null) {
            val messages = (messagesState as MessagesState.Success).messages
            if (messages.isNotEmpty()) {
                delay(100)
                val lastIndex = messages.size - 1
                if (lastIndex >= 0) {
                    listState.scrollToItem(lastIndex)
                    hasScrolledToBottom = true
                }
            }
        }
    }

    LaunchedEffect(sendState) {
        if (sendState is SendState.Success && messagesState is MessagesState.Success) {
            val messages = (messagesState as MessagesState.Success).messages
            if (messages.isNotEmpty()) {
                delay(100)
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    var isLoadingMore by remember { mutableStateOf(false) }
    LaunchedEffect(listState, selectedChannel) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collectLatest { firstIndex ->
                if (firstIndex == 0 && !isLoadingMore && messagesState is MessagesState.Success && selectedChannel != null) {
                    val messages = (messagesState as MessagesState.Success).messages
                    if (messages.isNotEmpty()) {
                        isLoadingMore = true
                        android.util.Log.d("LandscapeChatScreen", "Loading more messages, current count: ${messages.size}")
                        messageViewModel.loadMessages(selectedChannel, isLoadMore = true)
                        delay(1000)
                        isLoadingMore = false
                    }
                }
            }
    }

    LaunchedEffect(sendState) {
        when (sendState) {
            is SendState.Success -> {
                messageText = ""
                messageViewModel.resetSendState()
            }
            is SendState.Error -> {
                dialogMessage = (sendState as SendState.Error).message
                showDialog = true
                messageViewModel.resetSendState()
            }
            else -> {}
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.error)) },
            text = { Text(dialogMessage) },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    Row(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.weight(1f),
            tonalElevation = 1.dp
        ) {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge
                            )
                            if (!isNetworkAvailable) {
                                Text(
                                    text = "Оффлайн-режим",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            chatListViewModel.logout()
                            onLogout()
                        }) {
                            Text(
                                text = stringResource(R.string.logout),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                when (channelsState) {
                    is ChannelsState.Idle, is ChannelsState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is ChannelsState.Success -> {
                        val channels = (channelsState as ChannelsState.Success).channels
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(channels) { channel ->
                                val isSelected = channel == selectedChannel
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onChannelSelected(channel) },
                                    colors = if (isSelected) {
                                        CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer
                                        )
                                    } else {
                                        CardDefaults.cardColors()
                                    },
                                    elevation = if (isSelected) {
                                        CardDefaults.cardElevation(defaultElevation = 4.dp)
                                    } else {
                                        CardDefaults.cardElevation(defaultElevation = 1.dp)
                                    },
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Text(
                                        text = channel,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        }
                    }
                    is ChannelsState.Error -> {
                        val message = (channelsState as ChannelsState.Error).message
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(message)
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { chatListViewModel.loadChannels() }) {
                                    Text(stringResource(R.string.retry))
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            selectedChannel?.let { channel ->
                TopAppBar(title = { Text(channel) })
                when (messagesState) {
                    is MessagesState.Loading -> {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is MessagesState.Success -> {
                        val messages = (messagesState as MessagesState.Success).messages
                        LazyColumn(modifier = Modifier.weight(1f), state = listState, reverseLayout = false) {
                            items(messages) { message ->
                                MessageItem(message, onImageClick)
                            }
                        }
                    }
                    is MessagesState.Error -> {
                        val message = (messagesState as MessagesState.Error).message
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(message)
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { messageViewModel.loadMessages(channel) }) {
                                    Text(stringResource(R.string.retry))
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        label = { Text(stringResource(R.string.message)) },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { messageViewModel.sendMessage(messageText, username) },
                        enabled = messageText.isNotBlank() && sendState !is SendState.Loading && isNetworkAvailable
                    ) {
                        if (sendState is SendState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Text(stringResource(R.string.send))
                        }
                    }
                }
            } ?: run {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.select_chat))
                }
            }
        }
    }
}
