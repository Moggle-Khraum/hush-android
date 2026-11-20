package com.mogglestone.hush

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity

/**
 * RedirectBlock: Hush is registered as a handler for web links (http/https).
 *
 * When a link is opened by an app the user picked for RedirectBlock, the link
 * is dropped. Otherwise it is passed on to a browser.
 *
 * Limitation: Android only tells us the sender when the app sets a referrer.
 * Links from apps that don't set one are passed on. Apps with their own
 * in-app browser never send links through Android at all, so they can't be blocked here.
 */
class RedirectActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val link = intent?.data
        if (link == null) {
            finish()
            return
        }

        if (Prefs.isRedirectActive(this)) {
            val sender = senderPackage()
            val blockedApp = sender != null && sender in Prefs.redirectPackages(this)
            val site = matchedSite(link)
            val unknownSender = sender == null && Prefs.redirectUnknown(this)
            val reason = when {
                blockedApp -> "from $sender"
                site != null -> "to $site"
                unknownSender -> "from an unknown app"
                else -> null
            }
            if (reason != null) {
                Log.i(TAG, "Dropped link $reason: $link")
                Toast.makeText(this, "Hush stopped a link $reason", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        }

        openInBrowser(link)
        finish()
    }

    /** Package that sent the link, if the sender set a referrer. */
    private fun senderPackage(): String? {
        val ref = referrer
        if (ref != null && ref.scheme == "android-app") return ref.host
        val extra = intent.getStringExtra(Intent.EXTRA_REFERRER_NAME) ?: return null
        val uri = Uri.parse(extra)
        return if (uri.scheme == "android-app") uri.host else null
    }

    /** First configured site word that appears as a label of the link's host. */
    private fun matchedSite(link: Uri): String? {
        val labels = link.host?.lowercase()?.split('.') ?: return null
        return Prefs.redirectSites(this).firstOrNull { it in labels }
    }

    /** First installed browser that isn't Hush, so the link never loops back here. */
    private fun openInBrowser(link: Uri) {
        val browserIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)
        val browser = packageManager
            .queryIntentActivities(browserIntent, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .firstOrNull { it != packageName }

        if (browser == null) {
            Toast.makeText(this, "No browser found to open this link", Toast.LENGTH_LONG).show()
            return
        }
        val view = Intent(Intent.ACTION_VIEW, link)
            .setPackage(browser)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(view)
        } catch (e: Exception) {
            Log.w(TAG, "Could not open $link in $browser", e)
            Toast.makeText(this, "Could not open this link", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val TAG = "Hush"
    }
}
