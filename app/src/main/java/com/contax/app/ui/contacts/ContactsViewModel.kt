package com.contax.app.ui.contacts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.contax.app.R
import com.contax.app.data.backup.BackupRepository
import com.contax.app.data.contacts.ContactsRepository
import com.contax.app.data.model.BackupDestination
import com.contax.app.data.model.BackupFormat
import com.contax.app.data.model.Contact
import com.contax.app.data.model.ContactSource
import com.contax.app.data.model.EncryptionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ContactsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ContactsRepository(app)
    private val backupRepo = BackupRepository(app)

    data class UiState(
        val loading: Boolean = true,
        val hasPermission: Boolean = false,
        val contacts: List<Contact> = emptyList(),
        val sources: List<ContactSource> = listOf(ContactSource.Device),
        val query: String = "",
        val filter: ContactSource? = null,
        val selectionMode: Boolean = false,
        val selected: Set<Long> = emptySet()
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast

    fun consumeToast() { _toast.value = null }

    private fun toast(res: Int) { _toast.value = getApplication<Application>().getString(res) }

    /** نگاشت کد خطای مخزن به پیام مشخص به‌جای پیام عمومی */
    private fun failMsg(e: Throwable?): Int = when (e?.message) {
        "sim_write_failed" -> R.string.sim_write_unsupported
        else -> R.string.fail_error
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, hasPermission = repo.hasPermission()) }
            if (!repo.hasPermission()) {
                _ui.update { it.copy(loading = false, contacts = emptyList(), sources = listOf(ContactSource.Device)) }
                return@launch
            }
            val list = repo.getAll(force = true)
            val srcs = repo.sources()
            _ui.update {
                it.copy(
                    loading = false,
                    contacts = list,
                    sources = srcs,
                    filter = it.filter?.let { f -> if (srcs.any { s -> s == f }) f else null }
                )
            }
        }
    }

    fun setQuery(q: String) = _ui.update { it.copy(query = q) }
    fun setFilter(s: ContactSource?) = _ui.update { it.copy(filter = s) }

    fun enterSelection(id: Long) = _ui.update { it.copy(selectionMode = true, selected = setOf(id)) }

    fun toggleSelect(id: Long) = _ui.update { s ->
        if (s.selectionMode) {
            s.copy(selected = if (id in s.selected) s.selected - id else s.selected + id)
        } else {
            s.copy(selectionMode = true, selected = setOf(id))
        }
    }

    fun clearSelection() = _ui.update { it.copy(selectionMode = false, selected = emptySet()) }

    fun selectAllVisible(ids: List<Long>) = _ui.update { it.copy(selected = ids.toSet()) }

    fun deleteSelected() {
        val ids = _ui.value.selected.toList()
        viewModelScope.launch {
            repo.delete(ids).fold(
                onSuccess = { toast(R.string.delete_success) },
                onFailure = { e -> toast(failMsg(e)) }
            )
            clearSelection()
            refresh()
        }
    }

    fun transferSelected(target: ContactSource, move: Boolean) {
        val ids = _ui.value.selected.toList()
        viewModelScope.launch {
            val r = repo.copyOrMove(ids, target, move)
            _toast.value = getApplication<Application>().getString(
                if (move) R.string.moved_success else R.string.copied_success, r.ok
            )
            clearSelection()
            refresh()
        }
    }

    fun backupSelected() {
        val ids = _ui.value.selected.toList()
        viewModelScope.launch {
            val r = backupRepo.create(
                BackupRepository.NewBackup(
                    format = BackupFormat.VCF,
                    destination = BackupDestination.INTERNAL,
                    encryption = EncryptionType.NONE,
                    contactIds = ids
                )
            )
            toast(if (r.isSuccess) R.string.backup_done else R.string.backup_failed)
            clearSelection()
        }
    }

    fun toggleFavorite(c: Contact) {
        viewModelScope.launch {
            repo.setFavorite(c.id, !c.isFavorite)
            refresh()
        }
    }

    fun deleteOne(c: Contact) {
        viewModelScope.launch {
            repo.delete(listOf(c.id)).fold(
                onSuccess = { toast(R.string.delete_success) },
                onFailure = { e -> toast(failMsg(e)) }
            )
            refresh()
        }
    }
}
