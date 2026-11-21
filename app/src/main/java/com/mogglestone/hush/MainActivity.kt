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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

data class AppEntry(val label: String, val pkg: String)

private enum class Stage { WELCOME, START, CHOOSE, NET, REDIRECT, SETTINGS }

// ---- Palette: ivory and ink, one deep green accent. Midnight ink with brass at night. ----
private val Ivory = Color(0xFFF5F0E6)
private val Paper = Color(0xFFFBF8F1)
private val Ink = Color(0xFF1C1A16)
private val InkMuted = Color(0xFF5C5649)
private val Hairline = Color(0xFFD8CFBB)
private val Green = Color(0xFF2E5E52)

private val NightBg = Color(0xFF15130F)
private val NightPaper = Color(0xFF1D1A15)
private val NightText = Color(0xFFEFE8D8)
private val NightMuted = Color(0xFFB3AA97)
private val NightHairline = Color(0xFF3A352C)
private val Brass = Color(0xFFD4B06A)

private fun display(size: Int, weight: FontWeight = FontWeight.Medium) = TextStyle(
    fontFamily = FontFamily.Serif,
    fontStyle = FontStyle.Italic,
    fontWeight = weight,
    fontSize = size.sp
)

private val Caps = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val apps = loadApps()
        setContent { HushApp(apps) }
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
}

@Composable
private fun HushApp(apps: List<AppEntry>) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) {
        darkColorScheme(
            background = NightBg, surface = NightPaper, onBackground = NightText, onSurface = NightText,
            primary = Brass, onPrimary = NightBg, outline = NightMuted, error = Brass
        )
    } else {
        lightColorScheme(
            background = Ivory, surface = Paper, onBackground = Ink, onSurface = Ink,
            primary = Green, onPrimary = Paper, outline = InkMuted, error = Green
        )
    }
    MaterialTheme(colorScheme = scheme) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            HushScreens(apps)
        }
    }
}

@Composable
private fun HushScreens(apps: List<AppEntry>) {
    val ctx = LocalContext.current
    var stage by remember { mutableStateOf(Stage.WELCOME) }

    LaunchedEffect(stage) {
        if (stage == Stage.WELCOME) {
            delay(2600)
            stage = Stage.START
        }
    }

    // NetBlock state
    var netSel by remember { mutableStateOf(Prefs.blockedPackages(ctx)) }
    var netActive by remember { mutableStateOf(Prefs.isActive(ctx)) }
    // RedirectBlock state
    var redirectSel by remember { mutableStateOf(Prefs.redirectPackages(ctx)) }
    var redirectOn by remember { mutableStateOf(Prefs.redirectOn(ctx)) }

    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            BlockerVpnService.start(ctx)
            netActive = true
        }
    }

    fun startNet() {
        Prefs.setBlockedPackages(ctx, netSel)
        val prepare = VpnService.prepare(ctx)
        if (prepare != null) vpnLauncher.launch(prepare) else {
            BlockerVpnService.start(ctx)
            netActive = true
        }
    }

    fun stopNet() {
        BlockerVpnService.stop(ctx)
        netActive = false
    }

    fun setRedirect(on: Boolean) {
        redirectOn = on
        Prefs.setRedirectOn(ctx, on)
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = stage,
            transitionSpec = { fadeIn(tween(450)) togetherWith fadeOut(tween(300)) },
            label = "stage"
        ) { s ->
            when (s) {
                Stage.WELCOME -> Welcome()
                Stage.START -> StartScreen(onStart = { stage = Stage.CHOOSE })
                Stage.CHOOSE -> ChooseScreen(
                    netOn = netActive,
                    netCount = netSel.size,
                    redirectOn = redirectOn,
                    redirectCount = redirectSel.size,
                    onNet = { stage = Stage.NET },
                    onRedirect = { stage = Stage.REDIRECT },
                    onSettings = { stage = Stage.SETTINGS }
                )
                Stage.NET -> ModeScreen(
                    mode = Stage.NET,
                    apps = apps,
                    selected = netSel,
                    active = netActive,
                    relatedOn = Prefs.includeRelated(ctx),
                    onTab = { stage = it },
                    onBack = { stage = Stage.CHOOSE },
                    onToggleApp = { pkg ->
                        if (netActive) return@ModeScreen
                        netSel = if (pkg in netSel) netSel - pkg else netSel + pkg
                        Prefs.setBlockedPackages(ctx, netSel)
                    },
                    onMainButton = { if (netActive) stopNet() else if (netSel.isNotEmpty()) startNet() },
                    onForceStop = { pkg -> Killer.openForceStopPage(ctx, pkg) }
                )
                Stage.REDIRECT -> ModeScreen(
                    mode = Stage.REDIRECT,
                    apps = apps,
                    selected = redirectSel,
                    active = redirectOn,
                    relatedOn = false,
                    onTab = { stage = it },
                    onBack = { stage = Stage.CHOOSE },
                    onToggleApp = { pkg ->
                        redirectSel = if (pkg in redirectSel) redirectSel - pkg else redirectSel + pkg
                        Prefs.setRedirectPackages(ctx, redirectSel)
                    },
                    onMainButton = { setRedirect(!redirectOn) },
                    onForceStop = { }
                )
                Stage.SETTINGS -> SettingsScreen(onBack = { stage = Stage.CHOOSE })
            }
        }
    }
}

