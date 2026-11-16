package com.contax.app.ui.contacts

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.contax.app.R
import com.contax.app.data.contacts.ContactsRepository
import com.contax.app.data.model.Contact
import com.contax.app.data.model.ContactSource
import com.contax.app.data.model.EmailEntry
import com.contax.app.data.model.EmailLabel
import com.contax.app.data.model.ParsedContact
import com.contax.app.data.model.PhoneEntry
import com.contax.app.data.model.PhoneLabel
import com.contax.app.ui.common.ContactAvatar
import com.contax.app.ui.common.SectionHeader
import com.contax.app.ui.common.showToast
import com.contax.app.ui.common.sourceLabel
import kotlinx.coroutines.launch

private data class PhoneRow(var number: String, var label: PhoneLabel)
private data class EmailRow(var email: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactEditScreen(
    contactId: Long?,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { ContactsRepository(context) }
    val scope = rememberCoroutineScope()

    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    val phones = remember { mutableStateListOf(PhoneRow("", PhoneLabel.MOBILE)) }
    val emails = remember { mutableStateListOf<EmailRow>() }
    var organization by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var photoBytes by remember { mutableStateOf<ByteArray?>(null) }
    var original by remember { mutableStateOf<Contact?>(null) }
    var selectedSource by remember { mutableStateOf<ContactSource>(ContactSource.Device) }
    var sources by remember { mutableStateOf<List<ContactSource>>(listOf(ContactSource.Device)) }

    LaunchedEffect(contactId) {
        sources = repo.sources()
        if (contactId != null) {
            val c = repo.getById(contactId)
            if (c != null) {
                original = c
                firstName = c.firstName
                lastName = c.lastName
                phones.clear()
                c.phones.forEach { phones.add(PhoneRow(it.number, it.label)) }
                if (phones.isEmpty()) phones.add(PhoneRow("", PhoneLabel.MOBILE))
                emails.clear()
                c.emails.forEach { emails.add(EmailRow(it.email)) }
                organization = c.organization ?: ""
                address = c.address ?: ""
                note = c.note ?: ""
                photoBytes = c.photoBytes
            }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                photoBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (t: Throwable) {
                photoBytes = null
            }
        }
    }

    val editingSim = original?.source is ContactSource.Sim
    val isSim = if (contactId == null) selectedSource is ContactSource.Sim else editingSim

    fun save() {
        val f = firstName.trim()
        val l = lastName.trim()
        val display = listOf(f, l).filter { it.isNotBlank() }.joinToString(" ")
        val phoneEntries = phones.mapNotNull { pr ->
            val n = pr.number.trim()
            if (n.isNotBlank()) PhoneEntry(number = n, label = pr.label) else null
        }
        if (display.isBlank()) {
            showToast(context, context.getString(R.string.name_required))
            return
        }
        if (phoneEntries.isEmpty()) {
            showToast(context, context.getString(R.string.phone_required))
            return
        }
        val pc = ParsedContact(
            firstName = f, lastName = l, displayName = display,
            phones = if (isSim) phoneEntries.take(1) else phoneEntries,
            emails = if (isSim) emptyList() else emails.mapNotNull { er ->
                val e = er.email.trim()
                if (e.isNotBlank()) EmailEntry(email = e, label = EmailLabel.HOME) else null
            },
            address = if (isSim) null else address.trim().takeIf { it.isNotBlank() },
            note = if (isSim) null else note.trim().takeIf { it.isNotBlank() },
            organization = if (isSim) null else organization.trim().takeIf { it.isNotBlank() },
            photoBytes = if (isSim) null else photoBytes
        )
        scope.launch {
            val result = when {
                contactId != null && original?.source is ContactSource.Sim -> {
                    val oc = original!!
                    repo.updateSim(oc, display, phoneEntries.first().number).map { }
                }
                contactId != null -> repo.update(contactId, pc)
                else -> repo.insert(pc, selectedSource).map { }
            }
            result.fold(
                onSuccess = {
                    showToast(context, context.getString(R.string.saved_success))
                    onDone()
                },
                onFailure = {
                    val msg = if (it.message == "sim_write_failed" || it.message == "sim_unavailable") {
                        context.getString(R.string.sim_write_unsupported)
                    } else {
                        context.getString(R.string.fail_error)
                    }
                    showToast(context, msg)
                }
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            title = {
                Text(
                    stringResource(if (contactId == null) R.string.new_contact else R.string.edit_contact),
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onDone) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_cancel))
                }
            },
            actions = {
                IconButton(onClick = { save() }) {
                    Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.action_save))
                }
            }
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))

            // Avatar / photo
            Row(verticalAlignment = Alignment.CenterVertically) {
                ContactAvatar(
                    displayName = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "?" },
                    colorIndex = (firstName.length + lastName.length) % 6,
                    size = 72.dp,
                    photoBytes = if (isSim) null else photoBytes
                )
                Spacer(Modifier.width(14.dp))
                if (!isSim) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { photoPicker.launch("image/*") }) {
                            Text(stringResource(R.string.change_photo))
                        }
                        if (photoBytes != null) {
                            OutlinedButton(onClick = { photoBytes = null }) {
                                Text(stringResource(R.string.remove_photo))
                            }
                        }
                    }
                }
            }

            // Name
            SectionHeader(stringResource(R.string.field_first_name))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.field_first_name)) }
                )
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.field_last_name)) }
                )
            }

            // Phones
            SectionHeader(stringResource(R.string.field_phone))
            phones.forEachIndexed { index, row ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = row.number,
                        onValueChange = { phones[index] = row.copy(number = it) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.field_phone)) }
                    )
                    Box {
                        var open by remember { mutableStateOf(false) }
                        OutlinedButton(onClick = { open = true }) {
                            Text(
                                stringResource(
                                    when (row.label) {
                                        PhoneLabel.MOBILE -> R.string.label_mobile
                                        PhoneLabel.HOME -> R.string.label_home
                                        PhoneLabel.WORK -> R.string.label_work
                                        else -> R.string.label_other
                                    }
                                )
                            )
                            Icon(Icons.Rounded.ExpandMore, contentDescription = null, Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            PhoneLabel.entries.forEach { lbl ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                when (lbl) {
                                                    PhoneLabel.MOBILE -> R.string.label_mobile
                                                    PhoneLabel.HOME -> R.string.label_home
                                                    PhoneLabel.WORK -> R.string.label_work
                                                    else -> R.string.label_other
                                                }
                                            )
                                        )
                                    },
                                    onClick = {
                                        phones[index] = row.copy(label = lbl)
                                        open = false
                                    }
                                )
                            }
                        }
                    }
                    if (phones.size > 1) {
                        IconButton(onClick = { phones.removeAt(index) }) {
                            Icon(Icons.Rounded.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            if (!isSim) {
                OutlinedButton(onClick = { phones.add(PhoneRow("", PhoneLabel.MOBILE)) }) {
                    Icon(Icons.Rounded.Add, contentDescription = null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.add_phone))
                }
            }

            if (!isSim) {
                // Emails
                SectionHeader(stringResource(R.string.field_email))
                emails.forEachIndexed { index, row ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = row.email,
                            onValueChange = { emails[index] = EmailRow(it) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.field_email)) }
                        )
                        IconButton(onClick = { emails.removeAt(index) }) {
                            Icon(Icons.Rounded.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                OutlinedButton(onClick = { emails.add(EmailRow("")) }) {
                    Icon(Icons.Rounded.Add, contentDescription = null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.add_email))
                }

                SectionHeader(stringResource(R.string.field_org))
                OutlinedTextField(
                    value = organization,
                    onValueChange = { organization = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                SectionHeader(stringResource(R.string.field_address))
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                SectionHeader(stringResource(R.string.field_note))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            } else {
                Spacer(Modifier.height(12.dp))
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.sim_limit_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Save-to source picker (new contacts only)
            if (contactId == null) {
                SectionHeader(stringResource(R.string.save_to))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sources.forEach { src ->
                        FilterChip(
                            selected = selectedSource == src,
                            onClick = { selectedSource = src },
                            label = { Text(sourceLabel(src)) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { save() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
