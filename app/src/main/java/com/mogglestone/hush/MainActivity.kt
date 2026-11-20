package com.mogglestone.hush

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
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
            HushRoot()
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
    private fun MainScreen(onOpenSettings: () -> Unit) {
        var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
        LaunchedEffect(Unit) {
            apps = withContext(Dispatchers.IO) { loadApps() }
        }
        var tab by remember { mutableIntStateOf(0) }

        Column(
            Modifier
                .fillMaxSize()
                .background(ivory())
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 8.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Hush",
                    style = TextStyle(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 40.sp),
                    color = ink(),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = ink())
                }
            }
            Text(
                "Quiet the apps you choose.",
                style = MaterialTheme.typography.bodyMedium,
                color = inkSoft()
            )
            Spacer(Modifier.height(16.dp))
            ModeTabs(selected = tab, onSelect = { tab = it })
            HorizontalDivider(color = hairline())

            if (tab == 0) {
                NetBlockPanel(apps)
            } else {
                RedirectPanel(apps)
            }
        }
    }

    @Composable
    private fun HushRoot() {
        var themeMode by remember { mutableStateOf(Prefs.themeMode(this@MainActivity)) }
        val dark = when (themeMode) {
            Prefs.THEME_DARK -> true
            Prefs.THEME_LIGHT -> false
            else -> isSystemInDarkTheme()
        }
        HushTheme(dark = dark) {
            var showSettings by remember { mutableStateOf(false) }
            BackHandler(enabled = showSettings) { showSettings = false }
            if (showSettings) {
                SettingsScreen(
                    themeMode = themeMode,
                    onThemeMode = {
                        themeMode = it
                        Prefs.setThemeMode(this@MainActivity, it)
                    },
                    onBack = { showSettings = false }
                )
            } else {
                MainScreen(onOpenSettings = { showSettings = true })
            }
        }
    }

    @Composable
    private fun SettingsScreen(themeMode: String, onThemeMode: (String) -> Unit, onBack: () -> Unit) {
        var related by remember { mutableStateOf(Prefs.includeRelated(this@MainActivity)) }
        var kill by remember { mutableStateOf(Prefs.killProcesses(this@MainActivity)) }
        val linked = remember { isDefaultWebHandler() }
        val version = remember {
            try {
                packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
            } catch (e: PackageManager.NameNotFoundException) {
                "?"
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .background(ivory())
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = ink())
                }
                Text(
                    "Settings",
                    style = TextStyle(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 32.sp),
                    color = ink()
                )
            }

            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item { SectionLabel("Appearance") }
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeChip("System", themeMode == Prefs.THEME_SYSTEM) { onThemeMode(Prefs.THEME_SYSTEM) }
                        ThemeChip("Light", themeMode == Prefs.THEME_LIGHT) { onThemeMode(Prefs.THEME_LIGHT) }
                        ThemeChip("Dark", themeMode == Prefs.THEME_DARK) { onThemeMode(Prefs.THEME_DARK) }
                    }
                    Text(
                        "System follows your phone's light or dark setting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = inkSoft(),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    HorizontalDivider(color = hairline())
                }

                item { SectionLabel("Blocking") }
                item {
                    SettingRow(
                        icon = Icons.Outlined.Hub,
                        title = "Include related apps",
                        caption = "Same family, e.g. Messenger and Facebook.",
                        checked = related,
                        onChecked = {
                            related = it
                            Prefs.setIncludeRelated(this@MainActivity, it)
                        }
                    )
                    HorizontalDivider(color = hairline())
                    SettingRow(
                        icon = Icons.Outlined.Bolt,
                        title = "Kill background processes",
                        caption = "Ends running processes every 15 s.",
                        checked = kill,
                        onChecked = {
                            kill = it
                            Prefs.setKillProcesses(this@MainActivity, it)
                        }
                    )
                    HorizontalDivider(color = hairline())
                }

                item { SectionLabel("Link handler") }
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Default for web links", style = MaterialTheme.typography.bodyLarge, color = ink())
                            Text(
                                if (linked) "Set. RedirectBlock can drop links." else "Needed for RedirectBlock. Not set yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = inkSoft()
                            )
                        }
                        OutlinedButton(onClick = { openAppSettings() }) {
                            Text(if (linked) "Change" else "Set up", color = ink())
                        }
                    }
                    HorizontalDivider(color = hairline())
                }

                item { SectionLabel("About") }
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { openUrl("https://github.com/Moggle-Khraum/hush-android") }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("GitHub", style = MaterialTheme.typography.bodyLarge, color = ink())
                            Text("Moggle-Khraum/hush-android", style = MaterialTheme.typography.bodySmall, color = inkSoft())
                        }
                        Icon(Icons.Outlined.OpenInNew, contentDescription = null, tint = inkSoft(), modifier = Modifier.size(18.dp))
                    }
                    HorizontalDivider(color = hairline())
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Text("Version", style = MaterialTheme.typography.bodyLarge, color = ink(), modifier = Modifier.weight(1f))
                        Text(version, style = MaterialTheme.typography.bodyLarge, color = inkSoft())
                    }
                    HorizontalDivider(color = hairline())
                }
            }
        }
    }

    @Composable
    private fun SectionLabel(text: String) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = brass(),
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
        )
    }

    @Composable
    private fun RowScope.ThemeChip(label: String, selected: Boolean, onClick: () -> Unit) {
        Box(
            Modifier
                .weight(1f)
                .height(40.dp)
                .clip(CircleShape)
                .border(1.dp, if (selected) ink() else hairline(), CircleShape)
                .background(if (selected) ink() else Color.Transparent)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) ivory() else ink()
            )
        }
    }

    /** True when Android would open web links with Hush. */
    private fun isDefaultWebHandler(): Boolean {
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        return packageManager.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == packageName
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    @Composable
    private fun ModeTabs(selected: Int, onSelect: (Int) -> Unit) {
        Row(Modifier.fillMaxWidth()) {
            listOf(
                "NetBlock" to Icons.Outlined.WifiOff,
                "RedirectBlock" to Icons.Outlined.Link
            ).forEachIndexed { index, (label, icon) ->
                val on = index == selected
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { onSelect(index) }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = if (on) ink() else inkSoft(),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (on) ink() else inkSoft()
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(if (on) 2.dp else 1.dp)
                            .background(if (on) ink() else hairline())
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

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Spacer(Modifier.height(20.dp))
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Seal(
                        active = active,
                        enabled = active || selected.isNotEmpty(),
                        onClick = { if (active) stopBlocking() else startBlocking() }
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        when {
                            active -> "Blocking ${selected.size + related.size} app(s)"
                            selected.isEmpty() -> "Pick apps below to begin"
                            else -> "Press the seal to cut ${selected.size} app(s) off"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = inkSoft()
                    )
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = hairline())
                SettingRow(
                    icon = Icons.Outlined.Hub,
                    title = "Include related apps",
                    caption = "Messenger, Facebook, Meta services",
                    checked = includeRelated,
                    onChecked = {
                        includeRelated = it
                        Prefs.setIncludeRelated(this@MainActivity, it)
                        refreshIfActive()
                    }
                )
                HorizontalDivider(color = hairline())
                SettingRow(
                    icon = Icons.Outlined.Bolt,
                    title = "Kill background processes",
                    caption = "Ends them every 15 seconds",
                    checked = killProcesses,
                    onChecked = {
                        killProcesses = it
                        Prefs.setKillProcesses(this@MainActivity, it)
                        refreshIfActive()
                    }
                )
                HorizontalDivider(color = hairline())
                if (related.isNotEmpty()) {
                    Text(
                        "Also covered: " + related.sorted().joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = inkSoft(),
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
                AppSearch(query) { query = it }
            }
            appItems(apps, selected, query, onToggle = { toggleApp(it) }, showForceStop = true)
        }
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

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Spacer(Modifier.height(20.dp))
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Seal(
                        active = active,
                        enabled = active || selected.isNotEmpty(),
                        onClick = {
                            active = !active
                            Prefs.setRedirectActive(this@MainActivity, active)
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        when {
                            active -> "Stopping links from ${selected.size} app(s)"
                            selected.isEmpty() -> "Pick apps below to begin"
                            else -> "Press the seal to stop their links"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = inkSoft()
                    )
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = hairline())
                Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = inkSoft(), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "Hush must be the default app for web links. Set it under Open by default in its settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = inkSoft()
                    )
                }
                OutlinedButton(onClick = { openAppSettings() }) {
                    Text("Open Hush settings")
                }
                AppSearch(query) { query = it }
            }
            appItems(apps, selected, query, onToggle = { toggleApp(it) }, showForceStop = false)
        }
    }

    @Composable
    private fun AppSearch(query: String, onQuery: (String) -> Unit) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            label = { Text("Search apps") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        )
    }

    /** App rows as lazy items, so they scroll together with the header. */
    private fun LazyListScope.appItems(
        apps: List<AppEntry>,
        selected: Set<String>,
        query: String,
        onToggle: (String) -> Unit,
        showForceStop: Boolean
    ) {
        val shown = apps.filter { it.label.contains(query, ignoreCase = true) }
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
                    Text(app.label, style = MaterialTheme.typography.bodyLarge, color = ink())
                    Text(app.pkg, style = MaterialTheme.typography.bodySmall, color = inkSoft())
                }
                if (checked && showForceStop) {
                    // Android only lets the user force-stop another app.
                    TextButton(onClick = { Killer.openForceStopPage(this@MainActivity, app.pkg) }) {
                        Icon(Icons.Outlined.Close, contentDescription = null, tint = ink(), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Force stop", color = ink())
                    }
                }
            }
            HorizontalDivider(color = hairline())
        }
    }

    @Composable
    private fun SettingRow(
        icon: ImageVector,
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
            Icon(icon, contentDescription = null, tint = ink(), modifier = Modifier.size(22.dp))
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = ink())
                Text(caption, style = MaterialTheme.typography.bodySmall, color = inkSoft())
            }
            Switch(checked = checked, onCheckedChange = onChecked)
        }
    }

    /** The round seal that starts and stops a mode. */
    @Composable
    private fun Seal(active: Boolean, enabled: Boolean, onClick: () -> Unit) {
        val ring = if (active) alarm() else ink()
        Box(
            Modifier
                .size(112.dp)
                .clip(CircleShape)
                .border(2.dp, ring, CircleShape)
                .background(if (active) alarm().copy(alpha = 0.06f) else Color.Transparent)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(92.dp).border(1.dp, ring.copy(alpha = 0.45f), CircleShape))
            Icon(
                if (active) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                contentDescription = if (active) "Lift" else "Seal",
                tint = if (enabled) ring else inkSoft(),
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
