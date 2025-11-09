package com.github.kate1234567.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.github.kate1234567.data.model.Message
import com.github.kate1234567.data.model.MessageData

@Composable
fun MessageItem(message: Message, onImageClick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Text(
            text = message.from,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )
        when (val data = message.data) {
            is MessageData.Text -> {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 1.dp
                ) {
                    Text(
                        text = data.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            is MessageData.Image -> {
                data.link?.let { link ->
                    Card(
                        modifier = Modifier
                            .clickable { onImageClick(link) },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter("https://faerytea.name/thumb/$link"),
                            contentDescription = null,
                            modifier = Modifier.size(200.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
}

