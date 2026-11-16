package com.contax.app.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.contax.app.R
import com.contax.app.data.backup.Conflict
import com.contax.app.data.model.BackupDestination
import com.contax.app.data.model.BackupFormat
import com.contax.app.data.model.BackupInfo
import com.contax.app.data.model.EncryptionType
import com.contax.app.data.model.ScheduleMode
import com.contax.app.ui.common.ConfirmDialog
import com.contax.app.ui.common.RadioRow
import com.contax.app.ui.common.SectionHeader
import com.contax.app.ui.common.showToast
import com.contax.app.ui.common.sourceLabel
import com.contax.app.util.Fa
import com.contax.app.util.formatJalali
import com.contax.app.util.formatPhone
import com.contax.app.util.formatSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(vm: BackupViewModel = viewModel()) {
    val context = LocalContext.current
    val history by vm.history.collectAsState()
    val settings by vm.settings.collectAsState()
    val contactCount by vm.contactCount.collectAsState()
    val create by vm.create.collectAsState()
    val restoreStep by vm.restoreStep.collectAsState()
    val compareState by vm.compareState.collectAsState()
    val toastMsg by vm.toast.collectAsState()

    LaunchedEffect(Unit) { vm.refresh() }
    val toastText = toastMsg
    if (toastText != null) {
        LaunchedEffect(toastText) {
            showToast(context, toastText)
            vm.consumeToast()
        }
    }

    var deleteTarget by remember { mutableStateOf<BackupInfo?>(null) }

    val safCreateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri -> if (uri != null) vm.createBackup(uri) }

    val safOpenLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.restoreFile(uri) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            title = { Text(stringResource(R.string.backup_title), fontWeight = FontWeight.Bold) },
            actions = {
                Icon(
                    Icons.Rounded.CloudUpload, contentDescription = null,
                    modifier = Modifier.padding(end = 16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))

            // ── Status card ──
            val last = history.firstOrNull()
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            Modifier.size(44.dp).background(
                                if (last != null) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (last != null) Icons.Rounded.Shield else Icons.Rounded.Info,
                                contentDescription = null,
                                tint = if (last != null) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.last_backup_status),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                last?.let { formatJalali(it.createdAt) }
                                    ?: stringResource(R.string.last_backup_none),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    last?.let {
                        Text(
                            "${Fa.num(it.contactCount)} " + stringResource(R.string.contacts_count_label) +
                                "  |  " + stringResource(R.string.size_label) + ": " + formatSize(it.sizeKb),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { vm.startRestore() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Restore, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.restore))
                }
                OutlinedButton(
                    onClick = { vm.openCreate() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.CloudUpload, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.new_backup))
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionHeader(stringResource(R.string.backup_format))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BackupFormat.entries.forEach { f ->
                    FilterChip(
                        selected = create.format == f,
                        onClick = { vm.updateCreate { it.copy(format = f) } },
                        label = { Text(f.name) }
                    )
                }
            }

            // ── Schedule ──
            Spacer(Modifier.height(12.dp))
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.schedule_title), fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScheduleMode.entries.forEach { m ->
                            FilterChip(
                                selected = settings.scheduleMode == m,
                                onClick = {
                                    if (m != ScheduleMode.OFF) {
                                        vm.setSchedule(m, settings.scheduleHour, settings.autoFormat)
                                    } else {
                                        vm.setSchedule(ScheduleMode.OFF, settings.scheduleHour, settings.autoFormat)
                                    }
                                },
                                label = {
                                    Text(
                                        stringResource(
                                            when (m) {
                                                ScheduleMode.OFF -> R.string.schedule_off
                                                ScheduleMode.DAILY -> R.string.schedule_daily
                                                ScheduleMode.WEEKLY -> R.string.schedule_weekly
                                            }
                                        )
                                    )
                                }
                            )
                        }
                    }
                    if (settings.scheduleMode != ScheduleMode.OFF) {
                        Text(
                            stringResource(R.string.schedule_hour) + ": " +
                                Fa.num(settings.scheduleHour) + ":" + Fa.str("00"),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Slider(
                            value = settings.scheduleHour.toFloat(),
                            onValueChange = {
                                vm.setSchedule(settings.scheduleMode, it.toInt(), settings.autoFormat)
                            },
                            valueRange = 0f..23f,
                            steps = 22
                        )
                    }
                }
            }

            // ── History ──
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.backup_history), fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    "${Fa.num(history.size)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))

            history.forEachIndexed { index, info ->
                HistoryRow(
                    info = info,
                    isLatest = index == 0,
                    onRestore = { vm.pickBackup(info) },
                    onCompare = { vm.compareFor(info) },
                    onDownload = { vm.shareBackup(info) },
                    onDelete = { deleteTarget = info }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // ── Create dialog ──
    if (create.open) {
        CreateBackupDialog(
            ui = create,
            onUpdate = { vm.updateCreate(it) },
            onDismiss = { vm.dismissCreate() },
            onConfirmInternal = { vm.createBackup(null) },
            onConfirmSaf = { safCreateLauncher.launch(vm.suggestedFileName()) }
        )
    }

    // ── Restore flow dialog ──
    restoreStep?.let { step ->
        RestoreDialog(
            step = step,
            history = history,
            vm = vm,
            onOpenFile = { safOpenLauncher.launch(arrayOf("*/*")) },
            onDismiss = { vm.cancelRestore() }
        )
    }

    // ── Compare dialog ──
    compareState?.let { cmp ->
        CompareDialog(ui = cmp, onDismiss = { vm.closeCompare() })
    }

    deleteTarget?.let { info ->
        ConfirmDialog(
            title = stringResource(R.string.delete_backup),
            text = stringResource(R.string.confirm_delete_backup),
            confirmText = stringResource(R.string.action_delete),
            onConfirm = {
                deleteTarget = null
                vm.deleteBackup(info)
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun HistoryRow(
    info: BackupInfo,
    isLatest: Boolean,
    onRestore: () -> Unit,
    onCompare: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).background(Color(0xFFE8F5E9), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (info.isAuto) Icons.Rounded.Schedule else Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        info.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                Text(
                    formatJalali(info.createdAt) + "  •  " + formatSize(info.sizeKb) +
                        "  •  " + Fa.num(info.contactCount) + " " + stringResource(R.string.contacts_count_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isLatest) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                stringResource(R.string.latest_backup),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    if (info.isAuto) {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                stringResource(R.string.auto_backup),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    if (info.encrypted) {
                        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(6.dp)) {
                            Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                                Text(
                                    stringResource(R.string.enc_password),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.restore)) }, onClick = { menu = false; onRestore() }, leadingIcon = { Icon(Icons.Rounded.Restore, null, Modifier.size(18.dp)) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.compare)) }, onClick = { menu = false; onCompare() }, leadingIcon = { Icon(Icons.Rounded.Info, null, Modifier.size(18.dp)) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.download)) }, onClick = { menu = false; onDownload() }, leadingIcon = { Icon(Icons.Rounded.Download, null, Modifier.size(18.dp)) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete_backup), color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun CreateBackupDialog(
    ui: BackupViewModel.CreateUi,
    onUpdate: (f: (BackupViewModel.CreateUi) -> BackupViewModel.CreateUi) -> Unit,
    onDismiss: () -> Unit,
    onConfirmInternal: () -> Unit,
    onConfirmSaf: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_backup)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.backup_format), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BackupFormat.entries.forEach { f ->
                        FilterChip(selected = ui.format == f, onClick = { onUpdate { it.copy(format = f) } }, label = { Text(f.name) })
                    }
                }
                Text(stringResource(R.string.backup_destination), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                RadioRow(
                    selected = ui.destination == BackupDestination.INTERNAL,
                    label = stringResource(R.string.dest_internal),
                    sub = stringResource(R.string.dest_internal_sub),
                    onClick = { onUpdate { it.copy(destination = BackupDestination.INTERNAL) } }
                )
                RadioRow(
                    selected = ui.destination == BackupDestination.SAF,
                    label = stringResource(R.string.dest_saf),
                    sub = stringResource(R.string.dest_saf_sub),
                    onClick = { onUpdate { it.copy(destination = BackupDestination.SAF) } }
                )
                Text(stringResource(R.string.encryption), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        EncryptionType.NONE to stringResource(R.string.enc_none),
                        EncryptionType.DEVICE_KEY to stringResource(R.string.enc_device),
                        EncryptionType.PASSWORD to stringResource(R.string.enc_password)
                    ).forEach { (enc, label) ->
                        FilterChip(
                            selected = ui.encryption == enc,
                            onClick = { onUpdate { it.copy(encryption = enc) } },
                            label = { Text(label) }
                        )
                    }
                }
                if (ui.encryption == EncryptionType.PASSWORD) {
                    OutlinedTextField(
                        value = ui.password,
                        onValueChange = { v -> onUpdate { it.copy(password = v) } },
                        label = { Text(stringResource(R.string.password)) },
                        placeholder = { Text(stringResource(R.string.password_hint)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (ui.encryption == EncryptionType.DEVICE_KEY) {
                    Text(
                        stringResource(R.string.enc_device) + " — Android Keystore",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (ui.destination == BackupDestination.SAF) onConfirmSaf() else onConfirmInternal()
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun RestoreDialog(
    step: BackupViewModel.RestoreStep,
    history: List<BackupInfo>,
    vm: BackupViewModel,
    onOpenFile: () -> Unit,
    onDismiss: () -> Unit
) {
    var picked by remember { mutableStateOf<BackupInfo?>(null) }
    var pw by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restore_title)) },
        text = {
            when (step) {
                is BackupViewModel.RestoreStep.Pick -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.restore_pick), style = MaterialTheme.typography.bodySmall)
                        if (history.isEmpty()) {
                            Text(stringResource(R.string.last_backup_none), style = MaterialTheme.typography.bodySmall)
                        }
                        history.take(6).forEach { info ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = picked == info, onClick = { picked = info })
                                Column(Modifier.weight(1f)) {
                                    Text(info.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                    Text(
                                        formatJalali(info.createdAt) + " • " + Fa.num(info.contactCount),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        OutlinedButton(onClick = onOpenFile, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Folder, contentDescription = null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.restore_from_file))
                        }
                    }
                }
                is BackupViewModel.RestoreStep.Password -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.restore_encrypted))
                        OutlinedTextField(
                            value = pw,
                            onValueChange = { pw = it },
                            label = { Text(stringResource(R.string.password)) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                is BackupViewModel.RestoreStep.Select -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.restore_selected_count, step.selected.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { vm.selectAllRestore(true) }) { Text(stringResource(R.string.action_select_all)) }
                            TextButton(onClick = { vm.selectAllRestore(false) }) { Text(stringResource(R.string.action_cancel)) }
                        }
                        Column(
                            modifier = Modifier.height(240.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            step.parsed.forEachIndexed { i, pc ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = i in step.selected,
                                        onCheckedChange = { vm.toggleRestoreItem(i) }
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(pc.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                        pc.primaryPhone?.let {
                                            Text(
                                                formatPhone(it),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Text(stringResource(R.string.restore_conflict), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        RadioRow(
                            selected = step.conflict == Conflict.SKIP,
                            label = stringResource(R.string.conflict_skip),
                            onClick = { vm.setRestoreConflict(Conflict.SKIP) }
                        )
                        RadioRow(
                            selected = step.conflict == Conflict.DUPLICATE,
                            label = stringResource(R.string.conflict_dup),
                            onClick = { vm.setRestoreConflict(Conflict.DUPLICATE) }
                        )
                        Text(stringResource(R.string.restore_target), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        val targets by vm.sources.collectAsState()
                        Column {
                            targets.forEach { s ->
                                RadioRow(
                                    selected = step.target == s,
                                    label = sourceLabel(s),
                                    onClick = { vm.setRestoreTarget(s) }
                                )
                            }
                        }
                    }
                }
                is BackupViewModel.RestoreStep.Running -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center
                    ) { CircularProgressIndicator() }
                }
                is BackupViewModel.RestoreStep.Done -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            Icons.Rounded.CheckCircle, contentDescription = null,
                            tint = Color(0xFF2E7D32), modifier = Modifier.size(48.dp).align(Alignment.CenterHorizontally)
                        )
                        Text(
                            stringResource(R.string.restore_done, step.result.added, step.result.skipped),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (step) {
                is BackupViewModel.RestoreStep.Pick -> {
                    Button(
                        onClick = { picked?.let { vm.pickBackup(it) } },
                        enabled = picked != null
                    ) { Text(stringResource(R.string.action_ok)) }
                }
                is BackupViewModel.RestoreStep.Password -> {
                    Button(onClick = { vm.submitPassword(pw) }) { Text(stringResource(R.string.action_ok)) }
                }
                is BackupViewModel.RestoreStep.Select -> {
                    Button(onClick = { vm.runRestore() }) { Text(stringResource(R.string.restore)) }
                }
                is BackupViewModel.RestoreStep.Running -> {}
                is BackupViewModel.RestoreStep.Done -> {
                    Button(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
                }
            }
        },
        dismissButton = {
            if (step !is BackupViewModel.RestoreStep.Running && step !is BackupViewModel.RestoreStep.Done) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    )
}

@Composable
private fun CompareDialog(ui: BackupViewModel.CompareUi, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.compare_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.compare_matched, ui.result.matched),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.compare_only_backup, ui.result.onlyInBackup.size),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Column(
                    modifier = Modifier.height(140.dp).verticalScroll(rememberScrollState())
                ) {
                    ui.result.onlyInBackup.forEach {
                        Text("• ${it.displayName}  ${it.primaryPhone?.let { p -> formatPhone(p) } ?: ""}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(
                    stringResource(R.string.compare_only_device, ui.result.onlyInDevice.size),
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold
                )
                Column(
                    modifier = Modifier.height(140.dp).verticalScroll(rememberScrollState())
                ) {
                    ui.result.onlyInDevice.forEach {
                        Text("• ${it.displayName}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}