// ---------- Screens ----------

@Composable
private fun Welcome() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Welcome to Hush!", style = display(40), textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text("Cut any app off from the internet.", color = MaterialTheme.colorScheme.outline,
            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun StartScreen(onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Seal(size = 220.dp, filled = true, onClick = onStart, contentDescription = "Start blocking apps") {
            Text("Start", style = display(48))
            Text("BLOCKING APPS", style = Caps)
        }
        Spacer(Modifier.height(32.dp))
        Text(
            "Choose what loses internet, then hush it.",
            style = display(22),
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ChooseScreen(
    netOn: Boolean,
    netCount: Int,
    redirectOn: Boolean,
    redirectCount: Int,
    onNet: () -> Unit,
    onRedirect: () -> Unit,
    onSettings: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Header(onSettings = onSettings)
        Spacer(Modifier.height(16.dp))
        Seal(size = 88.dp, filled = false, onClick = {}, contentDescription = "Hush") {
            Text("H", style = display(40))
        }
        Spacer(Modifier.height(16.dp))
        Text("Choose a mode", style = display(32))
        Text("Each works on its own. Run one, or both.", color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(20.dp))
        ModeCard("I", "NetBlock", "Cuts the selected apps off the internet completely.",
            if (netOn) "Active · $netCount apps" else "Off", netOn, onNet)
        Spacer(Modifier.height(12.dp))
        ModeCard("II", "RedirectBlock", "Stops selected apps from opening blocked links, like ad or casino pages.",
            if (redirectOn) "Active · $redirectCount apps" else "Off", redirectOn, onRedirect)
    }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var related by remember { mutableStateOf(Prefs.includeRelated(ctx)) }
    var kill by remember { mutableStateOf(Prefs.killProcesses(ctx)) }
    var domains by remember { mutableStateOf(Prefs.blocklist(ctx).sorted()) }
    var newDomain by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Spacer(Modifier.weight(1f))
            Text("Settings", style = display(28))
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(64.dp))
        }
        HairlineRule()
        Column(Modifier.weight(1f, fill = true).padding(top = 12.dp)) {
            SectionLabel("Blocking")
            SwitchRow("Include related apps", "Same family, e.g. Messenger and Facebook.", related) {
                related = it
                Prefs.setIncludeRelated(ctx, it)
            }
            SwitchRow("Kill background processes", "Ends running processes every 15 s.", kill) {
                kill = it
                Prefs.setKillProcesses(ctx, it)
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("RedirectBlock")
            Text(
                "Hush must be your default for web links for RedirectBlock to work.",
                color = MaterialTheme.colorScheme.outline,
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = {
                ctx.startActivity(
                    Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS)
                        .setData(Uri.parse("package:${ctx.packageName}"))
                )
            }) { Text("Open link settings") }

            Spacer(Modifier.height(8.dp))
            SectionLabel("Blocked domains")
            domains.forEach { d ->
                Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(d, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        domains = domains - d
                        Prefs.setBlocklist(ctx, domains.toSet())
                    }) { Text("Remove") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newDomain,
                    onValueChange = { newDomain = it },
                    label = { Text("e.g. ggslot.fund") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    val d = newDomain.trim().lowercase().removePrefix("https://").removePrefix("http://").trimEnd('/')
                    if (d.isNotEmpty() && d !in domains) {
                        domains = (domains + d).sorted()
                        Prefs.setBlocklist(ctx, domains.toSet())
                    }
                    newDomain = ""
                }) { Text("Add") }
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("About")
            TextButton(onClick = {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Moggle-Khraum/hush-android")))
            }) { Text("GitHub · Moggle-Khraum/hush-android") }
            Text("Version 1.0", color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun ModeScreen(
    mode: Stage,
    apps: List<AppEntry>,
    selected: Set<String>,
    active: Boolean,
    relatedOn: Boolean,
    onTab: (Stage) -> Unit,
    onBack: () -> Unit,
    onToggleApp: (String) -> Unit,
    onMainButton: () -> Unit,
    onForceStop: (String) -> Unit
) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    val isNet = mode == Stage.NET
    val related = remember(selected, relatedOn) {
        if (isNet && relatedOn) Related.relatedTo(ctx, selected) else emptySet()
    }
    val count = if (isNet) selected.size + related.size else selected.size
    val plural = if (count == 1) "" else "s"
    val accent = MaterialTheme.colorScheme.primary

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Modes") }
            Spacer(Modifier.weight(1f))
            Text("Hush", style = display(28))
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(64.dp))
        }

        // Two independent modes, each with its own tab.
        Row(Modifier.fillMaxWidth()) {
            ModeTab("NetBlock", selected = isNet) { onTab(Stage.NET) }
            ModeTab("RedirectBlock", selected = !isNet) { onTab(Stage.REDIRECT) }
        }
        HairlineRule()

        Spacer(Modifier.height(12.dp))
        Text(
            if (isNet) "Selected apps lose all internet access."
            else "Links opened by selected apps are stopped. Hush must be your link handler.",
            style = display(16),
            color = MaterialTheme.colorScheme.outline
        )

        Spacer(Modifier.height(12.dp))
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(2.dp),
            border = BorderStroke(1.dp, if (active) accent else Hairline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Seal(size = 64.dp, filled = active, onClick = onMainButton, contentDescription = "Toggle") {
                    Text(if (isNet) "N" else "R", style = display(28))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            isNet && active -> "Silenced"
                            isNet -> "Net is open"
                            active -> "Guarded"
                            else -> "Links are open"
                        },
                        style = display(26)
                    )
                    Text(
                        when {
                            isNet -> "$count app$plural ${if (active) "cut off" else "selected"}"
                            else -> "$count app$plural ${if (active) "guarded" else "selected"}"
                        },
                        color = MaterialTheme.colorScheme.outline,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onMainButton,
            enabled = active || selected.isNotEmpty(),
            shape = RoundedCornerShape(2.dp),
            colors = if (active) ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onBackground
            ) else ButtonDefaults.buttonColors(),
            border = if (active) BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground) else null,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(
                when {
                    isNet && active -> "Restore internet"
                    isNet -> "Block $count app$plural"
                    active -> "Allow links again"
                    else -> "Block links from $count app$plural"
                }
            )
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search apps") },
            singleLine = true,
            shape = RoundedCornerShape(2.dp),
            modifier = Modifier.fillMaxWidth()
        )

        val shown = apps.filter { it.label.contains(query, ignoreCase = true) }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(shown, key = { it.pkg }) { app ->
                val checked = app.pkg in selected
                val isRelated = app.pkg in related
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clickable(enabled = !active) { onToggleApp(app.pkg) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = checked || isRelated, onCheckedChange = null, enabled = !active)
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(app.label, style = MaterialTheme.typography.bodyLarge)
                        Text(app.pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    if (isRelated) Text("Related", style = Caps, color = MaterialTheme.colorScheme.outline)
                    if (checked && isNet && active) {
                        TextButton(onClick = { onForceStop(app.pkg) }) { Text("Force stop") }
                    }
                }
                HairlineRule()
            }
        }
    }
}

