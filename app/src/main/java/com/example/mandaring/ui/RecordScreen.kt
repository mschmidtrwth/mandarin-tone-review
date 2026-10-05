package com.example.mandaring.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.mandaring.R
import com.example.mandaring.data.Profiles
import com.example.mandaring.data.Reference
import com.example.mandaring.data.ReferenceLibrary
import com.example.mandaring.pitch.Comparison
import com.example.mandaring.pitch.PitchRange
import com.example.mandaring.pitch.Rating
import com.example.mandaring.pitch.Session
import com.example.mandaring.pitch.SyllableScore
import com.example.mandaring.pitch.Word
import com.example.mandaring.pitch.contour
import com.example.mandaring.pitch.tones
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs
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
                    when {
                        state.calibration != null ->
                            Text(stringResource(R.string.calibration_title, state.profiles.current.name))
                        state.training != null -> Text(stringResource(R.string.train_title))
                        else -> ProfileMenu(state.profiles, viewModel::selectProfile, viewModel::addProfile)
                    }
                },
                actions = {
                    if (state.calibration == null) {
                        if (state.training == null) {
                            TextButton(onClick = viewModel::openTraining) {
                                Text(stringResource(R.string.action_train))
                            }
                            TextButton(onClick = viewModel::startCalibration) {
                                Text(stringResource(R.string.action_calibrate))
                            }
                        }
                        OptionsMenu(state.keepRecordings, viewModel::setKeepRecordings)
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
            } else if (state.training != null) {
                BackHandler(onBack = viewModel::closeTraining)
                TrainingPanel(
                    state = state,
                    training = state.training,
                    onPress = onPress,
                    onRelease = viewModel::stopRecording,
                    onToggleTone = viewModel::toggleTrainingTone,
                    onStart = viewModel::startTraining,
                    onNext = { viewModel.nextTrainingWord() },
                    onSkip = { viewModel.nextTrainingWord(skip = true) },
                    onSaveRecording = viewModel::saveCurrent,
                    onTogglePlayback = viewModel::togglePlayback,
                    onToggleReferencePlayback = viewModel::toggleReferencePlayback,
                    onRestart = viewModel::openTraining,
                    onClose = viewModel::closeTraining,
                )
            } else {
                ReferencePicker(state.library, state.reference, viewModel::selectReference)
                val reference = state.reference
                if (reference == null) {
                    ChartPanel(
                        take = state.current,
                        range = state.profiles.current.range,
                        playbackMs = state.playbackMs,
                        onTogglePlayback = viewModel::togglePlayback,
                    )
                } else {
                    PracticePanel(
                        reference = reference,
                        take = state.current,
                        range = state.profiles.current.range,
                        recording = state.recording,
                        playbackMs = state.playbackMs,
                        referencePlaybackMs = state.referencePlaybackMs,
                        onTogglePlayback = viewModel::togglePlayback,
                        onToggleReferencePlayback = viewModel::toggleReferencePlayback,
                    )
                }
                RecordButton(state.recording, onPress, onRelease = viewModel::stopRecording)
                state.message?.let {
                    Text(stringResource(it), color = MaterialTheme.colorScheme.error)
                }
                SaveRecordingButton(state.current, viewModel::saveCurrent)
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

/** Offers to keep the take on show if it has not been kept. */
@Composable
private fun SaveRecordingButton(take: Take?, onSave: () -> Unit) {
    if (take == null || take.file != null) return
    OutlinedButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_save_recording))
    }
}

@Composable
private fun OptionsMenu(keepRecordings: Boolean, onKeepRecordings: (Boolean) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.option_keep_recordings)) },
                trailingIcon = { Checkbox(checked = keepRecordings, onCheckedChange = null) },
                onClick = { onKeepRecordings(!keepRecordings) },
            )
        }
    }
}

