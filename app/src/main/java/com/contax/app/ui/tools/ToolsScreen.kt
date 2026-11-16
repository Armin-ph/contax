package com.contax.app.ui.tools

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
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.People
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.contax.app.R
import com.contax.app.data.model.DuplicateGroup
import com.contax.app.ui.common.ConfirmDialog
import com.contax.app.ui.common.ContactAvatar
import com.contax.app.ui.common.SectionHeader
import com.contax.app.ui.common.SourceBadge
import com.contax.app.ui.common.showToast
import com.contax.app.ui.common.sourceLabel
import com.contax.app.util.Fa
import com.contax.app.util.formatPhone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(vm: ToolsViewModel = viewModel()) {
    val context = LocalContext.current
    val stats by vm.stats.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val dupGroups by vm.dupGroups.collectAsState()
    val importing by vm.importing.collectAsState()
    val toastMsg by vm.toast.collectAsState()

    LaunchedEffect(Unit) { vm.refresh() }
    val toastText = toastMsg
    if (toastText != null) {
        LaunchedEffect(toastText) {
            showToast(context, toastText)
            vm.consumeToast()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.importFile(uri) }

    var mergeGroupTarget by remember { mutableStateOf<DuplicateGroup?>(null) }
    var showMergeAll by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            title = { Text(stringResource(R.string.tools_title), fontWeight = FontWeight.Bold) },
            actions = {
                Icon(
                    Icons.Rounded.AutoFixHigh, contentDescription = null,
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

            // ── Stats ──
            SectionHeader(stringResource(R.string.stats_title))
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        StatBadge(
                            modifier = Modifier.weight(1f),
                            icon = { Icon(Icons.Rounded.People, contentDescription = null, tint = Color(0xFF1565C0)) },
                            bg = Color(0xFFE3F2FD),
                            value = Fa.num(stats.total),
                            label = stringResource(R.string.stats_total)
                        )
                        StatBadge(
                            modifier = Modifier.weight(1f),
                            icon = { Icon(Icons.Rounded.Favorite, contentDescription = null, tint = Color(0xFFC2185B)) },
                            bg = Color(0xFFFCE4EC),
                            value = Fa.num(stats.favorites),
                            label = stringResource(R.string.stats_favorites)
                        )
                    }
                    Text(
                        stringResource(R.string.stats_by_source),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val maxCount = stats.bySource.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                    stats.bySource.forEach { (src, count) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                sourceLabel(src),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.width(92.dp),
                                maxLines = 1
                            )
                            LinearProgressIndicator(
                                progress = { count.toFloat() / maxCount },
                                modifier = Modifier.weight(1f).height(8.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Text(
                                Fa.num(count),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ── Duplicates ──
            SectionHeader(stringResource(R.string.duplicates_title))
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { vm.scanDuplicates() }, enabled = !scanning) {
                            if (scanning) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(stringResource(R.string.duplicates_scan))
                        }
                        if (!dupGroups.isNullOrEmpty()) {
                            OutlinedButton(onClick = { showMergeAll = true }) {
                                Icon(Icons.Rounded.CallMerge, contentDescription = null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.merge_all))
                            }
                        }
                    }

                    val groups = dupGroups
                    if (groups != null) {
                        if (groups.isEmpty()) {
                            Text(
                                stringResource(R.string.duplicates_none),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        groups.forEach { group ->
                            DuplicateGroupCard(
                                group = group,
                                onMerge = { mergeGroupTarget = group }
                            )
                        }
                    }
                }
            }

            // ── Import ──
            SectionHeader(stringResource(R.string.import_title))
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { importLauncher.launch(arrayOf("*/*")) }, enabled = !importing) {
                        if (importing) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Rounded.Download, contentDescription = null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(stringResource(R.string.import_pick))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    mergeGroupTarget?.let { g ->
        ConfirmDialog(
            title = stringResource(R.string.merge),
            text = stringResource(R.string.merge_confirm, g.contacts.size),
            confirmText = stringResource(R.string.merge),
            onConfirm = {
                mergeGroupTarget = null
                vm.mergeGroup(g)
            },
            onDismiss = { mergeGroupTarget = null }
        )
    }
    if (showMergeAll) {
        val groups = dupGroups.orEmpty()
        val total = groups.sumOf { it.contacts.size }
        ConfirmDialog(
            title = stringResource(R.string.merge_all),
            text = stringResource(R.string.merge_confirm, total),
            confirmText = stringResource(R.string.merge_all),
            onConfirm = {
                showMergeAll = false
                vm.mergeAll()
            },
            onDismiss = { showMergeAll = false }
        )
    }
}

@Composable
private fun StatBadge(
    icon: @Composable () -> Unit,
    bg: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(40.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) { icon() }
            Column {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DuplicateGroupCard(group: DuplicateGroup, onMerge: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = if (group.reason == DuplicateGroup.Reason.PHONE) Color(0xFFE3F2FD) else Color(0xFFFFF3E0), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        stringResource(if (group.reason == DuplicateGroup.Reason.PHONE) R.string.reason_phone else R.string.reason_name),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onMerge) {
                    Icon(Icons.Rounded.CallMerge, contentDescription = null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.merge))
                }
            }
            group.contacts.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ContactAvatar(c.displayName, c.avatarColor, size = 34.dp)
                    Column(Modifier.weight(1f)) {
                        Text(c.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text(
                            formatPhone(c.primaryPhone ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    SourceBadge(c.source)
                }
            }
        }
    }
}
