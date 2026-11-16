package com.contax.app.ui.common

import android.content.Context
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.contax.app.R
import com.contax.app.data.model.ContactSource
import com.contax.app.util.initials

// Light-tinted avatar palette (bg to fg) — matches the Contax design
val AvatarPalette = listOf(
    Color(0xFFE3F2FD) to Color(0xFF1565C0),
    Color(0xFFE8F5E9) to Color(0xFF2E7D32),
    Color(0xFFFFF3E0) to Color(0xFFEF6C00),
    Color(0xFFF3E5F5) to Color(0xFF6A1B9A),
    Color(0xFFFCE4EC) to Color(0xFFC2185B),
    Color(0xFFE0F2F1) to Color(0xFF00695C)
)

@Composable
fun ContactAvatar(displayName: String, colorIndex: Int, size: Dp = 44.dp, photoBytes: ByteArray? = null) {
    if (photoBytes != null) {
        val bmp = remember(photoBytes) {
            BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.size)?.asImageBitmap()
        }
        if (bmp != null) {
            androidx.compose.foundation.Image(
                bitmap = bmp,
                contentDescription = displayName,
                modifier = Modifier.size(size).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            return
        }
    }
    val (bg, fg) = AvatarPalette[colorIndex.coerceIn(0, AvatarPalette.size - 1)]
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials(displayName),
            color = fg,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.36f).sp
        )
    }
}

@Composable
fun sourceLabel(source: ContactSource): String = when (source) {
    ContactSource.Device -> stringResource(R.string.source_device)
    is ContactSource.Sim -> stringResource(if (source.slot == 1) R.string.source_sim1 else R.string.source_sim2)
    is ContactSource.Account -> when {
        source.type == "com.google" -> stringResource(R.string.source_google)
        source.type.contains("exchange", ignoreCase = true) -> stringResource(R.string.source_exchange)
        else -> source.name.ifBlank { stringResource(R.string.source_account) }
    }
}

@Composable
fun SourceBadge(source: ContactSource, modifier: Modifier = Modifier) {
    val (bg, fg) = when (source) {
        ContactSource.Device -> Color(0xFFE3F2FD) to Color(0xFF1565C0)
        is ContactSource.Sim -> Color(0xFFEDE7F6) to Color(0xFF5E35B1)
        is ContactSource.Account ->
            if (source.type == "com.google") Color(0xFFE3F2FD) to Color(0xFF1A73E8)
            else Color(0xFFFFF3E0) to Color(0xFFB26A00)
    }
    val icon: ImageVector = when (source) {
        ContactSource.Device -> Icons.Rounded.Smartphone
        is ContactSource.Sim -> Icons.Rounded.SimCard
        is ContactSource.Account ->
            if (source.type == "com.google") Icons.Rounded.Cloud else Icons.Rounded.Business
    }
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(50)) {
        Row(
            modifier = modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp))
            Text(
                sourceLabel(source),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun EmptyState(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Rounded.Inbox, contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        subtitle?.let {
            Text(
                it, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(vertical = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun RadioRow(
    selected: Boolean,
    label: String,
    sub: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.padding(start = 4.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            sub?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

fun showToast(context: Context, msg: String) {
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}
