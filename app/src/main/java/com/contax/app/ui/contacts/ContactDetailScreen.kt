package com.contax.app.ui.contacts

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MoveUp
import androidx.compose.material.icons.rounded.Note
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.contax.app.R
import com.contax.app.data.contacts.ContactsRepository
import com.contax.app.data.model.Contact
import com.contax.app.data.model.ContactSource
import com.contax.app.data.model.EmailLabel
import com.contax.app.data.model.PhoneLabel
import com.contax.app.ui.common.ConfirmDialog
import com.contax.app.ui.common.ContactAvatar
import com.contax.app.ui.common.SourceBadge
import com.contax.app.ui.common.showToast
import com.contax.app.util.formatPhone
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    contactId: Long,
    onEdit: (Long) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { ContactsRepository(context) }
    val scope = rememberCoroutineScope()

    var contact by remember { mutableStateOf<Contact?>(null) }
    var sources by remember { mutableStateOf<List<ContactSource>>(listOf(ContactSource.Device)) }

    fun refresh() {
        scope.launch {
            contact = repo.getById(contactId)
            sources = repo.sources()
        }
    }
    LaunchedEffect(contactId) { refresh() }

    var showDelete by remember { mutableStateOf(false) }
    var moveDialog by remember { mutableStateOf(false) }
    var copyDialog by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }

    val c = contact
    if (c == null) {
        // contact not loaded yet — keep smart-cast valid for the whole screen
        Box(Modifier.fillMaxSize()) { }
        return
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            title = { Text(stringResource(R.string.contacts_title), fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_cancel))
                }
            },
            actions = {
                IconButton(onClick = { showDelete = true }) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
                }
            }
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ContactAvatar(c.displayName, c.avatarColor, size = 84.dp, photoBytes = c.photoBytes)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(c.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    SourceBadge(c.source)
                }
                IconButton(onClick = {
                    scope.launch {
                        repo.setFavorite(c.id, !c.isFavorite)
                        refresh()
                    }
                }) {
                    Icon(
                        Icons.Rounded.Star,
                        contentDescription = stringResource(R.string.favorite),
                        tint = if (c.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalIconButton(onClick = { c.primaryPhone?.let { p ->
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + p)))
                } }) {
                    Icon(Icons.Rounded.Call, contentDescription = stringResource(R.string.call))
                }
                FilledTonalIconButton(onClick = { c.primaryPhone?.let { p ->
                    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + p)))
                } }) {
                    Icon(Icons.Rounded.Sms, contentDescription = stringResource(R.string.sms))
                }
                FilledTonalIconButton(onClick = { onEdit(c.id) }) {
                    Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.edit))
                }
                Box {
                    FilledTonalIconButton(onClick = { moreMenu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more))
                    }
                    DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.copy_to)) },
                            onClick = { moreMenu = false; copyDialog = true },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.move_to)) },
                            onClick = { moreMenu = false; moveDialog = true },
                            leadingIcon = { Icon(Icons.Rounded.MoveUp, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            InfoCard(title = stringResource(R.string.field_phone)) {
                c.phones.forEach { p ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(formatPhone(p.number), fontWeight = FontWeight.Medium)
                            Text(
                                stringResource(
                                    when (p.label) {
                                        PhoneLabel.MOBILE -> R.string.label_mobile
                                        PhoneLabel.HOME -> R.string.label_home
                                        PhoneLabel.WORK -> R.string.label_work
                                        else -> R.string.label_other
                                    }
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + p.number)))
                        }) {
                            Icon(Icons.Rounded.Call, contentDescription = stringResource(R.string.call), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            if (c.emails.isNotEmpty()) {
                InfoCard(title = stringResource(R.string.field_email)) {
                    c.emails.forEach { e ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Email, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.email)
                                Text(
                                    stringResource(
                                        when (e.label) {
                                            EmailLabel.HOME -> R.string.label_home
                                            EmailLabel.WORK -> R.string.label_work
                                            else -> R.string.label_other
                                        }
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            c.organization?.let {
                InfoCard(title = stringResource(R.string.field_org)) { InfoRow(Icons.Rounded.Business, it) }
            }
            c.address?.let {
                InfoCard(title = stringResource(R.string.field_address)) { InfoRow(Icons.Rounded.Home, it) }
            }
            c.note?.let {
                InfoCard(title = stringResource(R.string.field_note)) { InfoRow(Icons.Rounded.Note, it) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDelete) {
        ConfirmDialog(
            title = stringResource(R.string.confirm_delete_title),
            text = stringResource(R.string.confirm_delete_one),
            confirmText = stringResource(R.string.action_delete),
            onConfirm = {
                showDelete = false
                scope.launch {
                    val res = repo.delete(listOf(contactId))
                    res.fold(
                        onSuccess = {
                            showToast(context, context.getString(R.string.delete_success))
                            onBack()
                        },
                        onFailure = { e ->
                            // پیام مشخص به‌جای پیام موفقیت دروغین
                            val msgRes = if (e.message == "sim_write_failed")
                                R.string.sim_write_unsupported else R.string.fail_error
                            showToast(context, context.getString(msgRes))
                        }
                    )
                }
            },
            onDismiss = { showDelete = false }
        )
    }
    if (copyDialog) {
        TargetPickerDialog(
            title = stringResource(R.string.copy_to),
            sources = sources.filter { it != c.source },
            onPick = { src ->
                copyDialog = false
                scope.launch {
                    val r = repo.copyOrMove(listOf(contactId), src, move = false)
                    if (r.ok == 0) showToast(context, context.getString(R.string.fail_error))
                    else showToast(context, context.getString(R.string.copied_success, r.ok))
                }
            },
            onDismiss = { copyDialog = false }
        )
    }
    if (moveDialog) {
        TargetPickerDialog(
            title = stringResource(R.string.move_to),
            sources = sources.filter { it != c.source },
            onPick = { src ->
                moveDialog = false
                scope.launch {
                    val r = repo.copyOrMove(listOf(contactId), src, move = true)
                    if (r.ok == 0) showToast(context, context.getString(R.string.fail_error))
                    else showToast(context, context.getString(R.string.moved_success, r.ok))
                    refresh()
                }
            },
            onDismiss = { moveDialog = false }
        )
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
