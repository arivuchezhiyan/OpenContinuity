package com.opencontinuity.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.opencontinuity.OpenContinuityApp
import com.opencontinuity.core.protocol.SmsConversation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsScreen(navController: NavController) {
    val smsManager = OpenContinuityApp.instance.smsDataManager
    val conversations by smsManager.conversations.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SMS Messages") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Sms,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No messages synced to PC yet", color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues)
            ) {
                items(conversations) { conversation ->
                    ListItem(
                        headlineContent = { Text(conversation.contactName ?: conversation.address) },
                        supportingContent = { Text(conversation.snippet ?: "", maxLines = 1) },
                        trailingContent = { Text(formatTimestamp(conversation.timestamp)) }
                    )
                    Divider()
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    // Basic timestamp formatting
    return java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
}
