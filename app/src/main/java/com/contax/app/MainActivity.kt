package com.contax.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.contax.app.data.prefs.SettingsDataStore
import com.contax.app.ui.navigation.ContaxAppRoot
import com.contax.app.ui.theme.ContaxTheme
import com.contax.app.util.Fa
import kotlinx.coroutines.runBlocking

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-Edge: نوار وضعیت شفاف — آیکون‌های ساعت/باتری با رنگ مناسب تم
        // نمایش داده می‌شوند و دیگر نوار سفید روی استاتوس‌بار نمی‌افتد.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val prefs = SettingsDataStore(applicationContext)
        // تنها خواندنِ همگامِ برنامه (runBlocking): برای درست بودن زبان/تم/قفل در
        // «فریم اول» لازم است. نتیجه به MainViewModel هم پاس داده می‌شود تا
        // ViewModel دوبار خواندن sync نداشته باشد.
        val initial = runBlocking { prefs.current() }
        if (!prefs.isFirstLocaleApplied()) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(initial.language))
            prefs.markLocaleApplied()
        }

        // همگام‌سازی زبانِ مؤثر برنامه برای توابع اعداد و تاریخ (Fa)
        // تا در زبان انگلیسی اعداد لاتین و در فارسی اعداد فارسی نمایش داده شوند.
        val appLocales = AppCompatDelegate.getApplicationLocales()
        if (!appLocales.isEmpty) {
            val primary = appLocales.get(0)
            if (primary != null) Fa.syncLanguage(primary.language)
            else Fa.syncLocale(resources.configuration)
        } else {
            Fa.syncLocale(resources.configuration)
        }

        val app = application
        setContent {
            val vm: MainViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { MainViewModel(app, initial) }
                }
            )
            val settings by vm.settings.collectAsState()
            ContaxTheme(themeMode = settings.theme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ContaxAppRoot(vm)
                }
            }
        }
    }
}
