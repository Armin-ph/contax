package com.contax.app.ui.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.contax.app.MainViewModel
import com.contax.app.R
import com.contax.app.util.Fa

@Composable
fun LockScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()

    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    val promptTitle = stringResource(R.string.app_name)
    val promptSubtitle = stringResource(R.string.lock_subtitle)
    val negativeText = stringResource(R.string.action_cancel)

    fun showBiometric() {
        val activity = context as? FragmentActivity ?: return
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    vm.unlock()
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(promptTitle)
            .setSubtitle(promptSubtitle)
            .setNegativeButtonText(negativeText)
            .build()
        prompt.authenticate(info)
    }

    LaunchedEffect(Unit) {
        if (settings.fingerprintEnabled && vm.biometricAvailable()) showBiometric()
    }

    fun onDigit(d: Char) {
        error = false
        val np = pin + d
        pin = np
        if (vm.verifyPin(np)) {
            vm.unlock()
        } else if (np.length >= 6) {
            error = true
            pin = ""
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.background
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Image(
                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_full),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(92.dp)
            )
            Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.lock_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // PIN dots
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                repeat(6) { i ->
                    Box(
                        Modifier.size(14.dp).background(
                            if (i < pin.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        )
                    )
                }
            }

            if (error) {
                Text(
                    stringResource(R.string.wrong_pin),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }

            // Keypad
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("*", "0", "⌫")
            )
            keys.forEach { rowKeys ->
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    rowKeys.forEach { key ->
                        if (key == "*") {
                            if (settings.fingerprintEnabled && vm.biometricAvailable()) {
                                Surface(
                                    onClick = { showBiometric() },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.size(68.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Rounded.Fingerprint,
                                            contentDescription = stringResource(R.string.unlock_fingerprint),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(34.dp)
                                        )
                                    }
                                }
                            } else {
                                Spacer(Modifier.size(68.dp))
                            }
                        } else if (key == "⌫") {
                            Surface(
                                onClick = { pin = pin.dropLast(1); error = false },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.size(68.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.Backspace,
                                        contentDescription = stringResource(R.string.action_cancel),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        } else {
                            Surface(
                                onClick = { onDigit(key[0]) },
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.size(68.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        Fa.str(key),
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
