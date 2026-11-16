package com.contax.app.ui.backup

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.contax.app.R
import com.contax.app.data.backup.BackupCrypto
import com.contax.app.data.backup.BackupRepository
import com.contax.app.data.backup.Conflict
import com.contax.app.data.contacts.ContactsRepository
import com.contax.app.data.model.BackupDestination
import com.contax.app.data.model.BackupFormat
import com.contax.app.data.model.BackupInfo
import com.contax.app.data.model.CompareResult
import com.contax.app.data.model.ContactSource
import com.contax.app.data.model.EncryptionType
import com.contax.app.data.model.ParsedContact
import com.contax.app.data.model.RestoreResult
import com.contax.app.data.model.ScheduleMode
import com.contax.app.data.model.SettingsData
import com.contax.app.data.prefs.SettingsDataStore
import com.contax.app.data.work.BackupScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted as SS
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BackupViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = BackupRepository(app)
    private val contactsRepo = ContactsRepository(app)
    private val prefs = SettingsDataStore(app)

    val history = MutableStateFlow<List<BackupInfo>>(emptyList())
    val settings: StateFlow<SettingsData> = prefs.settings
        .stateIn(viewModelScope, SS.Eagerly, SettingsData())
    val contactCount = MutableStateFlow(0)
    val sources = MutableStateFlow<List<ContactSource>>(listOf(ContactSource.Device))

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast
    fun consumeToast() { _toast.value = null }
    private fun toast(res: Int) { _toast.value = getApplication<Application>().getString(res) }
    private fun toastRaw(s: String) { _toast.value = s }
    private fun str(res: Int) = getApplication<Application>().getString(res)

    fun refresh() {
        viewModelScope.launch {
            history.value = repo.history()
            contactCount.value = contactsRepo.getAll().size
            sources.value = contactsRepo.sources()
        }
    }

    // ─────────────── create backup ───────────────

    data class CreateUi(
        val open: Boolean = false,
        val format: BackupFormat = BackupFormat.VCF,
        val destination: BackupDestination = BackupDestination.INTERNAL,
        val encryption: EncryptionType = EncryptionType.NONE,
        val password: String = ""
    )

    val create = MutableStateFlow(CreateUi())

    fun openCreate() { create.value = CreateUi(open = true) }
    fun dismissCreate() { create.value = create.value.copy(open = false, password = "") }
    fun updateCreate(f: (CreateUi) -> CreateUi) { create.value = f(create.value) }

    fun suggestedFileName(): String {
        val c = create.value
        val ts = System.currentTimeMillis()
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date(ts))
        val ext = c.format.ext + if (c.encryption != EncryptionType.NONE) ".enc" else ""
        return "contax-$stamp.$ext"
    }

    fun createBackup(safUri: Uri?) {
        val c = create.value
        if (c.encryption == EncryptionType.PASSWORD && c.password.trim().length < 4) {
            toastRaw(str(R.string.password_hint))
            return
        }
        viewModelScope.launch {
            val r = repo.create(
                BackupRepository.NewBackup(
                    format = c.format,
                    destination = c.destination,
                    encryption = c.encryption,
                    password = c.password.trim().ifBlank { null },
                    safUri = safUri
                )
            )
            r.fold(
                onSuccess = {
                    toast(R.string.backup_done)
                    refresh()
                    dismissCreate()
                },
                onFailure = {
                    when (it.message) {
                        "no_contacts" -> toast(R.string.no_contacts_to_backup)
                        "storage_unavailable", "no_uri" -> toast(R.string.storage_unavailable)
                        else -> toast(R.string.backup_failed)
                    }
                }
            )
        }
    }

    // ─────────────── restore flow ───────────────

    sealed interface RestoreStep {
        data object Pick : RestoreStep
        data class Password(val info: BackupInfo) : RestoreStep
        data class Select(
            val info: BackupInfo,
            val parsed: List<ParsedContact>,
            val selected: Set<Int>,
            val conflict: Conflict,
            val target: ContactSource
        ) : RestoreStep
        data object Running : RestoreStep
        data class Done(val result: RestoreResult) : RestoreStep
    }

    val restoreStep = MutableStateFlow<RestoreStep?>(null)
    private val pendingFileBytes = MutableStateFlow<ByteArray?>(null)
    private val pendingCompareInfo = MutableStateFlow<BackupInfo?>(null)
    private val comparePasswordMode = MutableStateFlow(false)

    fun startRestore() {
        refresh()
        restoreStep.value = RestoreStep.Pick
    }

    fun cancelRestore() {
        restoreStep.value = null
        pendingFileBytes.value = null
        pendingCompareInfo.value = null
        comparePasswordMode.value = false
    }

    fun pickBackup(info: BackupInfo) {
        if (info.encrypted) {
            restoreStep.value = RestoreStep.Password(info)
        } else {
            loadPreview(info, null)
        }
    }

    fun restoreFile(uri: Uri) {
        viewModelScope.launch {
            val bytes = try {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (t: Throwable) { null }
            if (bytes == null) {
                toast(R.string.import_failed)
                return@launch
            }
            val header = if (bytes.size >= 6) String(bytes, 0, 6, Charsets.UTF_8) else ""
            val enc = when {
                header.startsWith(BackupCrypto.MAGIC + "K") -> EncryptionType.DEVICE_KEY
                header.startsWith(BackupCrypto.MAGIC) -> EncryptionType.PASSWORD
                else -> EncryptionType.NONE
            }
            val name = uri.lastPathSegment ?: "backup-file"
            if (enc == EncryptionType.NONE) {
                val format = com.contax.app.data.backup.ContactCodec.detectFormat(String(bytes, Charsets.UTF_8))
                if (format == null) {
                    toast(R.string.import_failed)
                    return@launch
                }
                val info = BackupInfo(-1, name, null, uri.toString(), format, BackupDestination.SAF, EncryptionType.NONE, 0.0, 0, false, System.currentTimeMillis())
                loadPreview(info, null)
            } else {
                val info = BackupInfo(-1, name, null, uri.toString(), BackupFormat.VCF, BackupDestination.SAF, enc, 0.0, 0, false, System.currentTimeMillis())
                restoreStep.value = RestoreStep.Password(info)
            }
        }
    }

    private fun loadPreview(info: BackupInfo, password: String?) {
        viewModelScope.launch {
            val r = repo.preview(info, password)
            r.fold(
                onSuccess = { parsed ->
                    restoreStep.value = RestoreStep.Select(
                        info = info,
                        parsed = parsed,
                        selected = (0 until parsed.size).toSet(),
                        conflict = Conflict.SKIP,
                        target = ContactSource.Device
                    )
                },
                onFailure = {
                    when (it.message) {
                        "bad_password" -> toast(R.string.wrong_password)
                        else -> toast(R.string.import_failed)
                    }
                }
            )
        }
    }

    fun submitPassword(pw: String) {
        val step = restoreStep.value as? RestoreStep.Password ?: return
        lastPassword = pw
        viewModelScope.launch {
            if (comparePasswordMode.value) {
                comparePasswordMode.value = false
                restoreStep.value = null
                pendingCompareInfo.value?.let { runCompare(it, pw) }
                pendingCompareInfo.value = null
                return@launch
            }
            val info = step.info
            if (info.id == -1L) {
                val bytes = pendingFileBytes.value
                if (bytes == null) {
                    toast(R.string.import_failed)
                    return@launch
                }
                val text = when (info.encryption) {
                    EncryptionType.PASSWORD -> BackupCrypto.decryptPassword(bytes, pw)
                    else -> BackupCrypto.decryptDeviceKey(bytes)
                }
                if (text == null) {
                    toast(R.string.wrong_password)
                    return@launch
                }
                val format = com.contax.app.data.backup.ContactCodec.detectFormat(text) ?: BackupFormat.VCF
                pendingFileBytes.value = null
                loadPreview(info.copy(format = format), pw)
            } else {
                loadPreview(info, pw)
            }
        }
    }

    fun toggleRestoreItem(index: Int) {
        val step = restoreStep.value as? RestoreStep.Select ?: return
        val next = if (index in step.selected) step.selected - index else step.selected + index
        restoreStep.value = step.copy(selected = next)
    }

    fun selectAllRestore(all: Boolean) {
        val step = restoreStep.value as? RestoreStep.Select ?: return
        restoreStep.value = step.copy(
            selected = if (all) (0 until step.parsed.size).toSet() else emptySet()
        )
    }

    fun setRestoreConflict(c: Conflict) {
        val step = restoreStep.value as? RestoreStep.Select ?: return
        restoreStep.value = step.copy(conflict = c)
    }

    fun setRestoreTarget(s: ContactSource) {
        val step = restoreStep.value as? RestoreStep.Select ?: return
        restoreStep.value = step.copy(target = s)
    }

    fun runRestore() {
        val step = restoreStep.value as? RestoreStep.Select ?: return
        restoreStep.value = RestoreStep.Running
        viewModelScope.launch {
            val allSelected = step.selected.size == step.parsed.size
            val sel = if (allSelected) null else step.selected.toList()
            val password = if (step.info.encryption == EncryptionType.PASSWORD) lastPassword else null
            val r = repo.restore(step.info, password, sel, step.conflict, step.target)
            r.fold(
                onSuccess = { restoreStep.value = RestoreStep.Done(it) },
                onFailure = {
                    restoreStep.value = null
                    when (it.message) {
                        "bad_password" -> toast(R.string.wrong_password)
                        else -> toast(R.string.fail_error)
                    }
                }
            )
        }
    }

    private var lastPassword: String? = null

    fun notePassword(pw: String) { lastPassword = pw }

    // ─────────────── compare ───────────────

    data class CompareUi(val info: BackupInfo, val result: CompareResult)

    val compareState = MutableStateFlow<CompareUi?>(null)

    fun compareFor(info: BackupInfo) {
        viewModelScope.launch {
            if (info.encrypted) {
                pendingCompareInfo.value = info
                comparePasswordMode.value = true
                restoreStep.value = RestoreStep.Password(info)
            } else {
                runCompare(info, null)
            }
        }
    }

    private suspend fun runCompare(info: BackupInfo, password: String?) {
        repo.compare(info, password).fold(
            onSuccess = { compareState.value = CompareUi(info, it) },
            onFailure = {
                when (it.message) {
                    "bad_password" -> toast(R.string.wrong_password)
                    else -> toast(R.string.fail_error)
                }
            }
        )
    }

    fun closeCompare() { compareState.value = null }

    // ─────────────── misc ───────────────

    fun deleteBackup(info: BackupInfo) {
        viewModelScope.launch {
            repo.deleteBackup(info)
            refresh()
        }
    }

    fun shareBackup(info: BackupInfo) {
        val uri = repo.shareUri(info) ?: run {
            toast(R.string.storage_unavailable)
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = info.format.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            getApplication<Application>().startActivity(
                Intent.createChooser(intent, info.name)
            )
        } catch (t: Throwable) {
            toast(R.string.fail_error)
        }
    }

    fun setSchedule(mode: ScheduleMode, hour: Int, format: BackupFormat) {
        viewModelScope.launch {
            prefs.setSchedule(mode, hour, format)
            BackupScheduler.apply(getApplication(), mode, hour)
        }
    }
}