// ---------- Pieces ----------

@Composable
private fun Header(onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Hush", style = display(32), modifier = Modifier.weight(1f))
        TextButton(onClick = onSettings) { Text("Settings") }
    }
    HairlineRule()
}

@Composable
private fun HairlineRule() {
    val c = if (MaterialTheme.colorScheme.background == NightBg) NightHairline else Hairline
    Box(Modifier.fillMaxWidth().height(1.dp).background(c))
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = Caps,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(68.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = display(15), color = MaterialTheme.colorScheme.outline)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
    HairlineRule()
}

@Composable
private fun RowScope.ModeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier.weight(1f).clickable(onClick = onClick).padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Box(Modifier.fillMaxWidth().height(2.dp).background(if (selected) accent else Color.Transparent))
    }
}

@Composable
private fun ModeCard(
    numeral: String,
    title: String,
    body: String,
    status: String,
    on: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, Hairline),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(numeral, style = display(20, FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f))
                Text(status.uppercase(), style = Caps,
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            }
            Text(title, style = display(30))
            Text(body, color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The round engraved seal: the start button and each mode's emblem. */
@Composable
private fun Seal(
    size: Dp,
    filled: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit
) {
    val bg = if (filled) MaterialTheme.colorScheme.primary else Color.Transparent
    val ring = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground
    val fg = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.matchParentSize()) {
            val c = Offset(this.size.width / 2, this.size.height / 2)
            val r = this.size.minDimension / 2
            drawCircle(ring, radius = r - 1.dp.toPx(), center = c, style = Stroke(1.dp.toPx()))
            drawCircle(
                ring, radius = r * 0.89f, center = c,
                style = Stroke(3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 9.dp.toPx())))
            )
            drawCircle(ring, radius = r * 0.8f, center = c, style = Stroke(1.dp.toPx()))
        }
        CompositionLocalProvider(LocalContentColor provides fg) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                content()
            }
        }
    }
}
