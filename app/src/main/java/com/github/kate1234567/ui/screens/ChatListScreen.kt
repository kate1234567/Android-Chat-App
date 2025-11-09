package com.github.kate1234567.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.kate1234567.R
import com.github.kate1234567.ui.viewmodel.ChatListViewModel
import com.github.kate1234567.ui.viewmodel.ChannelsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onChatSelected: (String) -> Unit,
    onLogout: () -> Unit,
    viewModel: ChatListViewModel = viewModel()
) {
    val channelsState by viewModel.channelsState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadChannels()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.logout()
                            onLogout()
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.logout),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        when (channelsState) {
            is ChannelsState.Idle, is ChannelsState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is ChannelsState.Success -> {
                val channels = (channelsState as ChannelsState.Success).channels
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(channels) { channel ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onChatSelected(channel) },
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(48.dp),
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = channel.firstOrNull()?.uppercase() ?: "#",
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = channel,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
            is ChannelsState.Error -> {
                val message = (channelsState as ChannelsState.Error).message
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.loadChannels() },
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }
    }
}
