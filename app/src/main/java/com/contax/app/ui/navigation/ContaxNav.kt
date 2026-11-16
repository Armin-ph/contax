package com.contax.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.contax.app.MainViewModel
import com.contax.app.R
import com.contax.app.ui.backup.BackupScreen
import com.contax.app.ui.common.UpdateDialog
import com.contax.app.ui.contacts.ContactDetailScreen
import com.contax.app.ui.contacts.ContactEditScreen
import com.contax.app.ui.contacts.ContactsScreen
import com.contax.app.ui.lock.LockScreen
import com.contax.app.ui.settings.SettingsScreen
import com.contax.app.ui.tools.ToolsScreen

data class TabItem(val route: String, val labelRes: Int, val icon: ImageVector)

private val tabs = listOf(
    TabItem("contacts", R.string.tab_contacts, Icons.Rounded.People),
    TabItem("backup", R.string.tab_backup, Icons.Rounded.CloudSync),
    TabItem("tools", R.string.tab_tools, Icons.Rounded.AutoFixHigh),
    TabItem("settings", R.string.tab_settings, Icons.Rounded.Settings)
)

@Composable
fun ContaxAppRoot(vm: MainViewModel) {
    val locked by vm.locked.collectAsState()
    if (locked) {
        LockScreen(vm)
        return
    }

    val nav = rememberNavController()
    val back by nav.currentBackStackEntryAsState()
    val route = back?.destination?.route ?: "contacts"

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    val selected = route == tab.route ||
                        (tab.route == "contacts" && (route.startsWith("detail") || route.startsWith("edit")))
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) }
                    )
                }
            }
        }
    ) { pad ->
        NavHost(
            navController = nav,
            startDestination = "contacts",
            modifier = Modifier.padding(pad)
        ) {
            composable("contacts") {
                ContactsScreen(
                    onOpen = { nav.navigate("detail/$it") },
                    onAdd = { nav.navigate("edit") }
                )
            }
            composable("detail/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0L
                ContactDetailScreen(
                    contactId = id,
                    onEdit = { nav.navigate("edit/$it") },
                    onBack = { nav.popBackStack() }
                )
            }
            composable("edit") {
                ContactEditScreen(contactId = null, onDone = { nav.popBackStack() })
            }
            composable("edit/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull()
                ContactEditScreen(contactId = id, onDone = { nav.popBackStack() })
            }
            composable("backup") { BackupScreen() }
            composable("tools") { ToolsScreen() }
            composable("settings") { SettingsScreen(vm) }
        }
    }

    // ── پاپ‌آپ خودکار نسخه جدید (هر وقت Release جدید در گیت‌هاب باشد) ──
    val updateInfo by vm.updateInfo.collectAsState()
    val updateDialogVisible by vm.updateDialogVisible.collectAsState()
    if (updateDialogVisible && updateInfo != null) {
        UpdateDialog(
            info = updateInfo!!, 
            onDismiss = { vm.dismissUpdateDialog() }
        )
    }
}
