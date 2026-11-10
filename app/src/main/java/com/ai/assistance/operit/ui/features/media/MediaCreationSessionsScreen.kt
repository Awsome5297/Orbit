package com.ai.assistance.operit.ui.features.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.media.MediaCreationRepository
import com.ai.assistance.operit.data.model.MediaCreationSessionEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaCreationSessionsScreen(
    onOpenSession: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { MediaCreationRepository.getInstance(context) }
    val scope = rememberCoroutineScope()
    val sessions by repository.observeSessions().collectAsState(initial = emptyList())
    var pendingDelete by remember { mutableStateOf<MediaCreationSessionEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.media_creation_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    scope.launch { onOpenSession(repository.createSession()) }
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.media_creation_new_session)) },
            )
        },
    ) { padding ->
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.media_creation_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(sessions, key = { it.id }) { session ->
                    MediaCreationSessionCard(
                        session = session,
                        onOpen = { onOpenSession(session.id) },
                        onDelete = { pendingDelete = session },
                    )
                }
            }
        }
    }

    pendingDelete?.let { session ->
        val title = if (session.title.isBlank()) {
            stringResource(R.string.media_creation_unnamed)
        } else {
            session.title
        }
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.media_creation_delete_title)) },
            text = { Text(stringResource(R.string.media_creation_delete_message, title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        scope.launch { repository.deleteSession(session.id) }
                    }
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun MediaCreationSessionCard(
    session: MediaCreationSessionEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Palette,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(12.dp))
            val title = if (session.title.isBlank()) {
                stringResource(R.string.media_creation_unnamed)
            } else {
                session.title
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.media_creation_items_count, session.nodeCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
            }
        }
    }
}
