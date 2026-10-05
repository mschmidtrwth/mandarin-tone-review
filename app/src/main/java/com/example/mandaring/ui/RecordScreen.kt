package com.example.mandaring.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.mandaring.R
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun RecordScreen(viewModel: RecordViewModel) {
    val state = viewModel.state
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ChartPanel(
                take = state.current,
                playbackMs = state.playbackMs,
                onTogglePlayback = viewModel::togglePlayback,
                modifier = Modifier.padding(top = 16.dp),
            )

            RecordButton(
                recording = state.recording,
                onPress = {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        viewModel.startRecording()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onRelease = viewModel::stopRecording,
            )

            state.message?.let {
                Text(stringResource(it), color = MaterialTheme.colorScheme.error)
            }

            TakeList(
                takes = state.takes,
                selected = state.current?.file,
                onSelect = viewModel::select,
                onDelete = viewModel::delete,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ChartPanel(
    take: Take?,
    playbackMs: Float?,
    onTogglePlayback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
        ) {
            if (take == null || !take.track.hasVoicedFrames) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(if (take == null) R.string.hint_empty else R.string.hint_no_pitch),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                PitchChart(
                    track = take.track,
                    cursorMs = playbackMs,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = onTogglePlayback, enabled = take != null) {
                Text(stringResource(if (playbackMs == null) R.string.action_play else R.string.action_stop))
            }
            if (take != null) {
                Text(
                    stringResource(R.string.duration_seconds, take.audio.durationMs / 1000f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RecordButton(recording: Boolean, onPress: () -> Unit, onRelease: () -> Unit) {
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnRelease by rememberUpdatedState(onRelease)
    Surface(
        color = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        currentOnPress()
                        tryAwaitRelease()
                        currentOnRelease()
                    },
                )
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                stringResource(if (recording) R.string.record_active else R.string.record_idle),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun TakeList(
    takes: List<File>,
    selected: File?,
    onSelect: (File) -> Unit,
    onDelete: (File) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
    LazyColumn(modifier) {
        items(takes, key = { it.name }) { file ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(file) }
                    .padding(vertical = 4.dp),
            ) {
                Text(
                    dateFormat.format(Date(file.lastModified())),
                    color = if (file == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onDelete(file) }) {
                    Text(stringResource(R.string.action_delete))
                }
            }
            HorizontalDivider()
        }
    }
}
