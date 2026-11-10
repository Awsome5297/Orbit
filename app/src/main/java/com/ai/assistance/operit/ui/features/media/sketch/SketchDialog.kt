package com.ai.assistance.operit.ui.features.media.sketch

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.ai.assistance.operit.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private const val TAG = "MediaSketchDialog"

@Composable
internal fun MediaSketchDialog(
    onDismiss: () -> Unit,
    onSave: (File) -> Unit,
    aspectRatio: Float? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { SketchState(aspectRatio) }
    var saving by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val loadFailed = stringResource(R.string.media_creation_sketch_image_load_failed)
    val exportFailed = stringResource(R.string.media_creation_sketch_export_failed)

    fun loadBackground(uri: Uri) {
        scope.launch {
            val image = loadSketchBackground(context, uri)
            if (image != null) {
                state.useBackground(image)
            } else {
                Toast.makeText(context, loadFailed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) loadBackground(uri)
    }

    fun close() {
        if (state.isBlank) onDismiss() else confirmDiscard = true
    }

    fun save() {
        if (state.isBlank || saving || !state.paperSize.isSpecified) return
        saving = true
        val result = state.toSketchResult()
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val target = File(context.cacheDir, "sketch_${UUID.randomUUID()}.png")
                    target.writeBytes(result.encode())
                    target
                }.onFailure {
                    Log.e(TAG, "Failed to export sketch", it)
                }.getOrNull()
            }
            if (file != null) {
                onSave(file)
            } else {
                saving = false
                Toast.makeText(context, exportFailed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Dialog(
        onDismissRequest = ::close,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        val containerColor = MaterialTheme.colorScheme.surfaceContainer
        val lightContainer = containerColor.luminance() > 0.5f
        SideEffect {
            if (window != null) {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = lightContainer
                    isAppearanceLightNavigationBars = lightContainer
                }
            }
        }

        Surface(modifier = Modifier.fillMaxSize(), color = containerColor) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.systemBars.union(WindowInsets.displayCutout),
                    ),
            ) {
                SketchTopBar(
                    state = state,
                    saving = saving,
                    onClose = ::close,
                    onChooseImage = { imagePicker.launch("image/*") },
                    onConfirm = ::save,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    SketchCanvas(state = state, modifier = Modifier.fillMaxSize())
                    if (state.pickingColor) {
                        SketchColorPicker(
                            state = state,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(8.dp),
                        )
                    }
                }
                SketchToolbar(state = state)
            }
        }
    }

    if (state.writingAt != null) {
        SketchTextInput(onConfirm = state::write, onDismiss = state::cancelText)
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.media_creation_sketch_discard_title)) },
            text = { Text(stringResource(R.string.media_creation_sketch_discard_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        onDismiss()
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun SketchTopBar(
    state: SketchState,
    saving: Boolean,
    onClose: () -> Unit,
    onChooseImage: () -> Unit,
    onConfirm: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(android.R.string.cancel),
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = state::undo, enabled = state.canUndo) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Undo,
                contentDescription = stringResource(R.string.media_creation_sketch_undo),
            )
        }
        IconButton(onClick = state::redo, enabled = state.canRedo) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Redo,
                contentDescription = stringResource(R.string.media_creation_sketch_redo),
            )
        }
        IconButton(onClick = state::clear, enabled = !state.isBlank) {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = stringResource(R.string.media_creation_sketch_clear),
            )
        }
        SketchPaperMenu(state = state, onChooseImage = onChooseImage)
        Spacer(Modifier.weight(1f))
        FilledIconButton(onClick = onConfirm, enabled = !state.isBlank && !saving) {
            if (saving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.save),
                )
            }
        }
    }
}

@Composable
private fun SketchTextInput(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.media_creation_sketch_write_text)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                maxLines = 5,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
