package com.contax.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CurrencyBitcoin
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Payment
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contax.app.MainViewModel
import com.contax.app.R
import com.contax.app.UpdateCheckState
import com.contax.app.data.model.ThemeMode
import com.contax.app.ui.common.SectionHeader
import com.contax.app.ui.common.showToast
import com.contax.app.util.AppInfo
import com.contax.app.util.Fa

private fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (t: Throwable) {
        showToast(context, context.getString(R.string.fail_error))
    }
}

/** رنگ نارنجی رسمی لوگوی بیتکوین برای دکمه پرداخت با کریپتو */
private val BitcoinOrange = Color(0xFFF7931A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val updateCheckState by vm.updateCheckState.collectAsState()

    var showPinDialog by remember { mutableStateOf(false) }
    var showCryptoDialog by remember { mutableStateOf(false) }

    // بعد از ذخیره PIN چه کاری انجام شود؟
    // NONE = فقط پین ذخیره شود (دکمه «تنظیم رمز»)
    // ENABLE_LOCK = قفل برنامه روشن شود (سوییچ قفل)
    // ENABLE_FINGERPRINT = اثر انگشت + قفل برنامه روشن شوند (سوییچ اثر انگشت)
    var pendingAfterPin by remember { mutableStateOf(PendingPinAction.NONE) }

    // پیام یک‌بارمصرف بررسی دستی به‌روزرسانی («شما روی آخرین نسخه هستید»)
    // فقط بلافاصله بعد از فشردن دکمه بررسی نمایش داده می‌شود؛
    // ورود به صفحه تنظیمات هیچ پیامی نشان نمی‌دهد.
    val toastMsg by vm.toast.collectAsState()
    val toastText = toastMsg
    if (toastText != null) {
        LaunchedEffect(toastText) {
            showToast(context, toastText)
            vm.consumeToast()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
            actions = {
                Icon(
                    Icons.Rounded.Info, contentDescription = null,
                    modifier = Modifier.padding(end = 16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))

            // ── Appearance & language ──
            SectionHeader(stringResource(R.string.appearance))
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.language), fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.language == "fa",
                            onClick = { vm.setLanguage("fa") },
                            label = { Text("فارسی") }
                        )
                        FilterChip(
                            selected = settings.language == "en",
                            onClick = { vm.setLanguage("en") },
                            label = { Text("English") }
                        )
                    }
                    Text(stringResource(R.string.theme), fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                            ThemeMode.LIGHT to stringResource(R.string.theme_light),
                            ThemeMode.DARK to stringResource(R.string.theme_dark)
                        ).forEach { (mode, label) ->
                            FilterChip(
                                selected = settings.theme == mode,
                                onClick = { vm.setTheme(mode) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }

            // ── Security ──
            SectionHeader(stringResource(R.string.security))
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.app_lock), fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.app_lock_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.lockEnabled,
                            onCheckedChange = { enabled ->
                                // قفل برنامه همیشه نیاز به PIN دارد — حتی اگر بیومتریک موجود باشد
                                if (enabled && !vm.hasPin()) {
                                    showToast(context, context.getString(R.string.pin_required_for_lock))
                                    pendingAfterPin = PendingPinAction.ENABLE_LOCK
                                    showPinDialog = true
                                } else {
                                    vm.setLockEnabled(enabled)
                                }
                            }
                        )
                    }
                    OutlinedButton(onClick = {
                        pendingAfterPin = PendingPinAction.NONE
                        showPinDialog = true
                    }) {
                        Text(
                            if (settings.pinHash != null) stringResource(R.string.change_pin)
                            else stringResource(R.string.set_pin)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.fingerprint))
                            if (!vm.biometricAvailable()) {
                                Text(
                                    stringResource(R.string.fingerprint_unavailable),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = settings.fingerprintEnabled,
                            enabled = vm.biometricAvailable(),
                            onCheckedChange = { enabled ->
                                // فعال‌سازی اثر انگشت فقط بعد از تنظیم رمز PIN مجاز است
                                if (enabled && !vm.hasPin()) {
                                    showToast(context, context.getString(R.string.pin_required_for_fingerprint))
                                    pendingAfterPin = PendingPinAction.ENABLE_FINGERPRINT
                                    showPinDialog = true
                                } else {
                                    vm.setFingerprint(enabled)
                                }
                            }
                        )
                    }
                    if (settings.lockEnabled) {
                        OutlinedButton(onClick = { vm.lockNow() }) {
                            Text(stringResource(R.string.lock_now))
                        }
                    }
                }
            }

            // ── About ──
            SectionHeader(stringResource(R.string.about))
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Image(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_full),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier.size(72.dp),
                        contentScale = ContentScale.Fit
                    )
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.about_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.version) + ": " + Fa.str(AppInfo.versionName),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(4.dp))

                    // سایت سازنده
                    AboutLinkRow(
                        icon = { Icon(Icons.Rounded.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                        title = stringResource(R.string.website),
                        subtitle = AppInfo.WEBSITE_URL,
                        onClick = { openLink(context, AppInfo.WEBSITE_URL) }
                    )
                    // گیت‌هاب برنامه
                    AboutLinkRow(
                        icon = { Icon(Icons.Rounded.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                        title = stringResource(R.string.github_link),
                        subtitle = AppInfo.GITHUB_URL.removePrefix("https://"),
                        onClick = { openLink(context, AppInfo.GITHUB_URL) }
                    )
                    // کانال تلگرام
                    AboutLinkRow(
                        icon = { Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) },
                        title = stringResource(R.string.telegram_link),
                        subtitle = AppInfo.TELEGRAM_URL.removePrefix("https://"),
                        onClick = { openLink(context, AppInfo.TELEGRAM_URL) }
                    )

                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(4.dp))

                    // حمایت مالی
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Favorite, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)
                        )
                        Text(stringResource(R.string.donate), fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { openLink(context, AppInfo.DONATE_RIAL_URL) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Rounded.Payment, contentDescription = null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.pay_rial),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            onClick = { showCryptoDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BitcoinOrange,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Rounded.CurrencyBitcoin, contentDescription = null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.pay_crypto),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // بررسی خودکار به‌روزرسانی (روشن/خاموش به انتخاب کاربر)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Sync, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.update_auto_check), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(R.string.update_auto_check_sub),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.updateCheckEnabled,
                            onCheckedChange = { vm.setUpdateCheckEnabled(it) }
                        )
                    }

                    // بررسی به‌روزرسانی
                    OutlinedButton(
                        onClick = { vm.checkForUpdate() },
                        enabled = updateCheckState != UpdateCheckState.CHECKING
                    ) {
                        if (updateCheckState == UpdateCheckState.CHECKING) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Rounded.SystemUpdateAlt, contentDescription = null, Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.check_updates))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showPinDialog) {
        PinDialog(
            onDismiss = { showPinDialog = false },
            onSave = { pin ->
                // ذخیره پین + اقدام بعدی به‌صورت اتمیک در یک کوروتین (بدون race)
                vm.savePin(
                    pin,
                    enableLock = pendingAfterPin != PendingPinAction.NONE,
                    enableFingerprint = pendingAfterPin == PendingPinAction.ENABLE_FINGERPRINT
                )
                showToast(context, context.getString(R.string.pin_saved))
                pendingAfterPin = PendingPinAction.NONE
                showPinDialog = false
            }
        )
    }

    if (showCryptoDialog) {
        CryptoDonateDialog(onDismiss = { showCryptoDialog = false })
    }
}

/** اقدامی که بلافاصله بعد از ذخیره PIN انجام می‌شود */
private enum class PendingPinAction { NONE, ENABLE_LOCK, ENABLE_FINGERPRINT }

/** پنجره پرداخت با کریپتو: نمایش ولت‌ها با امکان کپی آدرس */
@Composable
private fun CryptoDonateDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.crypto_pay_title)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    stringResource(R.string.crypto_pay_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AppInfo.CRYPTO_WALLETS.forEach { wallet ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    wallet.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    wallet.address,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = {
                                clipboard.setText(AnnotatedString(wallet.address))
                                showToast(context, context.getString(R.string.copied))
                            }) {
                                Icon(
                                    Icons.Rounded.ContentCopy,
                                    contentDescription = stringResource(R.string.copy),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}

@Composable
private fun AboutLinkRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        icon()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PinDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_pin)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.enter_pin), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.confirm_pin), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) confirm = it },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = error
                )
                if (error) {
                    Text(
                        stringResource(R.string.pin_mismatch),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (pin.length in 4..6 && pin == confirm) {
                    onSave(pin)
                } else {
                    error = true
                }
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
