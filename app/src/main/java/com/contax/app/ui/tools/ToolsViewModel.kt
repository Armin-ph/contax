package com.contax.app.ui.tools

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.contax.app.R
import com.contax.app.data.backup.ContactCodec
import com.contax.app.data.contacts.ContactsRepository
import com.contax.app.data.model.Contact
import com.contax.app.data.model.ContactSource
import com.contax.app.data.model.DuplicateGroup
import com.contax.app.data.model.ParsedContact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ToolsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ContactsRepository(app)

    data class Stats(
        val total: Int = 0,
        val favorites: Int = 0,
        val bySource: List<Pair<ContactSource, Int>> = emptyList()
    )

    val stats = MutableStateFlow(Stats())
    val scanning = MutableStateFlow(false)
    val dupGroups = MutableStateFlow<List<DuplicateGroup>?>(null)
    val importing = MutableStateFlow(false)

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast
    fun consumeToast() { _toast.value = null }
    private fun toast(res: Int) { _toast.value = getApplication<Application>().getString(res) }
    private fun toastRaw(s: String) { _toast.value = s }

    /** نگاشت کد خطای مخزن به پیام مشخص به‌جای پیام عمومی */
    private fun failMsg(e: Throwable?): Int = when (e?.message) {
        "sim_write_failed" -> R.string.sim_write_unsupported
        "merge_partial" -> R.string.merge_partial
        else -> R.string.fail_error
    }

    fun refresh() {
        viewModelScope.launch {
            val all = repo.getAll()
            val bySource = repo.sources().map { src -> src to all.count { it.source == src } }
            stats.value = Stats(total = all.size, favorites = all.count { it.isFavorite }, bySource = bySource)
        }
    }

    fun scanDuplicates() {
        viewModelScope.launch {
            scanning.value = true
            val groups = repo.findDuplicates()
            dupGroups.value = groups
            scanning.value = false
            if (groups.isEmpty()) toast(R.string.duplicates_none)
            else toastRaw(getApplication<Application>().getString(R.string.duplicates_found, groups.size))
        }
    }

    fun mergeGroup(group: DuplicateGroup) {
        viewModelScope.launch {
            val ids = group.contacts.map { it.id }
            repo.merge(ids).fold(
                onSuccess = {
                    toast(R.string.merge_done)
                    rescan()
                },
                onFailure = { e -> toast(failMsg(e)) }
            )
        }
    }

    fun mergeAll() {
        val groups = dupGroups.value ?: return
        viewModelScope.launch {
            var ok = 0
            var lastFailure: Throwable? = null
            for (g in groups) {
                val r = repo.merge(g.contacts.map { it.id })
                if (r.isSuccess) ok++ else lastFailure = r.exceptionOrNull()
            }
            if (ok == 0) {
                // نشان‌دادن دلیل واقعی شکست (سیم‌کارت/ناقص) به‌جای پیام عمومی
                toast(failMsg(lastFailure))
            } else {
                toastRaw(
                    getApplication<Application>().getString(R.string.merge_done) +
                        " ($ok/${groups.size})"
                )
            }
            rescan()
        }
    }

    private suspend fun rescan() {
        refresh()
        val groups = repo.findDuplicates()
        dupGroups.value = groups.ifEmpty { null }
    }

    fun importFile(uri: Uri) {
        viewModelScope.launch {
            importing.value = true
            try {
                val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null) {
                    toast(R.string.import_failed)
                    return@launch
                }
                val header = if (bytes.size >= 6) String(bytes, 0, 6, Charsets.UTF_8) else ""
                val text: String? = if (header.startsWith(com.contax.app.data.backup.BackupCrypto.MAGIC)) {
                    null // encrypted backups must be restored from the Backup tab
                } else String(bytes, Charsets.UTF_8)
                if (text == null) {
                    toast(R.string.import_failed)
                    return@launch
                }
                val format = ContactCodec.detectFormat(text)
                if (format == null) {
                    toast(R.string.import_failed)
                    return@launch
                }
                val parsed = ContactCodec.decode(text, format)
                var added = 0
                for (pc in parsed) {
                    val r = repo.insert(pc, ContactSource.Device)
                    if (r.isSuccess) added++
                }
                toastRaw(getApplication<Application>().getString(R.string.import_done, added))
                refresh()
            } catch (t: Throwable) {
                toast(R.string.import_failed)
            } finally {
                importing.value = false
            }
        }
    }
}
