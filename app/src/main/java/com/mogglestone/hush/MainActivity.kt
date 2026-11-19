package com.mogglestone.hush

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(val label: String, val pkg: String)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HushTheme {
                HushApp()
            }
        }
    }

    private fun loadApps(): List<AppEntry> {
        val pm = packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, PackageManager.MATCH_ALL)
            .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.pkg != packageName } // never block ourselves
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", packageName, null))
        startActivity(intent)
    }

    @Composable
    private fun HushApp() {
        var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
        LaunchedEffect(Unit) {
            apps = withContext(Dispatchers.IO) { loadApps() }
        }
        var tab by remember { mutableIntStateOf(0) }

        Column(
            Modifier
                .fillMaxSize()
                .background(Ivory)
                .padding(horizontal = 24.dp)
                .padding(top = 48.dp, bottom = 16.dp)
        ) {
            Text(
                "Hush",
                style = TextStyle(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 40.sp),
                color = Ink
            )
            Text(
                "Quiet the apps you choose.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkSoft
            )
            Spacer(Modifier.height(20.dp))
            ModeTabs(selected = tab, onSelect = { tab = it })
            HorizontalDivider(color = Hairline)

            if (tab == 0) {
                NetBlockPanel(apps)
            } else {
                RedirectPanel(apps)
            }
        }
    }

    @Composable
    private fun ModeTabs(selected: Int, onSelect: (Int) -> Unit) {
        Row(Modifier.fillMaxWidth()) {
            listOf("NetBlock", "RedirectBlock").forEachIndexed { index, label ->
                val on = index == selected
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { onSelect(index) }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (on) Ink else InkSoft
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(if (on) 2.dp else 1.dp)
                            .background(if (on) Ink else Hairline)
                    )
                }
            }
        }
    }

    @Composable
    private fun ColumnScope.NetBlockPanel(apps: List<AppEntry>) {
        var selected by remember { mutableStateOf(Prefs.netblockPackages(this@MainActivity)) }
        var active by remember { mutableStateOf(Prefs.isNetblockActive(this@MainActivity)) }
        var includeRelated by remember { mutableStateOf(Prefs.includeRelated(this@MainActivity)) }
        var killProcesses by remember { mutableStateOf(Prefs.killProcesses(this@MainActivity)) }
        var query by remember { mutableStateOf("") }
        var related by remember { mutableStateOf<Set<String>>(emptySet()) }

        // Related-app scan runs off the main thread.
        LaunchedEffect(selected, includeRelated) {
            val picked = selected
            val withRelated = includeRelated
            related = withContext(Dispatchers.IO) {
                if (withRelated) Related.relatedTo(this@MainActivity, picked) else emptySet()
            }
        }

        val vpnPermission = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                BlockerVpnService.start(this@MainActivity)
                active = true
            }
        }

        fun startBlocking() {
            Prefs.setNetblockPackages(this@MainActivity, selected)
            val prepare = VpnService.prepare(this@MainActivity)
            if (prepare != null) {
                vpnPermission.launch(prepare)
            } else {
                BlockerVpnService.start(this@MainActivity)
                active = true
            }
        }

        fun stopBlocking() {
            BlockerVpnService.stop(this@MainActivity)
            active = false
        }

        // Re-apply the block with the current settings if it is running.
        fun refreshIfActive() {
            if (!active) return
            if (selected.isEmpty()) stopBlocking() else BlockerVpnService.start(this@MainActivity)
        }

        fun toggleApp(pkg: String) {
            selected = if (pkg in selected) selected - pkg else selected + pkg
            Prefs.setNetblockPackages(this@MainActivity, selected)
            refreshIfActive()
        }

        Spacer(Modifier.height(28.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Seal(
                active = active,
                enabled = active || selected.isNotEmpty(),
                onClick = { if (active) stopBlocking() else startBlocking() }
            )
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    active -> "Blocking ${selected.size + related.size} app(s)"
                    selected.isEmpty() -> "Pick apps below to begin"
                    else -> "Press the seal to cut ${selected.size} app(s) off"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = InkSoft
            )
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = Hairline)
        SettingRow(
            title = "Include related apps",
            caption = "Same-family packages, e.g. Messenger, Facebook and Meta services",
            checked = includeRelated,
            onChecked = {
                includeRelated = it
                Prefs.setIncludeRelated(this@MainActivity, it)
                refreshIfActive()
            }
        )
        HorizontalDivider(color = Hairline)
        SettingRow(
            title = "Kill background processes",
            caption = "Ends running processes every 15 seconds so open connections drop",
            checked = killProcesses,
            onChecked = {
                killProcesses = it
                Prefs.setKillProcesses(this@MainActivity, it)
                refreshIfActive()
            }
        )
        HorizontalDivider(color = Hairline)

        if (related.isNotEmpty()) {
            Text(
                "Also covered: " + related.sorted().joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        AppPicker(
            apps = apps,
            selected = selected,
            query = query,
            onQuery = { query = it },
            onToggle = { toggleApp(it) },
            showForceStop = true
        )
    }

    @Composable
    private fun ColumnScope.RedirectPanel(apps: List<AppEntry>) {
        var selected by remember { mutableStateOf(Prefs.redirectPackages(this@MainActivity)) }
        var active by remember { mutableStateOf(Prefs.isRedirectActive(this@MainActivity)) }
        var query by remember { mutableStateOf("") }

        fun toggleApp(pkg: String) {
            selected = if (pkg in selected) selected - pkg else selected + pkg
            Prefs.setRedirectPackages(this@MainActivity, selected)
        }

        Spacer(Modifier.height(28.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Seal(
                active = active,
                enabled = active || selected.isNotEmpty(),
                onClick = {
                    active = !active
                    Prefs.setRedirectActive(this@MainActivity, active)
                }
            )
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    active -> "Stopping links from ${selected.size} app(s)"
                    selected.isEmpty() -> "Pick apps below to begin"
                    else -> "Press the seal to stop their links"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = InkSoft
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Links opened by these apps are dropped. Hush must be the default app for web links: " +
                "open Hush's settings and set it under Open by default.",
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft
        )
        OutlinedButton(
            onClick = { openAppSettings() },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("Open Hush settings")
        }

        Spacer(Modifier.height(12.dp))
        AppPicker(
            apps = apps,
            selected = selected,
            query = query,
            onQuery = { query = it },
            onToggle = { toggleApp(it) },
            showForceStop = false
        )
    }

    @Composable
    private fun ColumnScope.AppPicker(
        apps: List<AppEntry>,
        selected: Set<String>,
        query: String,
        onQuery: (String) -> Unit,
        onToggle: (String) -> Unit,
        showForceStop: Boolean
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            label = { Text("Search apps") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )
        val shown = apps.filter { it.label.contains(query, ignoreCase = true) }
        LazyColumn(Modifier.weight(1f)) {
            items(shown, key = { it.pkg }) { app ->
                val checked = app.pkg in selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(app.pkg) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = checked, onCheckedChange = null)
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(app.label, style = MaterialTheme.typography.bodyLarge, color = Ink)
                        Text(app.pkg, style = MaterialTheme.typography.bodySmall, color = InkSoft)
                    }
                    if (checked && showForceStop) {
                        // Android only lets the user force-stop another app.
                        TextButton(onClick = { Killer.openForceStopPage(this@MainActivity, app.pkg) }) {
                            Text("Force stop", color = Ink)
                        }
                    }
                }
                HorizontalDivider(color = Hairline)
            }
        }
    }

    @Composable
    private fun SettingRow(
        title: String,
        caption: String,
        checked: Boolean,
        onChecked: (Boolean) -> Unit
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = Ink)
                Text(caption, style = MaterialTheme.typography.bodySmall, color = InkSoft)
            }
            Switch(checked = checked, onCheckedChange = onChecked)
        }
    }

    /** The round seal that starts and stops a mode. */
    @Composable
    private fun Seal(active: Boolean, enabled: Boolean, onClick: () -> Unit) {
        val ring = if (active) Alarm else Ink
        Box(
            Modifier
                .size(148.dp)
                .clip(CircleShape)
                .border(2.dp, ring, CircleShape)
                .background(if (active) Alarm.copy(alpha = 0.06f) else Color.Transparent)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(124.dp).border(1.dp, ring.copy(alpha = 0.45f), CircleShape))
            Text(
                if (active) "Lift" else "Seal",
                style = TextStyle(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 28.sp),
                color = if (enabled) ring else InkSoft
            )
        }
    }
}
