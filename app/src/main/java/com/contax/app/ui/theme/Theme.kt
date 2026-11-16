package com.contax.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.contax.app.data.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF1976D2),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E8FB),
    onPrimaryContainer = Color(0xFF0B3B6F),
    secondary = Color(0xFF4A607C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE5F4),
    onSecondaryContainer = Color(0xFF1E3145),
    tertiary = Color(0xFF00796B),
    tertiaryContainer = Color(0xFFCCF0E9),
    onTertiaryContainer = Color(0xFF003D36),
    background = Color(0xFFE9EEF5),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE3EAF3),
    onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFFC6CCD6),
    error = Color(0xFFC62828),
    errorContainer = Color(0xFFFDECEA),
    onError = Color(0xFFFFFFFF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9FCBFF),
    onPrimary = Color(0xFF00325B),
    primaryContainer = Color(0xFF17497E),
    onPrimaryContainer = Color(0xFFD6E7FF),
    secondary = Color(0xFFB3C7E2),
    onSecondary = Color(0xFF1C324B),
    secondaryContainer = Color(0xFF334961),
    onSecondaryContainer = Color(0xFFDCE5F4),
    tertiary = Color(0xFF7BD9CB),
    tertiaryContainer = Color(0xFF005048),
    onTertiaryContainer = Color(0xFFA6F1E5),
    background = Color(0xFF0E1218),
    onBackground = Color(0xFFE2E3E6),
    surface = Color(0xFF191F27),
    onSurface = Color(0xFFE2E3E6),
    surfaceVariant = Color(0xFF212A36),
    onSurfaceVariant = Color(0xFFBEC5D2),
    outline = Color(0xFF3A4453),
    error = Color(0xFFEF9A9A),
    errorContainer = Color(0xFF5C1A1A),
    onError = Color(0xFF3B0A0A)
)

@Composable
fun ContaxTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // هماهنگی رنگ آیکون‌های نوار وضعیت/ناوبری با تمِ فعال برنامه.
    // (تغییر تم داخل برنامه باعث Recreate شدن اکتیویتی نمی‌شود،
    //  پس باید اینجا رنگ آیکون‌ها را با تم هماهنگ نگه داریم)
    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(dark) {
            val window = view.context.findActivity().window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
            onDispose { }
        }
    }

    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}

/** یافتن Activity از دل Context (برای کنترل Window/Insets) */
private tailrec fun Context.findActivity(): Activity =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> error("Could not find activity from context: $this")
    }
