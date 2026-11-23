package com.contax.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.biometric.BiometricManager
import androidx.lifecycle.viewModelScope
import com.contax.app.data.backup.BackupCrypto
import com.contax.app.data.model.BackupFormat
import com.contax.app.data.model.ScheduleMode
import com.contax.app.data.model.SettingsData
import com.contax.app.data.model.ThemeMode
import com.contax.app.data.prefs.SettingsDataStore
import com.contax.app.data.update.UpdateChecker
import com.contax.app.data.update.UpdateInfo
import com.contax.app.data.work.BackupScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** وضعیت بررسی دستی به‌روزرسانی (از صفحه تنظیمات) */
enum class UpdateCheckState { IDLE, CHECKING, AVAILABLE, LATEST }

class MainViewModel(app: Application, initial: SettingsData) : AndroidViewModel(app) {

    private val prefs = SettingsDataStore(app)

    private val _settings = MutableStateFlow(initial)
    val settings: StateFlow<SettingsData> = _settings

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked

    init {
        // وضعیت اولیه (تم/قفل/زمان‌بندی) از MainActivity می‌آید — همان یک خواندنِ
        // همگامِ پیش از فریم اول؛ اینجا دیگر runBlocking نداریم.
        _locked.value = initial.lockEnabled
        // keep auto-backup worker in sync with stored settings
        BackupScheduler.apply(app, initial.scheduleMode, initial.scheduleHour)
        // keep collecting changes
        viewModelScope.launch {
            prefs.settings.collect { s ->
                _settings.value = s
                if (!s.lockEnabled && _locked.value) _locked.value = false
            }
        }
    }

    // ── in-app update checker ──

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo

    private val _updateDialogVisible = MutableStateFlow(false)
    val updateDialogVisible: StateFlow<Boolean> = _updateDialogVisible

    private val _updateCheckState = MutableStateFlow(UpdateCheckState.IDLE)
    val updateCheckState: StateFlow<UpdateCheckState> = _updateCheckState

    /** پیام یک‌بارمصرف (مثل «شما روی آخرین نسخه هستید») — پس از نمایش باید consume شود
     *  تا هر بار ورود به تنظیمات دوباره نمایش داده نشود */
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast

    fun consumeToast() { _toast.value = null }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            // بررسی خودکار نسخه جدید هنگام شروع برنامه — فقط اگر کاربر آن را روشن کرده باشد
            if (!_settings.value.updateCheckEnabled) return@launch
            val info = UpdateChecker.check() ?: return@launch
            _updateInfo.value = info
            _updateDialogVisible.value = true
        }
    }

    /** بررسی دستی از بخش «درباره برنامه» */
    fun checkForUpdate() {
        if (_updateCheckState.value == UpdateCheckState.CHECKING) return
        viewModelScope.launch {
            _updateCheckState.value = UpdateCheckState.CHECKING
            val info = UpdateChecker.check()
            _updateInfo.value = info
            _updateCheckState.value =
                if (info != null) UpdateCheckState.AVAILABLE else UpdateCheckState.LATEST
            if (info != null) {
                _updateDialogVisible.value = true
            } else {
                // پیام «آخرین نسخه» فقط یک‌بار بلافاصله بعد از بررسی دستی نمایش داده می‌شود
                _toast.value = getApplication<Application>().getString(R.string.update_latest)
            }
        }
    }

    fun dismissUpdateDialog() { _updateDialogVisible.value = false }

    // ── lock ──

    fun unlock() { _locked.value = false }

    fun lockNow() { if (_settings.value.lockEnabled) _locked.value = true }

    fun verifyPin(pin: String): Boolean =
        _settings.value.pinHash?.let { BackupCrypto.verifyPin(pin, it) } ?: false

    fun hasPin(): Boolean = _settings.value.pinHash != null

    fun biometricAvailable(): Boolean = try {
        BiometricManager.from(getApplication<Application>())
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    } catch (t: Throwable) {
        false
    }

    // ── settings ──

    fun setLanguage(lang: String) = viewModelScope.launch {
        prefs.setLanguage(lang)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
    }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { prefs.setTheme(mode) }

    fun setUpdateCheckEnabled(v: Boolean) = viewModelScope.launch { prefs.setUpdateCheckEnabled(v) }

    fun setLockEnabled(v: Boolean) = viewModelScope.launch { prefs.setLockEnabled(v) }

    fun setFingerprint(v: Boolean) = viewModelScope.launch {
        // گارد ایمنی: اثر انگشت فقط وقتی قابل فعال شدن است که رمز PIN قبلاً تنظیم شده باشد
        if (v && _settings.value.pinHash == null) return@launch
        prefs.setFingerprint(v)
    }

    /**
     * ذخیره PIN و انجام اقدام بعدی (روشن‌کردن قفل / اثر انگشت) به‌صورت اتمیک
     * در یک کوروتین — تا گارد اثر انگشت قبل از ذخیره‌شدن هش اجرا نشود.
     */
    fun savePin(pin: String, enableLock: Boolean = false, enableFingerprint: Boolean = false) =
        viewModelScope.launch {
            prefs.setPinHash(BackupCrypto.hashPin(pin))
            if (enableFingerprint) prefs.setFingerprint(true)
            if (enableLock) prefs.setLockEnabled(true)
        }

    fun clearPin() = viewModelScope.launch {
        prefs.setPinHash(null)
        prefs.setLockEnabled(false)
    }

    fun setSchedule(mode: ScheduleMode, hour: Int, format: BackupFormat) = viewModelScope.launch {
        prefs.setSchedule(mode, hour, format)
        BackupScheduler.apply(getApplication(), mode, hour)
    }
}