/** Setting up, working through and summing up a drill. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrainingPanel(
    state: RecordUiState,
    training: TrainingState,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onToggleTone: (String) -> Unit,
    onStart: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onSaveRecording: () -> Unit,
    onTogglePlayback: () -> Unit,
    onToggleReferencePlayback: () -> Unit,
    onRestart: () -> Unit,
    onClose: () -> Unit,
) {
    val session = training.session
    val range = state.profiles.current.range
    if (session == null) {
        val patterns = remember(state.library) {
            state.library?.items.orEmpty().map { tonePattern(it.word) }.distinct()
                .sortedWith(compareBy({ it.length }, { it }))
        }
        Text(stringResource(R.string.train_choose), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (pattern in patterns) {
                FilterChip(
                    selected = pattern in training.tones,
                    onClick = { onToggleTone(pattern) },
                    label = { Text(pattern) },
                )
            }
        }
        if (range == null) {
            Text(stringResource(R.string.train_calibrate_first), color = MaterialTheme.colorScheme.error)
        }
        Button(onClick = onStart, enabled = range != null && state.library != null, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_start))
        }
    } else if (session.done) {
        SessionSummary(session, onRestart, onClose)
    } else {
        val reference = state.reference ?: return
        Text(
            stringResource(R.string.train_progress, session.position + 1, session.queue.size),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(
            progress = { session.position.toFloat() / session.queue.size },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(reference.word.pinyin, style = MaterialTheme.typography.headlineMedium)
        PracticePanel(
            reference = reference,
            take = state.current,
            range = range,
            recording = state.recording,
            playbackMs = state.playbackMs,
            referencePlaybackMs = state.referencePlaybackMs,
            onTogglePlayback = onTogglePlayback,
            onToggleReferencePlayback = onToggleReferencePlayback,
        )
        RecordButton(state.recording, onPress, onRelease)
        state.message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        SaveRecordingButton(state.current, onSaveRecording)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onSkip, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_skip))
            }
            Button(onClick = onNext, enabled = training.rating != null, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_next))
            }
        }
    }
}

@Composable
private fun SessionSummary(session: Session, onRestart: () -> Unit, onClose: () -> Unit) {
    Text(stringResource(R.string.train_finished), style = MaterialTheme.typography.headlineSmall)
    Text(
        stringResource(R.string.train_summary, session.passed, session.queue.distinct().size),
        style = MaterialTheme.typography.titleMedium,
    )
    if (session.missed.isNotEmpty()) {
        Text(
            stringResource(
                R.string.train_missed,
                session.missed.joinToString(", ") { Word.parse(it)?.pinyin ?: it },
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onRestart) { Text(stringResource(R.string.train_again)) }
        TextButton(onClick = onClose) { Text(stringResource(R.string.action_done)) }
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

/** Chooses the word to practise from a sheet listing the [library], which also credits its source. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReferencePicker(library: ReferenceLibrary?, selected: Reference?, onSelect: (Reference?) -> Unit) {
    var open by remember { mutableStateOf(false) }

    OutlinedButton(onClick = { open = true }, enabled = library != null, modifier = Modifier.fillMaxWidth()) {
        Text(
            when {
                library == null -> stringResource(R.string.reference_loading)
                selected == null -> stringResource(R.string.reference_choose)
                else -> selected.word.pinyin
            },
            style = MaterialTheme.typography.titleMedium,
        )
    }

    if (open && library != null) {
        // Opened in full, so that the credit below the list is on screen.
        ModalBottomSheet(
            onDismissRequest = { open = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            @Composable
            fun Choice(text: String, reference: Reference?) {
                Text(
                    text,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (reference == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            open = false
                            onSelect(reference)
                        }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }

            var query by rememberSaveable { mutableStateOf("") }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.reference_search)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )
            // Grouped by tone pattern, which a search flattens.
            val groups = remember(library, query) {
                val needle = query.trim().lowercase()
                library.items
                    .filter { needle.isEmpty() || needle in it.name || needle in it.word.pinyin.lowercase() }
                    .groupBy { tonePattern(it.word) }
                    .toSortedMap(compareBy({ it.length }, { it }))
            }
            LazyColumn(Modifier.weight(1f, fill = false)) {
                if (query.isBlank()) item { Choice(stringResource(R.string.reference_none), null) }
                for ((pattern, references) in groups) {
                    item(key = "header-$pattern") {
                        Text(
                            pattern,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        )
                    }
                    items(references, key = { it.name }) { Choice(it.word.pinyin, it) }
                }
            }
            HorizontalDivider()
            Text(
                stringResource(R.string.reference_credit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }
}

/** The chart and controls for imitating [reference]: its contour as a ghost under the take's. */
@Composable
private fun PracticePanel(
    reference: Reference,
    take: Take?,
    range: PitchRange?,
    recording: Boolean,
    playbackMs: Float?,
    referencePlaybackMs: Float?,
    onTogglePlayback: () -> Unit,
    onToggleReferencePlayback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val comparison = remember(reference, take, range) {
        if (take != null && range != null) Comparison.of(reference.contour, reference.spans, take.track, range) else null
    }
    val overlay = comparison?.overlay
    val heard = remember(reference, take, range) {
        if (take != null && range != null) take.track.tones(range, reference.word.syllables.size) else null
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChartFrame {
            OverlayChart(
                reference = reference.contour,
                attempt = overlay?.attempt,
                syllables = comparison?.syllables.orEmpty(),
                cursorMs = referencePlaybackMs ?: playbackMs?.let { overlay?.referenceMs(it) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = onToggleReferencePlayback, enabled = !recording) {
                Text(stringResource(if (referencePlaybackMs == null) R.string.action_listen else R.string.action_stop))
            }
            FilledTonalButton(onClick = onTogglePlayback, enabled = take != null) {
                Text(stringResource(if (playbackMs == null) R.string.action_play else R.string.action_stop))
            }
            Text(
                stringResource(
                    when {
                        take == null -> R.string.practice_hint
                        take.track.voicedFrames == 0 -> R.string.hint_no_pitch
                        range == null -> R.string.practice_uncalibrated
                        else -> when (comparison?.rating) {
                            Rating.CLOSE -> R.string.rating_close
                            Rating.NEAR -> R.string.rating_near
                            Rating.OFF, null -> R.string.rating_off
                        }
                    },
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val syllables = comparison?.syllables.orEmpty()
        if (syllables.isNotEmpty()) {
            SyllableFeedback(reference.word, heard, syllables)
        } else if (heard != null) {
            HeardTones(reference.word, heard)
        }
    }
}

/** A card per syllable of [word]: how close it was, the tone [heard] if that is not the word's, and a pitch that is off. */
@Composable
private fun SyllableFeedback(word: Word, heard: List<Int>?, scores: List<SyllableScore>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        word.syllables.forEachIndexed { index, syllable ->
            val score = scores[index]
            val heardTone = heard?.getOrNull(index)?.takeIf { it != word.spokenTones[index] }
            val height = score.similarity?.heightError ?: 0f
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.weight(1f),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(syllable.pinyin, style = MaterialTheme.typography.titleLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .background(score.rating.color(), CircleShape),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(
                                when (score.rating) {
                                    Rating.CLOSE -> R.string.syllable_close
                                    Rating.NEAR -> R.string.syllable_near
                                    Rating.OFF -> R.string.syllable_off
                                },
                            ),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    if (heardTone != null) {
                        Text(
                            stringResource(R.string.syllable_heard, syllable.copy(tone = heardTone).pinyin),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (score.rating != Rating.CLOSE && abs(height) >= HEIGHT_HINT) {
                        Text(
                            stringResource(if (height > 0) R.string.syllable_too_high else R.string.syllable_too_low),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** How many tone levels off a syllable has to sit before it is said to be too high or too low. */
private const val HEIGHT_HINT = 1f

/** [word] written with the tones [heard] in a take; syllables whose tone is not the word's stand out. */
@Composable
private fun HeardTones(word: Word, heard: List<Int>) {
    val label = stringResource(R.string.heard_tones)
    val wrong = SpanStyle(color = MaterialTheme.colorScheme.error)
    val expected = word.spokenTones
    Text(
        buildAnnotatedString {
            append(label)
            word.syllables.forEachIndexed { index, syllable ->
                append(' ')
                val pinyin = syllable.copy(tone = heard[index]).pinyin
                if (heard[index] == expected[index]) append(pinyin) else withStyle(wrong) { append(pinyin) }
            }
        },
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun ChartFrame(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
        content = content,
    )
}

@Composable
private fun ChartPanel(
    take: Take?,
    range: PitchRange?,
    playbackMs: Float?,
    onTogglePlayback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChartFrame {
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
