package com.example.mandaring.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.mandaring.R
import com.example.mandaring.data.Profiles
import com.example.mandaring.pitch.PitchRange
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(viewModel: RecordViewModel) {
    val state = viewModel.state
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val onPress = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.startRecording() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    if (state.calibration == null) {
                        ProfileMenu(state.profiles, viewModel::selectProfile, viewModel::addProfile)
                    } else {
                        Text(stringResource(R.string.calibration_title, state.profiles.current.name))
                    }
                },
                actions = {
                    if (state.calibration == null) {
                        TextButton(onClick = viewModel::startCalibration) {
                            Text(stringResource(R.string.action_calibrate))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val calibration = state.calibration
            if (calibration != null) {
                BackHandler(onBack = viewModel::cancelCalibration)
                CalibrationPanel(
                    calibration = calibration,
                    recording = state.recording,
                    message = state.message,
                    onPress = onPress,
                    onRelease = viewModel::stopRecording,
                    onSave = viewModel::saveCalibration,
                    onRedo = viewModel::startCalibration,
                    onCancel = viewModel::cancelCalibration,
                )
            } else {
                ChartPanel(
                    take = state.current,
                    range = state.profiles.current.range,
                    playbackMs = state.playbackMs,
                    onTogglePlayback = viewModel::togglePlayback,
                )
                RecordButton(state.recording, onPress, onRelease = viewModel::stopRecording)
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
}

@Composable
private fun ProfileMenu(profiles: Profiles, onSelect: (String) -> Unit, onAdd: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { expanded = true }) {
            Text("${profiles.current.name} ▾", style = MaterialTheme.typography.titleLarge)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (profile in profiles.all) {
                DropdownMenuItem(
                    text = { Text(profile.name) },
                    onClick = {
                        expanded = false
                        onSelect(profile.id)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_new_profile)) },
                onClick = {
                    expanded = false
                    naming = true
                },
            )
        }
    }

    if (naming) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text(stringResource(R.string.action_new_profile)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.profile_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        naming = false
                        onAdd(name)
                    },
                ) { Text(stringResource(R.string.action_add)) }
            },
            dismissButton = {
                TextButton(onClick = { naming = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun CalibrationPanel(
    calibration: CalibrationState,
    recording: Boolean,
    @StringRes message: Int?,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onSave: () -> Unit,
    onRedo: () -> Unit,
    onCancel: () -> Unit,
) {
    val step = calibration.current
    if (step != null) {
        Text(
            stringResource(R.string.calibration_step, calibration.step + 1, CalibrationStep.entries.size),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(step.prompt), style = MaterialTheme.typography.headlineSmall)
        RecordButton(recording, onPress, onRelease)
        message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
    } else {
        val range = calibration.range
        Text(
            if (range != null) rangeText(range) else stringResource(R.string.calibration_too_narrow),
            style = MaterialTheme.typography.headlineSmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onSave, enabled = range != null) { Text(stringResource(R.string.action_save)) }
            FilledTonalButton(onClick = onRedo) { Text(stringResource(R.string.action_redo)) }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}

@Composable
private fun rangeText(range: PitchRange) =
    stringResource(R.string.range_hz, range.lowHz.roundToInt(), range.highHz.roundToInt())

@Composable
private fun ChartPanel(
    take: Take?,
    range: PitchRange?,
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
            if (take == null || take.track.voicedFrames == 0) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(if (take == null) R.string.hint_empty else R.string.hint_no_pitch),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                PitchChart(
                    track = take.track,
                    range = range,
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
            Text(
                if (range != null) rangeText(range) else stringResource(R.string.range_unknown),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
