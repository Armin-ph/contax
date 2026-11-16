package com.contax.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.contax.app.data.update.UpdateInfo
import com.contax.app.data.update.UpdateInstaller
import com.contax.app.util.AppInfo
import com.contax.app.util.Fa
import kotlinx.coroutines.launch

/**
 * پاپ‌آپ نسخه جدید: نسخه فعلی و جدید، لیست تغییرات (از GitHub Release)
 * و دکمه «دانلود و نصب» که APK را می‌گیرد و صفحه نصب اندروید را باز می‌کند.
 */
@Composable
fun UpdateDialog(info: UpdateInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = {
            Text(
                stringResource(R.string.update_available_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(
                        R.string.update_available_text,
                        Fa.str(info.versionName),
                        Fa.str(AppInfo.versionName)
                    )
                )

                Text(
                    stringResource(R.string.update_changelog),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    info.changelog,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState())
                )

                if (downloading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            stringResource(R.string.update_downloading),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (failed) {
                    Text(
                        stringResource(R.string.update_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !downloading && info.apkUrl != null,
                onClick = {
                    downloading = true
                    failed = false
                    scope.launch {
                        try {
                            UpdateInstaller.downloadAndInstall(context, info)
                        } catch (t: Throwable) {
                            downloading = false
                            failed = true
                        }
                    }
                }
            ) { Text(stringResource(R.string.update_btn)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) }
        }
    )
}
