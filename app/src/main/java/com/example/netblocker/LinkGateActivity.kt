package com.example.netblocker

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.content.pm.PackageManager
import android.widget.Toast

/**
 * RedirectBlock. Hush registers for http/https links, so Android sends links here.
 *
 *  - Blocked domain, RedirectBlock on, and the link came from a selected app
 *    (or the sender is unknown): drop it silently.
 *  - Anything else: hand it to a real browser (never Hush itself, to avoid a loop).
 *
 * Limits: apps with their own in-app browser never send links here, and apps
 * that name Chrome directly bypass Hush. Android's referrer is not always set.
 */
class LinkGateActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri: Uri? = intent?.data
        if (uri == null || (uri.scheme != "http" && uri.scheme != "https")) {
            finish()
            return
        }

        val host = uri.host?.lowercase() ?: ""
        val blocked = Prefs.blocklist(this).any { d -> host == d || host.endsWith(".$d") }

        // Referrer looks like android-app://<package> when the sender tells us.
        val source = referrer?.takeIf { it.scheme == "android-app" }?.host
        val fromSelected = source == null || source in Prefs.redirectPackages(this)

        if (Prefs.redirectOn(this) && blocked && fromSelected) {
            Toast.makeText(this, "Blocked link to $host", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val browser = findBrowser()
        if (browser == null) {
            Toast.makeText(this, "No browser found to open this link", Toast.LENGTH_LONG).show()
        } else {
            startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(browser))
        }
        finish()
    }

    /** First browser that is not Hush. */
    private fun findBrowser(): String? {
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        return packageManager.queryIntentActivities(probe, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .firstOrNull { it != packageName }
    }
}
