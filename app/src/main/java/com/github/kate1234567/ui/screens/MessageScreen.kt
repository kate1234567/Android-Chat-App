package com.github.kate1234567.ui.screens

import androidx.activity.compose.BackHandler
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
import com.github.kate1234567.ui.viewmodel.MessageViewModel
import com.github.kate1234567.ui.viewmodel.MessagesState
import com.github.kate1234567.ui.viewmodel.SendState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen(
    channel: String,
    username: String,
    onBack: () -> Unit,
    onImageClick: (String) -> Unit,
    viewModel: MessageViewModel
) {
    var messageText by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }
    var dialogMessage by remember { mutableStateOf("") }

    val messagesState by viewModel.messagesState.collectAsState()
    val sendState by viewModel.sendState.collectAsState()
    val isNetworkAvailable by viewModel.isNetworkAvailable.collectAsState()
    val listState = rememberLazyListState()

    BackHandler {
        onBack()
    }

    LaunchedEffect(channel) {
        viewModel.loadMessages(channel)
    }

    var hasScrolledToBottom by remember(channel) { mutableStateOf(false) }
    LaunchedEffect(messagesState, channel) {
        if (messagesState is MessagesState.Success && !hasScrolledToBottom) {
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
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collectLatest { firstIndex ->
                if (firstIndex == 0 && !isLoadingMore && messagesState is MessagesState.Success) {
                    val messages = (messagesState as MessagesState.Success).messages
                    if (messages.isNotEmpty()) {
                        isLoadingMore = true
                        android.util.Log.d("MessageScreen", "Loading more messages, current count: ${messages.size}")
                        viewModel.loadMessages(channel, isLoadMore = true)
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
                viewModel.resetSendState()
            }

            is SendState.Error -> {
                dialogMessage = (sendState as SendState.Error).message
                showDialog = true
                viewModel.resetSendState()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = channel,
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
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(
                            text = "← ${stringResource(R.string.back)}",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (messagesState) {
                is MessagesState.Loading -> {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is MessagesState.Success -> {
                    val messages = (messagesState as MessagesState.Success).messages
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        state = listState,
                        reverseLayout = false
                    ) {
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
                            Button(onClick = { viewModel.loadMessages(channel) }) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
            }
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text(stringResource(R.string.message)) },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.large,
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = {
                            if (isNetworkAvailable) {
                                viewModel.sendMessage(messageText, username)
                            }
                        },
                        modifier = Modifier.size(56.dp),
                        containerColor = if (isNetworkAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = 4.dp,
                            pressedElevation = 8.dp
                        )
                    ) {
                        if (sendState is SendState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "→",
                                style = MaterialTheme.typography.headlineMedium,
                                color = if (isNetworkAvailable) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
