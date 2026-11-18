package com.example.netblocker

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class AppEntry(val label: String, val pkg: String)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val apps = loadApps()
        setContent {
            MaterialTheme {
                Screen(apps)
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

    @Composable
    private fun Screen(apps: List<AppEntry>) {
        val selected = remember {
            mutableStateListOf<String>().apply { addAll(Prefs.blockedPackages(this@MainActivity)) }
        }
        var active by remember { mutableStateOf(Prefs.isActive(this@MainActivity)) }
        var includeRelated by remember { mutableStateOf(Prefs.includeRelated(this@MainActivity)) }
        var killProcesses by remember { mutableStateOf(Prefs.killProcesses(this@MainActivity)) }
        var query by remember { mutableStateOf("") }

        val vpnPermission = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                BlockerVpnService.start(this@MainActivity)
                active = true
            }
        }

        fun startBlocking() {
            Prefs.setBlockedPackages(this@MainActivity, selected.toSet())
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

        val related = remember(selected.toList(), includeRelated) {
            if (includeRelated) Related.relatedTo(this@MainActivity, selected.toSet()) else emptySet()
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                if (active) "Blocking ${selected.size + related.size} app(s)" else "Internet not blocked",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                "Tick the apps to cut off from the internet, then press the button.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Button(
                onClick = { if (active) stopBlocking() else startBlocking() },
                enabled = active || selected.isNotEmpty(),
                colors = if (active) ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ) else ButtonDefaults.buttonColors(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (active) "Stop blocking" else "Block selected apps")
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Include related apps", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Same-family packages, e.g. Messenger + Facebook + Meta services",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = includeRelated, onCheckedChange = {
                    includeRelated = it
                    Prefs.setIncludeRelated(this@MainActivity, it)
                    refreshIfActive()
                })
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Kill background processes", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Ends running processes every 15s so open connections die",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = killProcesses, onCheckedChange = {
                    killProcesses = it
                    Prefs.setKillProcesses(this@MainActivity, it)
                    refreshIfActive()
                })
            }

            if (related.isNotEmpty()) {
                Text(
                    "Also blocking: " + related.sorted().joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search apps") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            val shown = apps.filter { it.label.contains(query, ignoreCase = true) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(shown, key = { it.pkg }) { app ->
                    val checked = app.pkg in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (checked) selected.remove(app.pkg) else selected.add(app.pkg)
                                Prefs.setBlockedPackages(this@MainActivity, selected.toSet())
                                refreshIfActive()
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(start = 8.dp)
                        ) {
                            Text(app.label, style = MaterialTheme.typography.bodyLarge)
                            Text(app.pkg, style = MaterialTheme.typography.bodySmall)
                        }
                        if (checked) {
                            // Android only lets the user force-stop another app.
                            TextButton(onClick = { Killer.openForceStopPage(this@MainActivity, app.pkg) }) {
                                Text("Force stop")
                            }
                        }
                    }
                }
            }
        }
    }
}
