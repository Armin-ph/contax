package com.contax.app.ui.contacts

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.MoveUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.contax.app.R
import com.contax.app.data.model.Contact
import com.contax.app.data.model.ContactSource
import com.contax.app.ui.common.ConfirmDialog
import com.contax.app.ui.common.ContactAvatar
import com.contax.app.ui.common.EmptyState
import com.contax.app.ui.common.RadioRow
import com.contax.app.ui.common.SourceBadge
import com.contax.app.ui.common.showToast
import com.contax.app.ui.common.sourceLabel
import com.contax.app.util.Fa
import com.contax.app.util.PhoneNormalizer
import com.contax.app.util.formatPhone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ContactsScreen(
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    vm: ContactsViewModel = viewModel()
) {
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current

    val toastMsg = vm.toast.collectAsState().value
    if (toastMsg != null) {
        LaunchedEffect(toastMsg) {
            showToast(context, toastMsg)
            vm.consumeToast()
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.refresh() }

    LaunchedEffect(Unit) { vm.refresh() }

    if (!ui.hasPermission && !ui.loading) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Rounded.Shield, contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.perm_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.perm_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            TextButton(
                onClick = {
                    permLauncher.launch(
                        arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
                    )
                }
            ) { Text(stringResource(R.string.perm_grant), fontWeight = FontWeight.Bold) }
        }
        return
    }

    var showSearch by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var moveDialog by remember { mutableStateOf(false) }
    var copyDialog by remember { mutableStateOf(false) }

    val filtered = remember(ui.contacts, ui.query, ui.filter) {
        val q = ui.query.trim()
        val qNorm = PhoneNormalizer.normalize(q)
        ui.contacts.filter { c ->
            val srcOk = ui.filter == null || c.source == ui.filter
            val qOk = q.isBlank() ||
                c.displayName.contains(q, ignoreCase = true) ||
                c.phones.any { it.number.contains(q) || (qNorm.isNotBlank() && PhoneNormalizer.normalize(it.number).contains(qNorm)) } ||
                c.emails.any { it.email.contains(q, ignoreCase = true) } ||
                (c.organization?.contains(q, ignoreCase = true) == true)
            srcOk && qOk
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    if (ui.selectionMode) {
                        Text(Fa.str(stringResource(R.string.selected_count, ui.selected.size)), fontWeight = FontWeight.Bold)
                    } else {
                        Text(stringResource(R.string.contacts_title), fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    if (ui.selectionMode) {
                        IconButton(onClick = { vm.selectAllVisible(filtered.map { it.id }) }) {
                            Icon(Icons.Rounded.SelectAll, contentDescription = stringResource(R.string.action_select_all))
                        }
                        IconButton(onClick = { vm.clearSelection() }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_cancel))
                        }
                    } else {
                        IconButton(onClick = { showSearch = !showSearch }) {
                            Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.search_hint))
                        }
                        IconButton(onClick = { vm.refresh() }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.action_refresh))
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (ui.selectionMode) {
                Surface(shadowElevation = 12.dp, color = MaterialTheme.colorScheme.surface) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(onClick = { moveDialog = true }) {
                            Icon(Icons.Rounded.MoveUp, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_move))
                        }
                        TextButton(onClick = { copyDialog = true }) {
                            Text(stringResource(R.string.action_copy))
                        }
                        TextButton(onClick = { vm.backupSelected() }) {
                            Text(stringResource(R.string.action_backup))
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!ui.selectionMode) {
                FloatingActionButton(onClick = onAdd, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.new_contact))
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (showSearch && !ui.selectionMode) {
                OutlinedTextField(
                    value = ui.query,
                    onValueChange = { vm.setQuery(it) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
            }

            // Source filter chips
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = ui.filter == null,
                        onClick = { vm.setFilter(null) },
                        label = { Text(stringResource(R.string.filter_all)) },
                        leadingIcon = { Icon(Icons.Rounded.FilterList, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
                items(ui.sources) { src ->
                    FilterChip(
                        selected = ui.filter == src,
                        onClick = { vm.setFilter(if (ui.filter == src) null else src) },
                        label = { Text(sourceLabel(src)) }
                    )
                }
            }

            Row(
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    Fa.num(filtered.size) + " " + stringResource(R.string.contacts_count_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (ui.loading) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) { CircularProgressIndicator() }
            } else if (filtered.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.empty_contacts),
                    subtitle = stringResource(R.string.empty_contacts_sub)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { contact ->
                        ContactCard(
                            contact = contact,
                            selectionMode = ui.selectionMode,
                            selected = ui.selected.contains(contact.id),
                            onClick = {
                                if (ui.selectionMode) vm.toggleSelect(contact.id) else onOpen(contact.id)
                            },
                            onLongClick = { vm.enterSelection(contact.id) }
                        )
                    }
                    item { Spacer(Modifier.height(64.dp)) }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.confirm_delete_title),
            text = stringResource(R.string.confirm_delete_text, ui.selected.size),
            confirmText = stringResource(R.string.action_delete),
            onConfirm = {
                showDeleteConfirm = false
                vm.deleteSelected()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
    if (moveDialog) {
        TargetPickerDialog(
            title = stringResource(R.string.move_to),
            sources = ui.sources,
            onPick = { src ->
                moveDialog = false
                vm.transferSelected(src, move = true)
            },
            onDismiss = { moveDialog = false }
        )
    }
    if (copyDialog) {
        TargetPickerDialog(
            title = stringResource(R.string.copy_to),
            sources = ui.sources,
            onPick = { src ->
                copyDialog = false
                vm.transferSelected(src, move = false)
            },
            onDismiss = { copyDialog = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactCard(
    contact: Contact,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onClick() })
                Spacer(Modifier.width(4.dp))
            }
            ContactAvatar(contact.displayName, contact.avatarColor, size = 46.dp, photoBytes = contact.photoBytes)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    contact.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                contact.primaryPhone?.let {
                    Text(
                        formatPhone(it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SourceBadge(contact.source)
                if (contact.phones.size > 1) {
                    Text(
                        "+" + Fa.num(contact.phones.size - 1),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (contact.isFavorite) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Rounded.Star,
                    contentDescription = stringResource(R.string.favorite),
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun TargetPickerDialog(
    title: String,
    sources: List<ContactSource>,
    onPick: (ContactSource) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                sources.forEach { src ->
                    RadioRow(
                        selected = false,
                        label = sourceLabel(src),
                        onClick = { onPick(src) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
