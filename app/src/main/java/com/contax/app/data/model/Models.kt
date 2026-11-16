package com.contax.app.data.model

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone

// ─── Contact models ───

enum class PhoneLabel(val typeInt: Int) {
    MOBILE(Phone.TYPE_MOBILE), HOME(Phone.TYPE_HOME), WORK(Phone.TYPE_WORK), OTHER(Phone.TYPE_OTHER);

    companion object {
        fun fromType(t: Int) = entries.firstOrNull { it.typeInt == t } ?: OTHER
    }
}

enum class EmailLabel(val typeInt: Int) {
    HOME(Email.TYPE_HOME), WORK(Email.TYPE_WORK), OTHER(Email.TYPE_OTHER);

    companion object {
        fun fromType(t: Int) = entries.firstOrNull { it.typeInt == t } ?: OTHER
    }
}

data class PhoneEntry(val id: Long = 0L, val number: String, val label: PhoneLabel = PhoneLabel.MOBILE)
data class EmailEntry(val id: Long = 0L, val email: String, val label: EmailLabel = EmailLabel.HOME)

sealed interface ContactSource {
    data object Device : ContactSource
    data class Sim(val slot: Int) : ContactSource
    data class Account(val name: String, val type: String) : ContactSource
}

data class Contact(
    val id: Long,
    val firstName: String = "",
    val lastName: String = "",
    val displayName: String,
    val phones: List<PhoneEntry> = emptyList(),
    val emails: List<EmailEntry> = emptyList(),
    val address: String? = null,
    val note: String? = null,
    val organization: String? = null,
    val source: ContactSource = ContactSource.Device,
    val avatarColor: Int = 0,
    val isFavorite: Boolean = false,
    val photoBytes: ByteArray? = null
) {
    val primaryPhone: String? get() = phones.firstOrNull()?.number
}

// ─── Backup models ───

enum class BackupFormat(val ext: String, val mime: String) {
    VCF("vcf", "text/vcard"), CSV("csv", "text/csv"), JSON("json", "application/json")
}

enum class BackupDestination { INTERNAL, SAF }

enum class EncryptionType { NONE, PASSWORD, DEVICE_KEY }

data class BackupInfo(
    val id: Long,
    val name: String,
    val fileName: String?,
    val uri: String?,
    val format: BackupFormat,
    val destination: BackupDestination,
    val encryption: EncryptionType,
    val sizeKb: Double,
    val contactCount: Int,
    val isAuto: Boolean,
    val createdAt: Long
) {
    val encrypted: Boolean get() = encryption != EncryptionType.NONE
}

data class RestoreResult(val added: Int, val skipped: Int, val total: Int)

data class CompareResult(
    val matched: Int,
    val onlyInBackup: List<ParsedContact>,
    val onlyInDevice: List<Contact>
)

/** Source-agnostic representation used for VCF/CSV/JSON encode & decode */
data class ParsedContact(
    val firstName: String = "",
    val lastName: String = "",
    val displayName: String,
    val phones: List<PhoneEntry> = emptyList(),
    val emails: List<EmailEntry> = emptyList(),
    val address: String? = null,
    val note: String? = null,
    val organization: String? = null,
    val photoBytes: ByteArray? = null,
    val sourceTag: String? = null
) {
    val primaryPhone: String? get() = phones.firstOrNull()?.number
}

data class DuplicateGroup(
    val key: String,
    val reason: Reason,
    val contacts: List<Contact>
) {
    enum class Reason { PHONE, NAME }
}

// ─── Settings models ───

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class ScheduleMode { OFF, DAILY, WEEKLY }

data class SettingsData(
    val language: String = "fa",
    val theme: ThemeMode = ThemeMode.LIGHT,
    val lockEnabled: Boolean = false,
    val pinHash: String? = null,
    val fingerprintEnabled: Boolean = true,
    val updateCheckEnabled: Boolean = true,
    val scheduleMode: ScheduleMode = ScheduleMode.OFF,
    val scheduleHour: Int = 9,
    val autoFormat: BackupFormat = BackupFormat.VCF,
    val lastAutoBackupAt: Long? = null
)

/** A copy/move destination option shown in dialogs */
data class TargetOption(val source: ContactSource, val label: String, val simWriteSupported: Boolean = true)
